package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext


fun IrPluginContext.provideContext(block: () -> Unit) {
	val last = sPluginContext
	sPluginContext = this
	try {
		block()
	} finally {
		sPluginContext = last
	}
}

private var sPluginContext: IrPluginContext? = null

val pluginContext: IrPluginContext get() = sPluginContext!!

inline val irBuiltIns get() = pluginContext.irBuiltIns
