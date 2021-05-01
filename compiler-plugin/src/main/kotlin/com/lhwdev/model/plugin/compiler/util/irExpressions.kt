@file:Suppress("NOTING_TO_INLINE", "NOTHING_TO_INLINE", "unused")

package com.lhwdev.model.plugin.compiler.util

import com.lhwdev.model.plugin.compiler.dumpSrcHead
import com.lhwdev.model.plugin.compiler.dumpSrcHeadColored
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.IrReturnableBlockSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.OperatorNameConventions
import kotlin.math.abs


// also aims to implement all statements which is available by kotlin code(like elvis operator, safe call etc.)


// null / non-null utilities
fun IrElementScope.irIsNull(expression: IrExpression): IrExpression = irEquals(expression, irNull())

fun IrElementScope.irAssertNonNull(
	target: IrExpression,
	type: IrType = target.type.withHasQuestionMark(false)
): IrExpression =
	irCall(
		irBuiltIns.checkNotNullSymbol,
		type = type,
		valueArguments = listOf(target),
		origin = IrStatementOrigin.EXCLEXCL
	)

fun IrBuilderScope.irElvisOperator(left: IrExpression, right: IrExpression, type: IrType): IrExpression =
	irBlock(type = type, origin = IrStatementOrigin.ELVIS) {
		val lhs = irTemporary(left, "elvis")
		+irIf(type) {
			irIsNull(irGet(lhs)) then right
			orElse(irGet(lhs))
		}
	}


// Function related

fun IrElementScope.irVararg(type: IrType, elements: List<IrVarargElement>) =
	IrVarargImpl(startOffset, endOffset, context.symbols.array.typeWith(type), type, elements)

private val sExtensionFunctionSymbol by lazy {
	pluginContext.referenceClass(
		kotlinPackageFqn.child(Name.identifier("ExtensionFunctionType"))
	)!!
}

fun toFunctionType(
	valueParameters: List<IrValueParameter>,
	extensionReceiverParameter: IrValueParameter? = null,
	returnType: IrType,
	annotations: List<IrConstructorCall>
) = irBuiltIns.function(valueParameters.size + extensionReceiverParameter.let { if(it == null) 0 else 1 })
	.createType(
		hasQuestionMark = false,
		arguments = (listOfNotNull(extensionReceiverParameter) + valueParameters)
			.map { makeTypeProjection(it.type, Variance.INVARIANT) } + makeTypeProjection(
			returnType,
			Variance.INVARIANT
		)
	)
	.addAnnotations(
		annotations + listOfNotNull(
			if(extensionReceiverParameter == null) null else IrConstructorCallImpl.fromSymbolOwner(
				sExtensionFunctionSymbol.defaultType, sExtensionFunctionSymbol.owner.constructors.first().symbol
			)
		)
	)


interface IrExpressionFunctionScope : IrFunctionScope

class IrExpressionFunctionScopeImpl(
	override val irElement: IrFunction,
	val scopeName: Name?,
	override val scope: Scope = Scope(irElement.symbol)
) : IrExpressionFunctionScope {
	override fun generateNameForDispatchReceiver(type: IrType): Name =
		error("function for IrFunctionExpression cannot have dispatchReceiver")
	
	// $this$func for valueArgument of func, $this$null otherwise
	override fun generateNameForExtensionReceiver(type: IrType): Name =
		Name.identifier("\$this\$${scopeName?.asString()}")
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Expression function cannot have dispatchReceiver.", level = DeprecationLevel.ERROR)
fun IrExpressionFunctionScope.addDispatchReceiver(): Nothing = error("do not use this")

fun IrBuilderScope.irFunctionExpression(
	returnType: IrType,
	annotations: List<IrConstructorCall> = emptyList(),
	isSuspend: Boolean = false,
	type: IrType? = null,
	argumentOfCall: IrFunctionSymbol? = null,
	origin: IrStatementOrigin = IrStatementOrigin.LAMBDA,
	init: IrExpressionFunctionScope.(IrFunction) -> IrBody?
): IrExpression {
	val function = irSimpleFunction(
		origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA,
		name = SpecialNames.ANONYMOUS_FUNCTION,
		visibility = DescriptorVisibilities.LOCAL,
		modality = Modality.FINAL,
		returnType = returnType,
		isSuspend = isSuspend
	) {
		IrExpressionFunctionScopeImpl(it, argumentOfCall?.owner?.name, scope).init(it)
	}
	
	return irFunctionExpression(
		type ?: toFunctionType(function.valueParameters, function.extensionReceiverParameter, returnType, annotations),
		function, origin
	)
}

fun IrElementScope.irFunctionExpression(
	type: IrType,
	function: IrSimpleFunction,
	origin: IrStatementOrigin
): IrFunctionExpression = IrFunctionExpressionImpl(startOffset, endOffset, type, function, origin)


// Calls

fun IrElementScope.irCall(
	callee: IrSimpleFunctionSymbol,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
	dispatchReceiver: IrExpression? = null,
	extensionReceiver: IrExpression? = null,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null
): IrCall = irCall(callee, type, origin).also {
	valueArguments.forEachIndexed { index, argument -> it.putValueArgument(index, argument) }
	typeArguments.forEachIndexed { index, argument -> it.putTypeArgument(index, argument) }
	it.dispatchReceiver = dispatchReceiver
	it.extensionReceiver = extensionReceiver
}

fun IrElementScope.irCall(
	callee: IrSimpleFunction,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
	dispatchReceiver: IrExpression? = null,
	extensionReceiver: IrExpression? = null,
	type: IrType = callee.returnType,
	origin: IrStatementOrigin? = null
) =
	irCall(callee.symbol, valueArguments, typeArguments, dispatchReceiver, extensionReceiver, type, origin)

fun IrElementScope.irConstructorCall(
	callee: IrConstructorSymbol,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null
): IrConstructorCall = irConstructorCall(callee, type, origin).also {
	valueArguments.forEachIndexed { index, argument -> it.putValueArgument(index, argument) }
	typeArguments.forEachIndexed { index, argument -> it.putTypeArgument(index, argument) }
}

fun IrElementScope.irConstructorCall(
	callee: IrConstructor,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
	type: IrType = callee.returnType,
	origin: IrStatementOrigin? = null
) = irConstructorCall(callee.symbol, valueArguments, typeArguments, type, origin)

inline fun IrElementScope.irCall(
	callee: IrFunctionSymbol,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null,
	block: IrFunctionAccessExpression.() -> Unit
): IrFunctionAccessExpression =
	irCall(callee, type, origin).apply(block)

inline fun IrElementScope.irCall(
	callee: IrFunction,
	type: IrType = callee.returnType,
	origin: IrStatementOrigin? = null,
	block: IrFunctionAccessExpression.() -> Unit
): IrFunctionAccessExpression =
	irCall(callee.symbol, type, origin).apply(block)

fun IrElementScope.irCall(
	callee: IrFunction,
	type: IrType = callee.returnType,
	origin: IrStatementOrigin? = null
): IrFunctionAccessExpression =
	irCall(callee.symbol, type, origin)

fun IrElementScope.irCall(
	callee: IrFunction,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
	dispatchReceiver: IrExpression? = null,
	extensionReceiver: IrExpression? = null,
	type: IrType = callee.returnType,
	origin: IrStatementOrigin? = null
) =
	irCall(callee.symbol, valueArguments, typeArguments, dispatchReceiver, extensionReceiver, type, origin)

fun IrElementScope.irCall(
	callee: IrFunctionSymbol,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
	dispatchReceiver: IrExpression? = null,
	extensionReceiver: IrExpression? = null,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null
) = irCall(callee, type, origin).also {
	valueArguments.forEachIndexed { index, argument -> it.putValueArgument(index, argument) }
	typeArguments.forEachIndexed { index, argument -> it.putTypeArgument(index, argument) }
	it.dispatchReceiver = dispatchReceiver
	it.extensionReceiver = extensionReceiver
}

fun IrElementScope.irCall(
	callee: IrFunctionSymbol,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null
): IrFunctionAccessExpression =
	when(callee) {
		is IrSimpleFunctionSymbol -> irCall(callee, type, origin = origin)
		is IrConstructorSymbol -> irConstructorCall(callee, type, origin = origin)
		else -> error("unexpected function symbol $callee")
	}

inline fun IrElementScope.irConstructorCall(
	callee: IrConstructorSymbol,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null,
	block: IrConstructorCall.() -> Unit
): IrConstructorCall =
	irConstructorCall(callee, type, origin).apply(block)

inline fun IrElementScope.irConstructorCall(
	callee: IrConstructor,
	type: IrType = callee.returnType,
	origin: IrStatementOrigin? = null,
	block: IrConstructorCall.() -> Unit
): IrConstructorCall =
	irConstructorCall(callee, type, origin).apply(block)

fun IrElementScope.irConstructorCall(
	callee: IrConstructor,
	type: IrType = callee.returnType,
	origin: IrStatementOrigin? = null
): IrConstructorCall =
	irConstructorCall(callee.symbol, type, origin)

fun IrElementScope.irConstructorCall(
	callee: IrConstructorSymbol,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null
): IrConstructorCall =
	IrConstructorCallImpl.fromSymbolOwner(type, callee, origin)

inline fun IrElementScope.irCall(
	callee: IrSimpleFunctionSymbol,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null,
	block: IrCall.() -> Unit
): IrCall =
	irCall(callee, type, origin).apply(block)

fun IrElementScope.irCall(
	callee: IrSimpleFunctionSymbol,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null,
	superQualifierSymbol: IrClassSymbol? = null
): IrCall = IrCallImpl.fromSymbolOwner(
	startOffset, endOffset,
	type = type, symbol = callee, origin = origin, superQualifierSymbol = superQualifierSymbol
)

fun IrElementScope.irDelegatingConstructorCall(
	callee: IrConstructorSymbol,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
): IrDelegatingConstructorCall = IrDelegatingConstructorCallImpl(
	startOffset, endOffset, irBuiltIns.unitType, callee,
	callee.owner.parentAsClass.typeParameters.size, callee.owner.valueParameters.size
).also { call ->
	valueArguments.forEachIndexed { index, argument -> call.putValueArgument(index, argument) }
	typeArguments.forEachIndexed { index, argument -> call.putTypeArgument(index, argument) }
}

fun IrConstructorScope.irInstanceInitializerCall(classSymbol: IrClassSymbol): IrInstanceInitializerCall =
	IrInstanceInitializerCallImpl(startOffset, endOffset, classSymbol, classSymbol.defaultType)

fun IrConstructorScope.irInstanceInitializerCall(): IrInstanceInitializerCall =
	irInstanceInitializerCall((scope.getLocalDeclarationParent() as IrClass).symbol)

sealed class IrSafeCallReceiver(val expression: IrExpression) {
	class Extension(expression: IrExpression) : IrSafeCallReceiver(expression)
	class Dispatch(expression: IrExpression) : IrSafeCallReceiver(expression)
}

fun IrBuilderScope.irSafeCall(
	callee: IrSimpleFunctionSymbol,
	safeReceiver: IrSafeCallReceiver,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null,
	block: IrCall.() -> Unit
): IrExpression = irBlock(type = type, origin = IrStatementOrigin.SAFE_CALL) {
	val receiver = irTemporary(safeReceiver.expression)
	+irIf(type, origin = IrStatementOrigin.SAFE_CALL) {
		irEquals(irGet(receiver), irNull()) then irNull()
		orElse(irCall(callee, type = type, origin = origin, block = block))
	}
}

fun IrBuilderScope.irSafeCall(
	callee: IrSimpleFunctionSymbol,
	valueArguments: List<IrExpression?> = emptyList(),
	typeArguments: List<IrType?> = emptyList(),
	safeReceiver: IrSafeCallReceiver,
	dispatchReceiver: IrExpression? = null,
	extensionReceiver: IrExpression? = null,
	type: IrType = callee.owner.returnType,
	origin: IrStatementOrigin? = null,
	block: (IrCall.() -> Unit)? = null
) = irSafeCall(callee, safeReceiver, type, origin) {
	this.dispatchReceiver = dispatchReceiver
	this.extensionReceiver = extensionReceiver
	valueArguments.forEachIndexed { index, argument ->
		putValueArgument(index, argument)
	}
	typeArguments.forEachIndexed { index, argument ->
		putTypeArgument(index, argument)
	}
	block?.invoke(this)
}

fun IrElementScope.irInvoke(
	functionSymbol: IrSimpleFunctionSymbol,
	functionalTypeReceiver: IrExpression,
	valueArguments: List<IrExpression>
) = irCall(
	functionSymbol,
	dispatchReceiver = functionalTypeReceiver,
	valueArguments = valueArguments,
	origin = IrStatementOrigin.INVOKE
)

fun IrElementScope.irInvoke(
	functionalTypeReceiver: IrValueSymbol,
	valueArguments: List<IrExpression>,
	valueParametersCount: Int = valueArguments.size
) =
	irInvoke(
		functionalTypeReceiver = irGet(functionalTypeReceiver, origin = IrStatementOrigin.VARIABLE_AS_FUNCTION),
		valueArguments = valueArguments, valueParametersCount = valueParametersCount
	)

fun IrElementScope.irInvoke(
	functionalTypeReceiver: IrExpression,
	valueArguments: List<IrExpression>,
	valueParametersCount: Int = valueArguments.size
) = irInvoke(
	functionSymbol = irBuiltIns.function(valueParametersCount).getSimpleFunction("invoke")!!,
	functionalTypeReceiver = functionalTypeReceiver,
	valueArguments = valueArguments
)

// Reference

fun IrElementScope.irReference(
	target: IrSymbol,
	type: IrType,
	origin: IrStatementOrigin? = null
): IrDeclarationReference =
	when(target) {
		is IrClassSymbol ->
			IrClassReferenceImpl(startOffset, endOffset, type, target, target.defaultType)
		is IrFunctionSymbol -> IrFunctionReferenceImpl.fromSymbolOwner(
			startOffset, endOffset,
			type = type, symbol = target,
			origin = origin,
			reflectionTarget = null,
			typeArgumentsCount = target.owner.typeParameters.size
		)
		is IrPropertySymbol -> target.owner.let { owner ->
			IrPropertyReferenceImpl(
				startOffset,
				endOffset,
				type = type,
				symbol = target,
				typeArgumentsCount = (type as? IrSimpleType)?.arguments?.size
					?: 0, // reference: org.jetbrains.kotlin.fir.backend.generators.CallAndReferenceGenerator:111
				field = owner.backingField?.symbol,
				getter = owner.getter?.symbol,
				setter = owner.setter?.symbol,
				origin = origin
			)
		}
		is IrLocalDelegatedPropertySymbol -> target.owner.let { owner ->
			IrLocalDelegatedPropertyReferenceImpl(
				startOffset,
				endOffset,
				type,
				target,
				owner.delegate.symbol,
				owner.getter.symbol,
				owner.setter?.symbol,
				origin
			)
		}
		else -> error("unexpected symbol $target")
	}


// Get / Set

fun IrElementScope.irGet(
	variable: IrValueDeclaration,
	type: IrType = variable.type,
	origin: IrStatementOrigin? = null
): IrGetValue = IrGetValueImpl(startOffset, endOffset, type, variable.symbol, origin)

fun IrElementScope.irGet(
	symbol: IrValueSymbol,
	type: IrType = symbol.owner.type,
	origin: IrStatementOrigin? = null
): IrGetValue = IrGetValueImpl(startOffset, endOffset, type, symbol, origin)

fun IrElementScope.irGet(
	symbol: IrFieldSymbol,
	receiver: IrExpression?,
	type: IrType = symbol.owner.type,
	superQualifierSymbol: IrClassSymbol? = null,
	origin: IrStatementOrigin? = null
): IrGetField = IrGetFieldImpl(startOffset, endOffset, symbol, type, receiver, origin, superQualifierSymbol)

fun IrElementScope.irGet(
	getterSymbol: IrSimpleFunctionSymbol,
	receiver: IrExpression?,
	type: IrType = getterSymbol.owner.returnType
): IrCall = IrCallImpl(
	startOffset, endOffset,
	type,
	getterSymbol,
	typeArgumentsCount = getterSymbol.owner.typeParameters.size,
	valueArgumentsCount = 0,
	origin = IrStatementOrigin.GET_PROPERTY
).apply {
	dispatchReceiver = receiver
}

fun IrElementScope.irGet(
	propertySymbol: IrPropertySymbol,
	receiver: IrExpression?,
	type: IrType = propertySymbol.owner.propertyType
): IrExpression {
	val property = propertySymbol.owner
	
	return when {
		property.getter != null -> irGet(property.getter!!.symbol, receiver, type)
		property.backingField != null -> irGet(property.backingField!!.symbol, receiver, type)
		else -> error("malformed property")
	}
}

fun IrElementScope.irGetObject(classSymbol: IrClassSymbol, type: IrType = classSymbol.defaultType): IrGetObjectValue =
	IrGetObjectValueImpl(startOffset, endOffset, type, classSymbol)

fun IrElementScope.irGetCompanion(fromSymbol: IrClassSymbol, type: IrType? = null): IrGetObjectValue? =
	fromSymbol.owner.companionObject()?.let { irGetObject(it.symbol, type ?: it.defaultType) }

val IrClassScope.irThisClass: IrValueSymbol get() = irElement.thisReceiver!!.symbol

fun IrClassScope.irThisClass(): IrGetValue = irGet(irThisClass)


fun IrElementScope.irSet(variable: IrVariable, value: IrExpression): IrSetValue = irSet(variable.symbol, value)

fun IrElementScope.irSet(variable: IrVariableSymbol, value: IrExpression): IrSetValue =
	IrSetValueImpl(startOffset, endOffset, irBuiltIns.unitType, variable, value, IrStatementOrigin.EQ)

fun IrElementScope.irSet(
	symbol: IrFieldSymbol,
	receiver: IrExpression?,
	value: IrExpression,
	superQualifierSymbol: IrClassSymbol? = null,
	origin: IrStatementOrigin? = null,
	type: IrType = irBuiltIns.unitType
): IrSetField =
	IrSetFieldImpl(startOffset, endOffset, symbol, receiver, value, type, origin, superQualifierSymbol)

fun IrElementScope.irSet(
	setterSymbol: IrFunctionSymbol,
	receiver: IrExpression?,
	value: IrExpression,
	type: IrType = irBuiltIns.unitType
): IrCall = IrCallImpl(
	startOffset, endOffset,
	type,
	setterSymbol as IrSimpleFunctionSymbol,
	typeArgumentsCount = setterSymbol.owner.typeParameters.size,
	valueArgumentsCount = 1,
	origin = IrStatementOrigin.EQ
).apply {
	dispatchReceiver = receiver
	putValueArgument(0, value)
}

fun IrElementScope.irSet(
	propertySymbol: IrPropertySymbol,
	receiver: IrExpression?,
	value: IrExpression,
	type: IrType = irBuiltIns.unitType
): IrExpression {
	val property = propertySymbol.owner
	if(!property.isVar) error("tried to set readonly property: ${property.dumpSrcHead()}")
	
	return when {
		property.setter != null -> irSet(property.setter!!.symbol, receiver, value, type = type)
		property.backingField != null -> irSet(property.backingField!!.symbol, receiver, value, type = type)
		else -> error("malformed property")
	}
}


// Exceptions
fun IrElementScope.irThrow(value: IrExpression): IrThrow =
	IrThrowImpl(startOffset, endOffset, value.type, value)

class IrTryBuilder(override val startOffset: Int, override val endOffset: Int, override val scope: Scope) :
	IrBuilderScope {
	@PublishedApi
	internal lateinit var expression: IrTryImpl
	
	inline fun tryBlock(type: IrType? = null, body: IrBlockBuilder.() -> Unit) {
		val block = irBlock(type = type, body = body)
		val expr = IrTryImpl(startOffset, endOffset, block.type)
		expr.tryResult = block
		expression = expr
	}
	
	inline fun catchBlock(
		catchType: IrType,
		catchParameterName: String? = null,
		body: IrBlockBuilder.(catchParameter: IrVariable) -> Unit
	) {
		val catchParameter = irVariable(catchParameterName ?: "catchParameter", catchType)
		expression.catches += IrCatchImpl(startOffset, endOffset, catchParameter, irBlock { body(catchParameter) })
	}
	
	inline fun finallyBlock(type: IrType? = null, body: IrBlockBuilder.() -> Unit) {
		expression.finallyExpression = irBlock(type = type, body = body)
	}
	
	fun build(): IrTry = expression
}

inline fun IrBuilderScope.irTry(block: IrTryBuilder.() -> Unit): IrTry =
	IrTryBuilder(startOffset, endOffset, scope).apply(block).build()


// Branches

inline class IrWhenBuilder(val expression: IrWhen) {
	infix fun IrExpression.then(then: IrExpression) {
		expression.branches += IrBranchImpl(expression.startOffset, expression.endOffset, this, then)
	}
	
	fun orElse(then: IrExpression) {
		expression.branches += IrElseBranchImpl(
			expression.startOffset,
			expression.endOffset,
			expression.scope.irTrue(),
			then
		)
	}
}


inline fun IrElementScope.irWhen(
	type: IrType,
	origin: IrStatementOrigin? = IrStatementOrigin.WHEN,
	block: IrWhenBuilder.() -> Unit
): IrWhen =
	IrWhenBuilder(IrWhenImpl(startOffset, endOffset, type, origin)).apply(block).expression

inline fun IrBuilderScope.irWhenSubject(
	subject: IrExpression,
	type: IrType,
	subjectName: String? = null,
	origin: IrStatementOrigin? = null,
	block: IrWhenBuilder.(subject: IrVariable) -> Unit
) = irBlock(type = type, origin = IrStatementOrigin.WHEN) {
	val subjectVar = if(subjectName == null) irTemporary(subject, nameHint = "subject") else irVariable(
		subjectName,
		subject.type,
		initializer = subject
	)
	+irWhen(type, origin) { block(subjectVar) }
}

inline fun IrElementScope.irIf(
	type: IrType,
	origin: IrStatementOrigin? = IrStatementOrigin.IF,
	block: IrWhenBuilder.() -> Unit
): IrWhen =
	IrWhenBuilder(IrIfThenElseImpl(startOffset, endOffset, type, origin)).apply(block).expression

fun IrElementScope.irIfThen(
	type: IrType,
	condition: IrExpression,
	origin: IrStatementOrigin? = IrStatementOrigin.IF,
	then: IrExpression
): IrWhen =
	IrIfThenElseImpl(startOffset, endOffset, type, origin).apply {
		branches += IrBranchImpl(startOffset, endOffset, condition, then)
	}


class IrWhenBuilderWithScope(val scope: IrBuilderScope, val expression: IrWhen) {
	infix fun IrExpression.then(then: IrExpression) {
		expression.branches += IrBranchImpl(expression.startOffset, expression.endOffset, this, then)
	}
	
	inline infix fun IrExpression.then(then: IrBlockBuilder.() -> Unit) {
		then(this@IrWhenBuilderWithScope.scope.irBlock(type = expression.type, body = then))
	}
	
	fun orElse(then: IrExpression) {
		expression.branches += IrElseBranchImpl(
			expression.startOffset,
			expression.endOffset,
			expression.scope.irTrue(),
			then
		)
	}
	
	inline fun orElse(then: IrBlockBuilder.() -> Unit) {
		orElse(this@IrWhenBuilderWithScope.scope.irBlock(type = expression.type, body = then))
	}
}


inline fun IrBuilderScope.irIfWithScope(type: IrType, block: IrWhenBuilderWithScope.() -> Unit): IrWhen =
	IrWhenBuilderWithScope(
		this,
		IrIfThenElseImpl(startOffset, endOffset, type, IrStatementOrigin.IF)
	).apply(block).expression

fun IrElementScope.irBranch(condition: IrExpression, result: IrExpression): IrBranch =
	IrBranchImpl(startOffset, endOffset, condition, result)

fun IrElementScope.irElseBranch(result: IrExpression): IrElseBranch =
	IrElseBranchImpl(startOffset, endOffset, irTrue(), result)


// Expressions

fun IrElementScope.primitiveOp1(
	primitiveOpSymbol: IrSimpleFunctionSymbol,
	primitiveOpReturnType: IrType,
	origin: IrStatementOrigin,
	dispatchReceiver: IrExpression
): IrExpression = org.jetbrains.kotlin.ir.builders.primitiveOp1(
	startOffset, endOffset, primitiveOpSymbol, primitiveOpReturnType, origin, dispatchReceiver
)

fun IrElementScope.primitiveOp2(
	primitiveOpSymbol: IrSimpleFunctionSymbol, primitiveOpReturnType: IrType,
	origin: IrStatementOrigin,
	argument1: IrExpression, argument2: IrExpression
): IrExpression = org.jetbrains.kotlin.ir.builders.primitiveOp2(
	startOffset, endOffset, primitiveOpSymbol, primitiveOpReturnType, origin, argument1, argument2
)

inline fun IrElementScope.irStringTemplate(block: IrExpressionsBuilder.() -> Unit): IrStringConcatenation =
	IrStringConcatenationImpl(startOffset, endOffset, irBuiltIns.stringType, buildExpressions(block))

fun IrElementScope.irStringTemplate(vararg elements: IrExpression): IrStringConcatenation =
	IrStringConcatenationImpl(startOffset, endOffset, irBuiltIns.stringType, elements.toList())

// logic
fun IrElementScope.irNot(argument: IrExpression, origin: IrStatementOrigin = IrStatementOrigin.EXCL): IrExpression =
	primitiveOp1(irBuiltIns.booleanNotSymbol, irBuiltIns.booleanType, origin, argument)

fun IrElementScope.irLogicAnd(left: IrExpression, right: IrExpression): IrExpression =
	primitiveOp2(irBuiltIns.andandSymbol, irBuiltIns.booleanType, IrStatementOrigin.ANDAND, left, right)

fun IrElementScope.irLogicOr(left: IrExpression, right: IrExpression): IrExpression =
	primitiveOp2(irBuiltIns.ororSymbol, irBuiltIns.booleanType, IrStatementOrigin.OROR, left, right)


// primitive operations

fun IrElementScope.irUnaryOperator(
	name: Name,
	receiver: IrExpression,
	receiverType: IrType = receiver.type,
	type: IrType = receiverType
): IrCall = irCall(
	context.symbols.getUnaryOperator(name, receiverType),
	dispatchReceiver = receiver,
	type = type
)

fun IrElementScope.irBinaryOperator(
	name: Name,
	left: IrExpression,
	right: IrExpression,
	typeLeft: IrType = left.type,
	typeRight: IrType = right.type,
	type: IrType = typeLeft
): IrCall = irCall(
	context.symbols.getBinaryOperator(name, typeLeft, typeRight),
	dispatchReceiver = left,
	valueArguments = listOf(right),
	type = type
)


object OperatorNames {
	val UNARY_PLUS = OperatorNameConventions.UNARY_PLUS
	val UNARY_MINUS = OperatorNameConventions.UNARY_MINUS
	
	val ADD = OperatorNameConventions.PLUS
	val SUB = OperatorNameConventions.MINUS
	val MUL = OperatorNameConventions.TIMES
	val DIV = OperatorNameConventions.DIV
	val MOD = OperatorNameConventions.MOD
	val REM = OperatorNameConventions.REM
	
	val AND = OperatorNameConventions.AND
	val OR = OperatorNameConventions.OR
	val XOR = Name.identifier("xor")
	val INV = Name.identifier("inv")
	
	val SHL = Name.identifier("shl")
	val SHR = Name.identifier("shr")
	val SHRU = Name.identifier("ushr")
	
	val NOT = OperatorNameConventions.NOT
	
	val INC = OperatorNameConventions.INC
	val DEC = OperatorNameConventions.DEC
	
	
	val BINARY = setOf(ADD, SUB, MUL, DIV, MOD, REM, AND, OR, XOR, SHL, SHR, SHRU)
	val UNARY = setOf(UNARY_PLUS, UNARY_MINUS, INV, NOT, INC, DEC)
	val ALL = BINARY + UNARY
}


@Suppress("UNCHECKED_CAST")
fun IrElementScope.irBitOr(left: IrExpression, right: IrExpression) =
	irBinaryOperator(OperatorNames.OR, left, right)

@Suppress("UNCHECKED_CAST")
fun IrElementScope.irBitAnd(left: IrExpression, right: IrExpression) =
	irBinaryOperator(OperatorNames.AND, left, right)

fun IrElementScope.irShl(left: IrExpression, right: IrExpression) =
	irBinaryOperator(OperatorNames.SHL, left, right, typeRight = irBuiltIns.intType)

fun IrElementScope.irShr(left: IrExpression, right: IrExpression) =
	irBinaryOperator(OperatorNames.SHR, left, right, typeRight = irBuiltIns.intType)

fun IrElementScope.irUshr(left: IrExpression, right: IrExpression) =
	irBinaryOperator(OperatorNames.SHRU, left, right, typeRight = irBuiltIns.intType)

fun IrElementScope.irXor(left: IrExpression, right: IrExpression) =
	irBinaryOperator(OperatorNames.XOR, left, right)

fun IrElementScope.irShiftBits(value: IrExpression, bitsToShiftLeft: Int) =
	if(bitsToShiftLeft == 0) value else irBinaryOperator(
		if(bitsToShiftLeft > 0) OperatorNames.SHL else OperatorNames.SHR,
		value,
		irInt(abs(bitsToShiftLeft)),
		typeRight = irBuiltIns.intType
	)

// equals
fun IrElementScope.irEquals(
	argument1: IrExpression,
	argument2: IrExpression,
	origin: IrStatementOrigin = IrStatementOrigin.EQEQ
) =
	primitiveOp2(irBuiltIns.eqeqSymbol, irBuiltIns.booleanType, origin, argument1, argument2)

fun IrElementScope.irNotEquals(argument1: IrExpression, argument2: IrExpression) =
	irNot(irEquals(argument1, argument2, origin = IrStatementOrigin.EXCLEQ), IrStatementOrigin.EXCLEQ)

fun IrElementScope.irEqualsNull(argument: IrExpression) = irEquals(argument, irNull())

fun IrElementScope.isEqualsReferential(argument1: IrExpression, argument2: IrExpression) =
	primitiveOp2(irBuiltIns.eqeqeqSymbol, irBuiltIns.booleanType, IrStatementOrigin.EQEQEQ, argument1, argument2)

// cast
fun IrElementScope.typeOperator(
	resultType: IrType,
	argument: IrExpression,
	typeOperator: IrTypeOperator,
	typeOperand: IrType
) =
	IrTypeOperatorCallImpl(startOffset, endOffset, resultType, typeOperator, typeOperand, argument)

fun IrElementScope.irIs(argument: IrExpression, type: IrType) =
	typeOperator(context.irBuiltIns.booleanType, argument, IrTypeOperator.INSTANCEOF, type)

fun IrElementScope.irNotIs(argument: IrExpression, type: IrType) =
	typeOperator(context.irBuiltIns.booleanType, argument, IrTypeOperator.NOT_INSTANCEOF, type)

fun IrElementScope.irAs(argument: IrExpression, type: IrType) =
	typeOperator(type, argument, IrTypeOperator.CAST, type)

fun IrElementScope.irPrimitiveCast(expression: IrExpression, type: IrType): IrExpression {
	val toName = (type.classifierOrFail.owner as IrDeclarationWithName).name.identifier.substringAfter('.')
	val castFunction = expression.type.classOrNull!!.owner.referenceFunction("to$toName")
	return irCall(castFunction, dispatchReceiver = expression)
}

fun IrElementScope.irImplicitCast(argument: IrExpression, type: IrType) =
	typeOperator(type, argument, IrTypeOperator.IMPLICIT_CAST, type)

fun IrElementScope.irReinterpretCast(argument: IrExpression, type: IrType) =
	typeOperator(type, argument, IrTypeOperator.REINTERPRET_CAST, type)


// Block

open class IrBlockBuilder(
	override val startOffset: Int,
	override val endOffset: Int,
	var resultType: IrType? = null,
	protected val origin: IrStatementOrigin? = null,
	protected val isTransparentScope: Boolean,
	override val scope: Scope
) : IrStatementsScope {
	val statements = mutableListOf<IrStatement>()
	
	override fun <T : IrStatement> T.unaryPlus(): T {
		statements += this
		return this
	}
	
	protected fun inferReturnType() =
		resultType ?: (statements.lastOrNull() as? IrExpression)?.type ?: irBuiltIns.unitType
	
	open fun build(): IrContainerExpression =
		if(isTransparentScope) IrCompositeImpl(startOffset, endOffset, inferReturnType(), origin, statements)
		else IrBlockImpl(startOffset, endOffset, inferReturnType(), origin, statements)
}

class IrReturnableBlockBuilder( // TODO: this inheritance relationship seems improper, extract common parent like `IrBlockBuilderBase`
	startOffset: Int, endOffset: Int,
	val returnTargetSymbol: IrReturnableBlockSymbol,
	resultType: IrType,
	origin: IrStatementOrigin? = null
) : IrBlockBuilder(startOffset, endOffset, resultType, origin, false, Scope(returnTargetSymbol)) {
	
	override fun build(): IrReturnableBlock =
		IrReturnableBlockImpl(startOffset, endOffset, resultType!!, returnTargetSymbol, origin, statements)
}

inline fun IrBuilderScope.irBlock(
	type: IrType? = null,
	origin: IrStatementOrigin? = null,
	body: IrBlockBuilder.() -> Unit
) = IrBlockBuilder(
	startOffset, endOffset,
	resultType = type,
	origin = origin,
	isTransparentScope = false,
	scope = scope
).apply(body).build()

inline fun IrBuilderScope.irComposite(
	type: IrType? = null,
	origin: IrStatementOrigin? = null,
	body: IrBlockBuilder.() -> Unit
) = IrBlockBuilder(
	startOffset, endOffset,
	resultType = type,
	origin = origin,
	isTransparentScope = true,
	scope = scope
).apply(body).build()

inline fun IrBuilderScope.irReturnableBlock(
	type: IrType,
	origin: IrStatementOrigin? = null,
	body: IrBlockBuilder.() -> Unit
) = IrReturnableBlockBuilder(
	startOffset, endOffset,
	@OptIn(ObsoleteDescriptorBasedAPI::class)
	IrReturnableBlockSymbolImpl(createAnonymousFunctionDescriptor(returnType = type.toKotlinType())),
	resultType = type, origin = origin
).apply(body).build()

// Flows

// return
val IrBuilderScope.returnTargetSymbol
	get() = scope.scopeOwnerSymbol as? IrReturnTargetSymbol ?: error("returnTargetSymbol not found")

fun IrBuilderScope.irReturn(value: IrExpression) = irReturn(value, returnTargetSymbol)
fun IrElementScope.irReturn(value: IrExpression, returnTargetSymbol: IrReturnTargetSymbol): IrReturn =
	IrReturnImpl(startOffset, endOffset, irBuiltIns.nothingType, returnTargetSymbol, value)

fun IrBuilderScope.irReturnUnit() = irReturn(irUnit())
fun IrElementScope.irReturnUnit(returnTargetSymbol: IrReturnTargetSymbol) =
	irReturn(irUnit(), returnTargetSymbol)

fun IrBuilderScope.irReturnTrue() = irReturn(irFalse())
fun IrBuilderScope.irReturnTrue(returnTargetSymbol: IrReturnTargetSymbol) =
	irReturn(irFalse(), returnTargetSymbol)

fun IrBuilderScope.irReturnFalse() = irReturn(irFalse())
fun IrBuilderScope.irReturnFalse(returnTargetSymbol: IrReturnTargetSymbol) =
	irReturn(irFalse(), returnTargetSymbol)


// Constants

fun IrElementScope.irUnit() =
	irGetObject(irBuiltIns.unitClass, irBuiltIns.unitType)

fun IrElementScope.irBoolean(value: Boolean): IrConst<Boolean> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.booleanType, IrConstKind.Boolean, value)

fun IrElementScope.irTrue() = irBoolean(true)
fun IrElementScope.irFalse() = irBoolean(false)

fun IrElementScope.irByte(value: Byte): IrConst<Byte> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.byteType, IrConstKind.Byte, value)

fun IrElementScope.irShort(value: Short): IrConst<Short> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.shortType, IrConstKind.Short, value)

fun IrElementScope.irInt(value: Int): IrConst<Int> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.intType, IrConstKind.Int, value)

fun IrElementScope.irLong(value: Long): IrConst<Long> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.longType, IrConstKind.Long, value)

fun IrElementScope.irFloat(value: Float): IrConst<Float> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.floatType, IrConstKind.Float, value)

fun IrElementScope.irDouble(value: Double): IrConst<Double> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.doubleType, IrConstKind.Double, value)

fun IrElementScope.irChar(value: Char): IrConst<Char> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.charType, IrConstKind.Char, value)

fun IrElementScope.irString(value: String): IrConst<String> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.stringType, IrConstKind.String, value)

fun IrElementScope.irNull(): IrConst<Nothing?> =
	IrConstImpl(startOffset, endOffset, irBuiltIns.nothingNType, IrConstKind.Null, null)

fun IrElementScope.irNull(type: IrType): IrConst<Nothing?> =
	IrConstImpl.constNull(startOffset, endOffset, type)


fun IrBuilderScope.irObjectExpression(
	type: IrType,
	name: Name = SpecialNames.NO_NAME_PROVIDED,
	expressionOrigin: IrStatementOrigin = IrStatementOrigin.OBJECT_LITERAL,
	superTypes: List<IrType> = emptyList(),
	isFun: Boolean = false,
	origin: IrDeclarationOrigin = sBackendDeclarationOrigin,
	source: SourceElement = SourceElement.NO_SOURCE,
	initClassSelf: (IrClassScope.(IrClass) -> Unit)? = null,
	init: IrClassScope.(IrClass) -> Unit
): IrExpression = irBlock(type, expressionOrigin) {
	// irBlock { irClass(kind = CLASS, ..); irConstructorCall(<- that local class)
	val aClass = +irClass(
		name = name,
		kind = ClassKind.CLASS,
		visibility = DescriptorVisibilities.LOCAL,
		modality = Modality.FINAL,
		superTypes = superTypes,
		isFun = isFun,
		origin = origin,
		source = source
	) {
		+irConstructorDefault()
		initClassSelf?.invoke(this, it)
	}
	
	// because inside init parameter of irClass, should only modify class itself, not adding
	// members into declarations like function
	aClass.scope.init(aClass)
	
	+irConstructorCall(aClass.primaryConstructor!!.symbol)
}


