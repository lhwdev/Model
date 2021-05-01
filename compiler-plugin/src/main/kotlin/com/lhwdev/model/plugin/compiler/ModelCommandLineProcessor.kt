package com.lhwdev.model.plugin.compiler

import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor


class ModelCommandLineProcessor : CommandLineProcessor {
	override val pluginId get() = "com.lhwdev.model.compiler-plugin"
	
	override val pluginOptions = emptyList<CliOption>()
}
