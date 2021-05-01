package com.lhwdev.model.plugin.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration


class ModelComponentRegistrar : ComponentRegistrar {
	override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
		IrGenerationExtension.registerExtension(project, ModelIrGenerationExtension())
	}
}
