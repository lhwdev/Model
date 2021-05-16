package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.backend.common.ir.createDispatchReceiverParameter
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.overrides.IrOverridingUtil
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource


val sBackendDeclarationOrigin: IrDeclarationOrigin = object : IrDeclarationOrigin {
	override val isSynthetic: Boolean get() = true
	override fun toString(): String = "(IR_BACKEND)"
}

fun <T> List<T>.withAdded(index: Int, element: T): List<T> {
	val list = ArrayList<T>(size + 1)
	list.addAll(subList(0, index))
	list.add(element)
	list.addAll(subList(index, size))
	
	return list
}


// Members

fun IrBuilderScope.irClass(
	name: Name,
	kind: ClassKind = ClassKind.CLASS,
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	modality: Modality = Modality.FINAL,
	superTypes: List<IrType> = emptyList(),
	isCompanion: Boolean = false,
	isInner: Boolean = false,
	isData: Boolean = false,
	isExternal: Boolean = false,
	isInline: Boolean = false,
	isExpect: Boolean = false,
	isFun: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	source: SourceElement = SourceElement.NO_SOURCE,
	init: IrClassScope.(IrClass) -> Unit = {}
): IrClass {
	val symbol = IrClassSymbolImpl()
	
	val aClass = irFactory.createClass(
		startOffset, endOffset,
		origin = origin,
		symbol = symbol,
		name = name, kind = kind,
		visibility = visibility,
		modality = modality,
		isCompanion = isCompanion, isInner = isInner, isData = isData, isExternal = isExternal, isInline = isInline,
		isExpect = isExpect, isFun = isFun,
		source = source
	)
	
	aClass.parent = scope.getLocalDeclarationParent()
	
	val thisReceiver = irFactory.createValueParameter(
		startOffset, endOffset,
		origin = IrDeclarationOrigin.INSTANCE_RECEIVER,
		symbol = IrValueParameterSymbolImpl(),
		name = Name.special("<this>"),
		index = -1,
		type = aClass.typeWith(aClass.typeParameters.map { it.defaultType }),
		varargElementType = null,
		isCrossinline = false, isNoinline = false, isHidden = false, isAssignable = false
	)
	thisReceiver.parent = aClass
	aClass.thisReceiver = thisReceiver
	
	// TODO: generating fake overrides after assigning superType breaks; but it is needed in some cases
	@OptIn(ObsoleteDescriptorBasedAPI::class)
	val overridingUtil = IrOverridingUtil(irBuiltIns, context.linker.fakeOverrideBuilder)
	overridingUtil.buildFakeOverridesForClass(aClass)
	
	aClass.superTypes = superTypes
	
	val declarationScope = IrClassScopeImpl(aClass, overridingUtil)
	declarationScope.init(aClass)
	
	return aClass
}

fun IrBuilderScope.irSimpleFunction(
	name: Name,
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	modality: Modality = Modality.FINAL,
	returnType: IrType = irBuiltIns.unitType,
	isInline: Boolean = false,
	isExternal: Boolean = false,
	isTailrec: Boolean = false,
	isSuspend: Boolean = false,
	isOperator: Boolean = false,
	isInfix: Boolean = false,
	isExpect: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	containerSource: DeserializedContainerSource? = null,
	init: IrSimpleFunctionScope.(IrSimpleFunction) -> IrBody?
): IrSimpleFunction {
	val symbol = IrSimpleFunctionSymbolImpl()
	val function = irFactory.createFunction(
		startOffset, endOffset,
		symbol = symbol,
		name = name,
		visibility = visibility, modality = modality,
		returnType = returnType,
		isInline = isInline, isExternal = isExternal, isTailrec = isTailrec, isSuspend = isSuspend,
		isOperator = isOperator, isInfix = isInfix, isExpect = isExpect, isFakeOverride = false,
		origin = origin,
		containerSource = containerSource
	)
	
	function.parent = scope.getLocalDeclarationParent()
	function.body = function.scope.init(function)
	
	return function
}


fun IrClassScope.irOverrideFunction(
	overrideTarget: IrSimpleFunctionSymbol,
	visibility: DescriptorVisibility = overrideTarget.owner.visibility,
	modality: Modality = Modality.OPEN,
	returnType: IrType = overrideTarget.owner.returnType,
	isTailrec: Boolean = overrideTarget.owner.isTailrec,
	isSuspend: Boolean = overrideTarget.owner.isSuspend,
	isOperator: Boolean = overrideTarget.owner.isOperator,
	isInfix: Boolean = overrideTarget.owner.isInfix,
	isExpect: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	containerSource: DeserializedContainerSource? = null,
	init: IrOverrideFunctionScope.(IrSimpleFunction) -> IrBody?
): IrSimpleFunction {
	val irClass = irElement
	
	return irSimpleFunction(
		name = overrideTarget.owner.name,
		visibility = visibility, modality = modality,
		returnType = returnType,
		isTailrec = isTailrec, isSuspend = isSuspend, isOperator = isOperator, isInfix = isInfix, isExpect = isExpect,
		origin = origin, containerSource = containerSource
	) { function ->
		function.overriddenSymbols += overrideTarget
		IrOverrideFunctionScopeImpl(irElement, overrideTarget, irClass, scope).init(function)
	}
}


fun IrClassScope.irMemberFunction(
	name: Name,
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	modality: Modality = Modality.FINAL,
	returnType: IrType = irBuiltIns.unitType,
	isInline: Boolean = false,
	isExternal: Boolean = false,
	isTailrec: Boolean = false,
	isSuspend: Boolean = false,
	isOperator: Boolean = false,
	isInfix: Boolean = false,
	isExpect: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	containerSource: DeserializedContainerSource? = null,
	init: IrMemberFunctionScope.(IrSimpleFunction) -> IrBody?
): IrSimpleFunction {
	val irClass = irElement
	return irSimpleFunction(
		name = name,
		visibility = visibility,
		modality = modality,
		returnType = returnType,
		isInline = isInline, isExternal = isExternal, isTailrec = isTailrec, isSuspend = isSuspend,
		isOperator = isOperator, isInfix = isInfix, isExpect = isExpect,
		origin = origin,
		containerSource = containerSource
	) { function ->
		addDispatchReceiver()
		IrMemberFunctionScopeImpl(irElement, irClass, scope).init(function)
	}
}


fun IrClassScope.irConstructor(
	name: Name = Name.special("<init>"),
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	returnType: IrType = irBuiltIns.unitType,
	isInline: Boolean = false,
	isExternal: Boolean = false,
	isExpect: Boolean = false,
	isPrimary: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	containerSource: DeserializedContainerSource? = null,
	init: IrBuilderScope.(IrConstructor) -> IrBody?
): IrConstructor {
	val symbol = IrConstructorSymbolImpl()
	
	val function = irFactory.createConstructor(
		startOffset, endOffset,
		origin = origin,
		symbol = symbol,
		name = name,
		visibility = visibility,
		returnType = returnType,
		isInline = isInline, isExternal = isExternal, isExpect = isExpect, isPrimary = isPrimary,
		containerSource = containerSource
	)
	
	function.parent = scope.getLocalDeclarationParent()
	function.body = irBuilderScope(function).init(function)
	
	return function
}

fun IrClassScope.irConstructorDefault(): IrConstructor {
	val mainSuperClass = mainSuperClass
	return irConstructor(isPrimary = true) {
		irBlockBody {
			+irDelegatingConstructorCall(mainSuperClass.primaryConstructor!!.symbol)
		}
	}
}


// Property

val IrProperty.propertyType: IrType
	get() = getter?.returnType ?: backingField?.type
	?: error("malformed property: cannot infer type from getter or backingField")

fun IrBuilderScope.irProperty(
	name: Name,
	type: IrType,
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	modality: Modality = Modality.FINAL,
	isVar: Boolean = false,
	isConst: Boolean = false,
	isLateinit: Boolean = false,
	isDelegated: Boolean = false,
	isExternal: Boolean = false,
	isExpect: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	containerSource: DeserializedContainerSource? = null,
	init: IrPropertyScope.(IrProperty) -> Unit
): IrProperty {
	val symbol = IrPropertySymbolImpl()
	val property = irFactory.createProperty(
		startOffset, endOffset,
		origin = origin,
		symbol = symbol,
		name = name,
		visibility = visibility,
		modality = modality,
		isVar = isVar, isConst = isConst, isLateinit = isLateinit, isDelegated = isDelegated, isExternal = isExternal,
		isExpect = isExpect,
		containerSource = containerSource
	)
	
	property.parent = scope.getLocalDeclarationParent()
	
	with(property.scopeOf(type)) {
		init(property)
		
		if(property.getter == null) property.getter = irPropertyGetter()
		if(isVar && property.setter == null) property.setter = irPropertySetter()
	}
	
	return property
}

fun IrClassScope.irMemberProperty(
	name: Name,
	type: IrType,
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	modality: Modality = Modality.FINAL,
	isVar: Boolean = false,
	isConst: Boolean = false,
	isLateinit: Boolean = false,
	isDelegated: Boolean = false,
	isExternal: Boolean = false,
	isExpect: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	containerSource: DeserializedContainerSource? = null,
	init: IrMemberPropertyScope.(IrProperty) -> Unit
): IrProperty {
	val irClass = irElement
	
	return irProperty(
		name = name,
		type = type,
		visibility = visibility,
		modality = modality,
		isVar = isVar, isConst = isConst, isLateinit = isLateinit, isDelegated = isDelegated, isExternal = isExternal,
		isExpect = isExpect,
		origin = origin,
		containerSource = containerSource
	) {
		IrMemberPropertyScopeImpl(irElement, type, irClass, scope).init(it)
	}
}


fun IrBuilderScope.irField(
	name: Name,
	type: IrType,
	visibility: DescriptorVisibility,
	isFinal: Boolean,
	isExternal: Boolean = false,
	isStatic: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin
): IrField {
	val symbol = IrFieldSymbolImpl()
	
	val field = irFactory.createField(
		startOffset, endOffset,
		origin = origin,
		symbol = symbol, name = name, type = type,
		visibility = visibility,
		isFinal = isFinal, isExternal = isExternal, isStatic = isStatic
	)
	field.parent = scope.getLocalDeclarationParent()
	
	return field
}

fun IrBuilderScope.irOverrideProperty(
	overrideTarget: IrPropertySymbol,
	type: IrType = overrideTarget.owner.propertyType,
	visibility: DescriptorVisibility = overrideTarget.owner.visibility,
	modality: Modality = Modality.OPEN,
	isVar: Boolean = overrideTarget.owner.isVar,
	isLateinit: Boolean = false,
	isDelegated: Boolean = false,
	isExpect: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	containerSource: DeserializedContainerSource? = null,
	init: IrPropertyScope.(IrProperty) -> Unit
): IrProperty = irProperty(
	name = overrideTarget.owner.name, type = type,
	visibility = visibility, modality = modality,
	isVar = isVar, isLateinit = isLateinit, isDelegated = isDelegated, isExpect = isExpect,
	origin = origin, containerSource = containerSource) {
	init(it)
}


fun IrPropertyScope.irPropertyGetter(
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	isInline: Boolean = false,
	body: (IrFunctionScope.(IrSimpleFunction, thisParameter: IrValueParameterSymbol) -> IrBody?)? = null
): IrSimpleFunction {
	val property = irElement
	
	return irSimpleFunction(
		name = Name.special("<get-${property.name}>"),
		visibility = visibility,
		isInline = isInline,
		returnType = irPropertyType,
		origin = if(body == null) IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR else sBackendDeclarationOrigin
	) { function ->
		addDispatchReceiver()
		val thisParameter = function.dispatchReceiverParameter!!.symbol
		
		function.correspondingPropertySymbol = property.symbol
		
		if(body != null) {
			body(function, thisParameter)
		} else { // default property accessor
			irExpressionBody(irGet(property.backingField!!.symbol, receiver = irGet(thisParameter)))
		}
	}
}

fun IrPropertyScope.irPropertySetter(
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	isInline: Boolean = false,
	body: (IrFunctionScope.(IrSimpleFunction, thisParameter: IrValueParameterSymbol, valueParameter: IrValueParameterSymbol) -> IrBody?)? = null
): IrSimpleFunction {
	val property = irElement
	val propertyType = irPropertyType
	return irSimpleFunction(
		name = Name.special("<set-${property.name}>"),
		visibility = visibility,
		isInline = isInline,
		returnType = irBuiltIns.unitType,
		origin = if(body == null) IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR else sBackendDeclarationOrigin
	) { function ->
		function.createDispatchReceiverParameter()
		val thisParameter = function.dispatchReceiverParameter!!.symbol
		val valueParameter = function.addValueParameter("value", propertyType).symbol
		
		function.correspondingPropertySymbol = property.symbol
		
		if(body != null) {
			body(function, thisParameter, valueParameter)
		} else irExpressionBody(
			irSet(property.backingField!!.symbol, receiver = irGet(thisParameter), value = irGet(valueParameter))
		)
	}
}

fun IrPropertyScope.irBackingField(initializer: (IrBuilderScope.() -> IrExpression)? = null): IrField {
	val property = irElement
	
	val field = irField(
		name = property.name,
		type = irPropertyType,
		visibility = with(property) {
			when { // from fir2ir: org.jetbrains.kotlin.psi2ir.generators/PropertyGenerator.kt
				isLateinit -> setter?.visibility ?: visibility
				isConst -> visibility
				else -> DescriptorVisibilities.PRIVATE
			}
		},
		isFinal = !property.isVar
	)
	field.correspondingPropertySymbol = property.symbol
	if(initializer != null) field.initializer = irExpressionBody(field.scope.initializer())
	
	return field
}


// Variables

fun IrBuilderScope.irVariable(
	name: String,
	type: IrType,
	isVar: Boolean = false,
	isConst: Boolean = false,
	isLateinit: Boolean = false,
	annotations: List<IrConstructorCall> = emptyList(),
	origin: IrDeclarationOrigin = IrDeclarationOrigin.DEFINED
): IrVariable {
	val name2 = Name.guessByFirstCharacter(name)
	val variable = IrVariableImpl(
		startOffset, endOffset, origin, IrVariableSymbolImpl(), name2, type, isVar, isConst, isLateinit
	)
	variable.annotations = annotations
	return variable
}

fun IrBuilderScope.irVariable(
	name: String,
	type: IrType,
	isVar: Boolean = false,
	isConst: Boolean = false,
	isLateinit: Boolean = false,
	annotations: List<IrConstructorCall> = emptyList(),
	origin: IrDeclarationOrigin = IrDeclarationOrigin.DEFINED,
	initializer: IrExpression
): IrVariable = irVariable(name, type, isVar, isConst, isLateinit, annotations, origin).also {
	it.initializer = initializer
}

fun IrStatementsScope.irTemporary(
	value: IrExpression,
	nameHint: String? = null,
	type: IrType? = null
): IrVariable {
	val temporary = scope.createTemporaryVariable(value, nameHint, irType = type)
	+temporary
	return temporary
}

fun IrStatementsScope.irTemporaryVar(
	value: IrExpression,
	nameHint: String? = null,
	type: IrType? = null
): IrVariable {
	val temporary = scope.createTemporaryVariable(value, nameHint, isMutable = true, irType = type)
	+temporary
	return temporary
}

fun IrStatementsScope.irTemporaryVariable(
	type: IrType,
	nameHint: String? = null,
	isMutable: Boolean = true
): IrVariable {
	val temporary = scope.createTemporaryVariableDeclaration(type, nameHint, isMutable = isMutable)
	+temporary
	return temporary
}

fun IrBuilderScope.irCreateTemporary(
	value: IrExpression,
	nameHint: String? = null,
	type: IrType? = null
): IrVariable = scope.createTemporaryVariable(value, nameHint, irType = type)

fun IrBuilderScope.irCreateTemporaryVar(
	value: IrExpression,
	nameHint: String? = null,
	type: IrType? = null
): IrVariable = scope.createTemporaryVariable(value, nameHint, isMutable = true, irType = type)

fun IrBuilderScope.irCreateTemporaryVariable(
	type: IrType,
	nameHint: String? = null,
	isMutable: Boolean = true
): IrVariable = scope.createTemporaryVariableDeclaration(type, nameHint, isMutable = isMutable)


// Body

class IrBlockBodyBuilder(override val startOffset: Int, override val endOffset: Int, override val scope: Scope) :
	IrStatementsScope {
	val statements = mutableListOf<IrStatement>()
	
	override fun <T : IrStatement> T.unaryPlus(): T {
		statements += this
		return this
	}
	
	fun build(): IrBlockBody = IrBlockBodyImpl(startOffset, endOffset, statements)
}


@Suppress("NOTHING_TO_INLINE", "unused")
inline fun IrBuilderScope.irNoBody(): IrBody? = null

inline fun IrBuilderScope.irBlockBody(
	body: IrBlockBodyBuilder.() -> Unit
): IrBlockBody = IrBlockBodyBuilder(startOffset, endOffset, scope).apply(body).build()

fun IrBuilderScope.irExpressionBody(
	body: IrExpression
): IrExpressionBody = irFactory.createExpressionBody(startOffset, endOffset, body)
