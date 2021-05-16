package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.parentAsClass


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
	IrConstructorCallImpl.fromSymbolOwner(startOffset, endOffset, type, callee, origin)

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
