package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.kotlinPackageFqn


private val kotlinPackage = pluginContext.referencePackage(kotlinPackageFqn)


fun IrElementScope.irError(message: String): IrCall = irCall(
	kotlinPackage.referenceFirstFunction("error", 1),
	valueArguments = listOf(irString(message))
)

fun IrElementScope.irError(vararg elements: IrExpression): IrCall = irCall(
	kotlinPackage.referenceFirstFunction("error",1),
	valueArguments = listOf(irStringTemplate(*elements))
)

fun IrElementScope.irTODO(message: String? = null): IrCall = irCall(
	kotlinPackage.referenceFirstFunction("TODO", if(message == null) 0 else 1),
	valueArguments = if(message == null) emptyList() else listOf(irString(message))
)
