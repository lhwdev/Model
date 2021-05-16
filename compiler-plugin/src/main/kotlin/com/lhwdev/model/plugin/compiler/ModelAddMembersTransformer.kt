package com.lhwdev.model.plugin.compiler

import com.lhwdev.model.plugin.compiler.util.*
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name


data class ModelInternalGenerationInfo(
	val modelInfoCache: IrProperty?,
	val modelInfoParameter: IrProperty?
)

data class ModelInfoIr(
	val properties: List<ModelPropertyIr>
)

data class ModelPropertyIr(
	val property: IrProperty,
	val index: Int,
	val modelInfo: IrElementScope.(thisRef: IrValueDeclaration) -> IrExpression
)

fun modelInfoOf(targetModel: IrClass): ModelInfoIr {
	val properties = targetModel.declarations
		.filterIsInstance<IrProperty>()
		.filter {
			it.backingField != null &&
				!it.hasAnnotation(ModelIrNames.Exclude) &&
				!it.hasAnnotation(ModelIrNames.ModelInfoParameter)
		}
		.mapIndexed { index, property ->
			ModelPropertyIr(property, index) { thisRef ->
				irCall(
					ModelIrSymbols.modelInfoAt,
					extensionReceiver = irGet(ModelIrSymbols.ModelValueClass.modelInfo, receiver = irGet(thisRef)),
					valueArguments = listOf(irInt(index))
				)
			}
		}
	
	return ModelInfoIr(properties)
}

class ModelAddMembersTransformer : FileLoweringPass, IrElementTransformerVoid() {
	override fun lower(irFile: IrFile) {
		irFile.transformChildren(this, null)
	}
	
	override fun visitClass(declaration: IrClass): IrStatement {
		if(declaration.hasAnnotation(ModelIrNames.Model)) {
			irTrace.record(ModelSlices.IsModel, declaration, Unit)
			transformModel(declaration)
		}
		return super.visitClass(declaration)
	}
	
	private fun IrDeclarationsScope.createModelInfo(targetModel: IrClass): ModelInternalGenerationInfo {
		val companion = targetModel.companionObject() ?: irClass(
			name = Name.identifier("Companion"),
			kind = ClassKind.OBJECT,
			isCompanion = true
		) {
			+irConstructorDefault()
		}.also { targetModel.declarations += it }
		
		return companion.scope.addToThisCompanion(targetModel)
	}
	
	private fun IrClassScope.addToThisCompanion(targetModel: IrClass): ModelInternalGenerationInfo {
		val modelInfoParameters = targetModel.declarations.filterIsInstance<IrProperty>()
			.filter { it.hasAnnotation(ModelIrNames.ModelInfoParameter) }
		
		if(modelInfoParameters.size > 1)
			error("multiple properties with @ModelInfoParameter: ${modelInfoParameters.joinToString { it.name.toString() }}")
		
		if(targetModel.typeParameters.isNotEmpty() && modelInfoParameters.isEmpty()) error(
			"""
			model with type parameter(s) should have a property with @ModelInfoParameter for model system to handle
			serialization and other things.
			
			Note that the visibility of the property can be private as it is only used for code generation and
			accessed inside the class itself.
			The specified model info is not always used, so you should provide consistent value.
			
			example:
			
			@Model
			data class MyModel<T>(val data: T, @ModelInfoParameter val info: ModelInfo<MyModel<T>>)
			
			@Model
			class MyModel2<T, R>(val t: T, val r: R, val tInfo: ModelInfo<T>, val rInfo: ModelInfo<R>) {
				@ModelInfoParameter
				private val selfModelInfo = CustomModelInfo(tInfo, rInfo)
		""".trimIndent()
		)
		
		val modelInfoParameter = modelInfoParameters.firstOrNull()
		
		val modelType = referenceClassType(ModelIrNames.ModelInfo, targetModel.defaultType)
		val properties = targetModel.declarations.filterIsInstance<IrProperty>()
		
		fun IrElementScope.propertyModelInfo(property: IrProperty, parameters: List<IrValueParameter>): IrExpression {
			val type = property.propertyType
			val classifier = type.classifierOrNull
			
			if(classifier != null && classifier.owner in targetModel.typeParameters) {
				return irGet(parameters[targetModel.typeParameters.indexOf(classifier.owner)])
			}
			
			val predefinedModelInfo = irModelInfoFor(type)
			
			return predefinedModelInfo ?: irTODO("not implemented yet")
		}
		
		fun IrBuilderScope.irModelInfoImpl(
			parameters: List<IrValueParameter>
		): IrExpression = irObjectExpression(modelType, superTypes = listOf(modelType)) {
			+irOverrideProperty(ModelIrSymbols.ModelInfoClass.modelDescriptor) { modelDescriptor ->
				modelDescriptor.backingField = irBackingField {
					val descriptorLambda = irFunctionExpression(
						returnType = irBuiltIns.unitType,
						argumentOfCall = ModelIrSymbols.fStructuredModelDescriptor
					) {
						val r = addExtensionReceiver(referenceClassType(ModelIrNames.StructuredModelDescriptorBuilder))
						
						irBlockBody {
							for(property in properties) +irCall(
								ModelIrSymbols.StructuredModelDescriptorBuilderClass.property,
								dispatchReceiver = irGet(r),
								valueArguments = listOf(
									// name: String
									irString(property.name.asString()),
									
									// info: ModelInfo<*>
									propertyModelInfo(property, parameters)
								)
							)
						}
					}
					irCall(
						ModelIrSymbols.fStructuredModelDescriptor,
						valueArguments = listOf(descriptorLambda)
					)
				}
			}
			
			+irOverrideFunction(ModelIrSymbols.ModelInfoClass.create) {
				defaultParameters()
				
				irExpressionBody(irTODO("TODO"))
			}
		}
		
		
		val cache = if(targetModel.typeParameters.isEmpty()) +irProperty(
			name = Name.identifier("\$modelInfo_cache"),
			type = referenceClassType(ModelIrNames.ModelInfo),
			visibility = DescriptorVisibilities.PRIVATE
		) { cache ->
			cache.backingField = irBackingField { irModelInfoImpl(parameters = emptyList()) } // TODO
		} else null
		
		+irMemberFunction(
			name = ModelIrNames.modelInfo,
			returnType = modelType,
			origin = DeclarationOrigins.modelInfo
		) {
			val parameters = targetModel.typeParameters.map {
				addValueParameter(
					Name.identifier("${it.name.asString().firstToLowerCase()}Info"),
					type = referenceClassType(ModelIrNames.ModelInfo)
				)
			}
			
			val expression = if(cache != null) {
				irGet(cache.symbol, receiver = irGet(irThisClass))
			} else irModelInfoImpl(parameters = parameters)
			irExpressionBody(expression)
		}
		
		
		return ModelInternalGenerationInfo(modelInfoCache = cache, modelInfoParameter = modelInfoParameter)
	}
	
	private fun transformModel(target: IrClass): Unit = with(target.scope) {
		val modelValue = referenceClass(ModelIrNames.ModelValue)
		
		// 1. Create modelInfo implementation
		// 2. Alter companion
		val modelGenerationInfo = createModelInfo(target)
		
		// 3. Override ModelValue
		target.superTypes = target.superTypes + modelValue.defaultType
		
		// 4. Implement members
		+irOverrideProperty(
			modelValue.referenceProperty(ModelIrNames.ModelValueClass.modelInfo),
			type = ModelIrSymbols.StructuredModelInfo.typeWith(defaultType)
		) { modelInfo ->
			modelInfo.getter = irPropertyGetter { _, irThis ->
				val modelInfoValue = when {
					modelGenerationInfo.modelInfoCache != null ->
						irGet(modelGenerationInfo.modelInfoCache.symbol, receiver = irGetCompanion(target.symbol)!!)
					modelGenerationInfo.modelInfoParameter != null ->
						irGet(modelGenerationInfo.modelInfoParameter.symbol, receiver = irGet(irThis))
					else -> error("???????")
				}
				irExpressionBody(modelInfoValue)
			}
		}
		
		val infoIr = modelInfoOf(target)
		
		+irOverrideFunction(ModelIrSymbols.ModelValueClass.readModel) { readModel ->
			defaultParameters()
			val irThisClass = irThisClass
			val (index /* index: Int */, to /* to: ModelWriter */) = readModel.valueParameters
			
			irBlockBody {
				+irWhenSubject(irGet(index), irBuiltIns.unitType) { i ->
					for((propertyIndex, property) in infoIr.properties.withIndex()) {
						irEquals(irGet(i), irInt(propertyIndex)) then irWriteModelTo(
							writer = irGet(to),
							value = irGet(
								infoIr.properties[propertyIndex].property.symbol,
								receiver = irGet(irThisClass)
							),
							type = property.property.propertyType,
							modelInfo = property.modelInfo(this@irBlockBody, irThisClass)
						)
					}
					
					orElse(irError(irString("unexpected index "), irGet(index)))
				}
			}
		}
		
		+irOverrideFunction(ModelIrSymbols.ModelValueClass.writeModel) { writeModel ->
			defaultParameters()
			val irThisClass = irThisClass
			val (index /* index: Int */, from /* from: ModelReader */) = writeModel.valueParameters
			
			irBlockBody {
				+irWhenSubject(irGet(index), irBuiltIns.unitType) { i ->
					for((propertyIndex, property) in infoIr.properties.withIndex()) {
						irEquals(irGet(i), irInt(propertyIndex)) then if(property.property.isVar) {
							val read = irReadModelFrom(
								reader = irGet(from),
								type = property.property.propertyType,
								modelInfo = property.modelInfo(this@irBlockBody, irThisClass)
							)
							
							irSet(
								infoIr.properties[propertyIndex].property.symbol,
								receiver = irGet(irThisClass),
								value = read
							)
						} else {
							irError("readonly property '${property.property.name}'")
						}
					}
					
					orElse(irError(irString("unexpected index "), irGet(index)))
				}
			}
		}
		
		for(modelProperty in infoIr.properties) {
			transformProperty(modelProperty)
		}
	}
	
	private fun transformProperty(propertyIr: ModelPropertyIr): Unit = with(propertyIr.property.scope) {
		val property = propertyIr.property
		println("transform ${property.debugName()}")
		val type = property.propertyType
		
		val originalGetter = property.getter ?: irPropertyGetter().also {
			property.getter = it
		}
		
		// getter
		with(originalGetter.memberScope) {
			val irThis = irThis
			val value = irReturnableBlock(type) {
				val transformer = MapInlineTransformer(
					callMapping = mapOf(),
					getMapping = mapOf(),
					returnMapping = mapOf(originalGetter.symbol to returnTargetSymbol)
				)
				originalGetter.body?.statements?.forEach {
					val statement = it.transform(transformer, null) as IrStatement
					+statement
				}
			}
			// fun <T> onReadProperty(model: ModelValue, index: Int, value: T): T
			// currentModelManager.onReadManager
			val call = irCall(
				ModelIrSymbols.ModelManagerClass.onReadProperty,
				dispatchReceiver = irGet(ModelIrSymbols.currentModelManager, receiver = null),
				
				// <T>
				typeArguments = listOf(property.propertyType),
				
				// (model = this, index = INDEX, value = property
				valueArguments = listOf(irGet(irThis), irInt(propertyIr.index), value)
			)
			
			originalGetter.body = irExpressionBody(call)
		}
		
		// setter
	}
}


private class MapInlineTransformer(
	private val getMapping: Map<IrValueSymbol, IrValueSymbol>,
	private val callMapping: Map<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol>,
	private val returnMapping: Map<IrReturnTargetSymbol, IrReturnTargetSymbol>
) : IrElementTransformerVoid() {
	override fun visitGetValue(expression: IrGetValue): IrExpression {
		val mapTo = getMapping[expression.symbol]
		if(mapTo != null) return IrGetValueImpl(
			startOffset = expression.startOffset, endOffset = expression.endOffset,
			type = expression.type, symbol = mapTo,
			origin = expression.origin
		)
		return super.visitGetValue(expression)
	}
	
	override fun visitReturn(expression: IrReturn): IrExpression {
		val mapTo = returnMapping[expression.returnTargetSymbol]
		if(mapTo != null) return IrReturnImpl(
			startOffset = expression.startOffset, endOffset = expression.endOffset,
			type = expression.type, returnTargetSymbol = mapTo, value = expression.value
		)
		return super.visitReturn(expression)
	}
	
	override fun visitCall(expression: IrCall): IrExpression {
		val mapTo = callMapping[expression.symbol]
		if(mapTo != null) return IrCallImpl(
			startOffset = expression.startOffset, endOffset = expression.endOffset,
			type = expression.type, symbol = mapTo,
			typeArgumentsCount = expression.typeArgumentsCount, valueArgumentsCount = expression.valueArgumentsCount,
			origin = expression.origin, superQualifierSymbol = expression.superQualifierSymbol
		).apply {
			copyTypeAndValueArgumentsFrom(expression)
		}
		return super.visitCall(expression)
	}
}
