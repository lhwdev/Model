package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.overrides.IrOverridingUtil
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrTypeParameterSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance


interface IrClassScope : IrDeclarationsScope {
	override val irElement: IrClass
	val overridingUtil: IrOverridingUtil
}

val IrClassScope.defaultType: IrType get() = irElement.defaultType
fun IrClassScope.typeWith(vararg arguments: IrType): IrType = irElement.typeWith(*arguments)

class IrClassScopeImpl(
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
	val parameter = irFactory.createValueParameter(
		startOffset, endOffset,
		origin = origin,
		symbol = IrValueParameterSymbolImpl(),
		name = name,
		index = index,
		type = type,
		varargElementType = null,
		isCrossinline = isCrossinline, isNoinline = isNoinline, isHidden = isHidden, isAssignable = isAssignable
	)
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
	type: IrType = irElement.parentAsClass.defaultType,
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
	val parameter = irFactory.createTypeParameter(
		startOffset, endOffset,
		origin = origin,
		symbol = IrTypeParameterSymbolImpl(),
		name = name,
		index = index,
		isReified = isReified,
		variance = variance
	)
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
val IrSimpleFunction.memberScope: IrMemberFunctionScope get() = IrMemberFunctionScopeImpl(this, parentAsClass)

interface IrOverrideFunctionScope : IrMemberFunctionScope {
	val overrideTarget: IrSimpleFunctionSymbol
}

class IrOverrideFunctionScopeImpl(
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


interface IrMemberFunctionScope : IrSimpleFunctionScope, IrMemberScope

val IrMemberFunctionScope.irThisClass: IrValueParameter
	get() = irElement.dispatchReceiverParameter!!

val IrMemberFunctionScope.irThis: IrValueParameter
	get() = irElement.extensionReceiverParameter ?: irElement.dispatchReceiverParameter
	?: error("something that can be called 'this' (extensionReceiverParameter or dispatchReceiverParameter) do not exist")

class IrMemberFunctionScopeImpl(
	override val irElement: IrSimpleFunction,
	override val irParentClass: IrClass,
	override val scope: Scope = Scope(irElement.symbol)
) : IrMemberFunctionScope

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

fun IrProperty.scopeOf(type: IrType): IrPropertyScope = IrPropertyScopeImpl(this, type)

private class IrInferredPropertyScopeImpl(
	override val irElement: IrProperty,
	override val startOffset: Int = irElement.startOffset,
	override val endOffset: Int = irElement.endOffset,
	override val scope: Scope = Scope(irElement.symbol)
) : IrPropertyScope {
	override val irPropertyType: IrType get() = irElement.propertyType
}

val IrProperty.scope: IrPropertyScope get() = IrInferredPropertyScopeImpl(this)

interface IrMemberPropertyScope : IrPropertyScope, IrMemberScope

class IrMemberPropertyScopeImpl(
	override val irElement: IrProperty,
	override val irPropertyType: IrType,
	override val irParentClass: IrClass,
	override val scope: Scope = Scope(irElement.symbol)
) : IrMemberPropertyScope
