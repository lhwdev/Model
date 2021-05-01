package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.ir.expressions.IrExpression


@Suppress("NOTHING_TO_INLINE")
inline class IrExpressionsBuilder(val expressions: MutableList<IrExpression>) {
	inline operator fun IrExpression.unaryPlus() {
		expressions += this
	}
}

inline fun buildExpressions(block: IrExpressionsBuilder.() -> Unit): List<IrExpression> =
	IrExpressionsBuilder(mutableListOf()).apply(block).expressions




