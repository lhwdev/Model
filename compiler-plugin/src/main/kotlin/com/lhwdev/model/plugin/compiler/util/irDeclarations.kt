package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.backend.common.ir.createDispatchReceiverParameter
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.descriptors.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.overrides.IrOverridingUtil
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.types.Variance


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

interface IrClassScope : IrDeclarationsScope {
	override val irElement: IrClass
	val overridingUtil: IrOverridingUtil
}

val IrClassScope.defaultType: IrType get() = irElement.defaultType
fun IrClassScope.typeWith(vararg arguments: IrType): IrType = irElement.typeWith(*arguments)

private class IrClassScopeImpl(
	override val irElement: IrClass,
	overridingUtil: IrOverridingUtil? = null,
	override val scope: Scope = Scope(irElement.symbol)
) : IrClassScope {
	var mOverridingUtil = overridingUtil
	override val overridingUtil: IrOverridingUtil
		get() = mOverridingUtil ?: run {
			val util = IrOverridingUtil(irBuiltIns, context.linker.fakeOverrideBuilder)
			mOverridingUtil = util
			util
		}
	
	override val declarations: MutableList<IrDeclaration> get() = irElement.declarations
}

val IrClass.scope: IrClassScope get() = IrClassScopeImpl(this)

fun IrClassScope.overrideAllDefault() {
	overridingUtil.buildFakeOverridesForClass(irElement)
}

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
	val descriptor = WrappedClassDescriptor()
	val symbol = IrClassSymbolImpl(descriptor)
	
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
	descriptor.bind(aClass)
	
	aClass.parent = scope.getLocalDeclarationParent()
	
	val thisReceiverDescriptor = WrappedValueParameterDescriptor()
	val thisReceiver = irFactory.createValueParameter(
		startOffset, endOffset,
		origin = IrDeclarationOrigin.INSTANCE_RECEIVER,
		symbol = IrValueParameterSymbolImpl(thisReceiverDescriptor),
		name = Name.special("<this>"),
		index = -1,
		type = aClass.typeWith(aClass.typeParameters.map { it.defaultType }),
		varargElementType = null,
		isCrossinline = false, isNoinline = false, isHidden = false, isAssignable = false
	)
	thisReceiverDescriptor.bind(thisReceiver)
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

interface IrMemberScope : IrBuilderScope {
	val irParentClass: IrClass
}


// Function

interface IrFunctionScope : IrBuilderScope {
	override val irElement: IrFunction
	
	fun generateNameForDispatchReceiver(type: IrType): Name = Name.special("<this>")
	fun generateNameForExtensionReceiver(type: IrType): Name = Name.special("<this>")
}

val IrFunction.scope: IrFunctionScope
	get() = when(this) {
		is IrSimpleFunction -> scope
		is IrConstructor -> scope
		else -> error("unknown function kind")
	}

fun IrFunctionScope.irValueParameter(
	name: Name,
	type: IrType,
	index: Int = irElement.valueParameters.size,
	isCrossinline: Boolean = false,
	isNoinline: Boolean = false,
	isHidden: Boolean = false,
	isAssignable: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	defaultValue: (IrBuilderScope.() -> IrExpression)? = null
): IrValueParameter {
	val descriptor = WrappedValueParameterDescriptor()
	val parameter = irFactory.createValueParameter(
		startOffset, endOffset,
		origin = origin,
		symbol = IrValueParameterSymbolImpl(descriptor),
		name = name,
		index = index,
		type = type,
		varargElementType = null,
		isCrossinline = isCrossinline, isNoinline = isNoinline, isHidden = isHidden, isAssignable = isAssignable
	)
	descriptor.bind(parameter)
	parameter.parent = scope.getLocalDeclarationParent()
	
	if(defaultValue != null) parameter.defaultValue = with(parameter.scope) {
		irExpressionBody(defaultValue())
	}
	
	return parameter
}

fun IrFunctionScope.addValueParameter(
	name: Name,
	type: IrType,
	index: Int = irElement.valueParameters.size,
	isCrossinline: Boolean = false,
	isNoinline: Boolean = false,
	isHidden: Boolean = false,
	isAssignable: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	defaultValue: (IrBuilderScope.() -> IrExpression)? = null
): IrValueParameter {
	val parameter = irValueParameter(
		name = name,
		type = type,
		index = index,
		isCrossinline = isCrossinline, isNoinline = isNoinline, isHidden = isHidden, isAssignable = isAssignable,
		origin = origin,
		defaultValue = defaultValue
	)
	addValueParameter(parameter, index)
	return parameter
}

fun IrFunctionScope.addValueParameter(parameter: IrValueParameter, index: Int = parameter.index) {
	irElement.valueParameters = irElement.valueParameters.withAdded(index, parameter)
}

fun IrFunctionScope.addDispatchReceiver(
	type: IrType = (irElement.parent as IrClass).defaultType,
	name: Name = generateNameForDispatchReceiver(type)
): IrValueParameter {
	check(irElement.dispatchReceiverParameter == null)
	
	val parameter = irValueParameter(
		name = name,
		type = type,
		index = -1
	)
	irElement.dispatchReceiverParameter = parameter
	return parameter
}

fun IrFunctionScope.addExtensionReceiver(
	type: IrType,
	name: Name = generateNameForExtensionReceiver(type)
): IrValueParameter {
	check(irElement.extensionReceiverParameter == null)
	
	val parameter = irValueParameter(
		name = name,
		type = type,
		index = -1
	)
	irElement.extensionReceiverParameter = parameter
	return parameter
}

fun IrFunctionScope.addTypeParameter(
	name: Name,
	index: Int = irElement.typeParameters.size,
	variance: Variance,
	superTypes: List<IrType> = emptyList(),
	isReified: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin
): IrTypeParameter {
	val descriptor = WrappedTypeParameterDescriptor()
	val parameter = irFactory.createTypeParameter(
		startOffset, endOffset,
		origin = origin,
		symbol = IrTypeParameterSymbolImpl(descriptor),
		name = name,
		index = index,
		isReified = isReified,
		variance = variance
	)
	descriptor.bind(parameter)
	parameter.parent = scope.getLocalDeclarationParent()
	
	parameter.superTypes = superTypes
	irElement.typeParameters = irElement.typeParameters.withAdded(index, parameter)
	
	return parameter
}


interface IrSimpleFunctionScope : IrFunctionScope {
	override val irElement: IrSimpleFunction
}

private class IrSimpleFunctionScopeImpl(
	override val irElement: IrSimpleFunction,
	override val scope: Scope = Scope(irElement.symbol)
) : IrSimpleFunctionScope

val IrSimpleFunction.scope: IrSimpleFunctionScope get() = IrSimpleFunctionScopeImpl(this)

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
	val descriptor = WrappedSimpleFunctionDescriptor()
	val symbol = IrSimpleFunctionSymbolImpl(descriptor)
	
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
	
	descriptor.bind(function)
	
	function.parent = scope.getLocalDeclarationParent()
	function.body = IrSimpleFunctionScopeImpl(function).init(function)
	
	return function
}


interface IrOverrideFunctionScope : IrMemberFunctionScope {
	val overrideTarget: IrSimpleFunctionSymbol
}

private class IrOverrideFunctionScopeImpl(
	override val irElement: IrSimpleFunction,
	override val overrideTarget: IrSimpleFunctionSymbol,
	override val irParentClass: IrClass,
	override val scope: Scope = Scope(irElement.symbol)
) : IrOverrideFunctionScope

fun IrOverrideFunctionScope.defaultParameters() {
	defaultValueParameters()
	defaultTypeParameters()
}

fun IrOverrideFunctionScope.defaultValueParameters() {
	fun IrValueParameter.copy(): IrValueParameter = irValueParameter(
		name = name,
		type = type, index = index,
		isCrossinline = isCrossinline, isNoinline = isNoinline, isHidden = isHidden,
		isAssignable = isAssignable, origin = origin
	).also {
		it.varargElementType = varargElementType
	}
	
	overrideTarget.owner.dispatchReceiverParameter?.let {
		irElement.dispatchReceiverParameter = it.copy()
	}
	
	overrideTarget.owner.extensionReceiverParameter?.let {
		irElement.extensionReceiverParameter = it.copy()
	}
	
	overrideTarget.owner.valueParameters.forEach {
		addValueParameter(it.copy())
	}
}

fun IrOverrideFunctionScope.defaultTypeParameters() {
	overrideTarget.owner.typeParameters.forEach {
		addTypeParameter(
			name = it.name,
			index = it.index, variance = it.variance, superTypes = it.superTypes,
			isReified = it.isReified, origin = it.origin
		)
	}
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


interface IrMemberFunctionScope : IrSimpleFunctionScope, IrMemberScope

val IrMemberFunctionScope.irThisClass: IrValueParameter
	get() = irElement.dispatchReceiverParameter!!

val IrMemberFunctionScope.irThis: IrValueParameter
	get() = irElement.extensionReceiverParameter ?: irElement.dispatchReceiverParameter
	?: error("something that can be called 'this' (extensionReceiverParameter or dispatchReceiverParameter) do not exist")

private class IrMemberFunctionScopeImpl(
	override val irElement: IrSimpleFunction,
	override val irParentClass: IrClass,
	override val scope: Scope = Scope(irElement.symbol)
) : IrMemberFunctionScope

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


val IrClassScope.mainSuperClass: IrClass
	get() = irElement.superTypes.mapNotNull { it.classifierOrNull?.owner as? IrClass }
		.find { it.kind == ClassKind.CLASS }
		?: irBuiltIns.anyClass.owner

interface IrConstructorScope : IrFunctionScope {
	override val irElement: IrConstructor
}

private class IrConstructorScopeImpl(
	override val irElement: IrConstructor,
	override val scope: Scope = Scope(irElement.symbol)
) : IrConstructorScope

val IrConstructor.scope: IrConstructorScope get() = IrConstructorScopeImpl(this)

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
	val descriptor = WrappedClassConstructorDescriptor()
	val symbol = IrConstructorSymbolImpl(descriptor)
	
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
	
	descriptor.bind(function)
	
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

interface IrPropertyScope : IrBuilderScope {
	override val irElement: IrProperty
	val irPropertyType: IrType
}

private class IrPropertyScopeImpl(
	override val irElement: IrProperty,
	override val irPropertyType: IrType,
	override val startOffset: Int = irElement.startOffset,
	override val endOffset: Int = irElement.endOffset,
	override val scope: Scope = Scope(irElement.symbol)
) : IrPropertyScope

private class IrInferredPropertyScopeImpl(
	override val irElement: IrProperty,
	override val startOffset: Int = irElement.startOffset,
	override val endOffset: Int = irElement.endOffset,
	override val scope: Scope = Scope(irElement.symbol)
) : IrPropertyScope {
	override val irPropertyType: IrType get() = irElement.propertyType
}

val IrProperty.scope: IrPropertyScope get() = IrInferredPropertyScopeImpl(this)

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
	descriptor: WrappedPropertyDescriptor = WrappedPropertyDescriptor(),
	init: IrPropertyScope.(IrProperty) -> Unit
): IrProperty {
	val symbol = IrPropertySymbolImpl(descriptor)
	
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
	
	descriptor.bind(property)
	
	property.parent = scope.getLocalDeclarationParent()
	IrPropertyScopeImpl(property, type, startOffset, endOffset, Scope(symbol)).init(property)
	
	return property
}

interface IrMemberPropertyScope : IrPropertyScope, IrMemberScope

private class IrMemberPropertyScopeImpl(
	override val irElement: IrProperty,
	override val irPropertyType: IrType,
	override val irParentClass: IrClass,
	override val scope: Scope = Scope(irElement.symbol)
) : IrMemberPropertyScope

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
	val descriptor = WrappedFieldDescriptor()
	val symbol = IrFieldSymbolImpl(descriptor)
	
	val field = irFactory.createField(
		startOffset, endOffset,
		origin = origin,
		symbol = symbol, name = name, type = type,
		visibility = visibility,
		isFinal = isFinal, isExternal = isExternal, isStatic = isStatic
	)
	descriptor.bind(field)
	field.parent = scope.getLocalDeclarationParent()
	
	return field
}


class WrappedOverriddenPropertyDescriptor(val target: MutableCollection<PropertyDescriptor>) :
	WrappedPropertyDescriptor() {
	override fun getOverriddenDescriptors(): MutableCollection<out PropertyDescriptor> = target
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
	origin = origin, containerSource = containerSource,
	descriptor = @OptIn(ObsoleteDescriptorBasedAPI::class) WrappedOverriddenPropertyDescriptor(
		mutableListOf(
			overrideTarget.descriptor
		)
	)
) {
	
	init(it)
}


fun IrPropertyScope.irPropertyGetter(
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	isInline: Boolean = false,
	body: (IrBlockBodyBuilder.(IrSimpleFunction, thisParameter: IrValueParameterSymbol) -> Unit)? = null
): IrSimpleFunction {
	val property = irElement
	
	return irSimpleFunction(
		name = Name.special("<get-${property.name}>"),
		visibility = visibility,
		isInline = isInline,
		returnType = irPropertyType,
		origin = if(body == null) IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR else sBackendDeclarationOrigin
	) { function ->
		function.createDispatchReceiverParameter()
		val thisParameter = function.dispatchReceiverParameter!!.symbol
		
		function.correspondingPropertySymbol = property.symbol
		
		irBlockBody {
			if(body != null) {
				body(function, thisParameter)
			} else { // default property accessor
				+irReturn(irGet(property.backingField!!.symbol, receiver = irGet(thisParameter)))
			}
		}
	}
}

fun IrPropertyScope.irPropertySetter(
	visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
	isInline: Boolean = false,
	body: (IrBlockBodyBuilder.(
		IrSimpleFunction, thisParameter: IrValueParameterSymbol, valueParameter: IrValueParameterSymbol
	) -> Unit)? = null
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
		
		irBlockBody {
			if(body != null) {
				body(function, thisParameter, valueParameter)
			} else { // default property accessor
				+irSet(property.backingField!!.symbol, receiver = irGet(thisParameter), value = irGet(valueParameter))
			}
		}
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
	val descriptor = WrappedVariableDescriptor()
	val variable = IrVariableImpl(
		startOffset, endOffset, origin, IrVariableSymbolImpl(descriptor), name2, type, isVar, isConst, isLateinit
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


