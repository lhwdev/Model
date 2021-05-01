package com.lhwdev.model.plugin.compiler

import com.lhwdev.model.plugin.compiler.util.provideContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump


class ModelIrGenerationExtension : IrGenerationExtension {
	override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
		pluginContext.provideContext {
			ModelAddMembersTransformer().lower(moduleFragment)
			println(moduleFragment.dumpSrcColored())
			// println(moduleFragment.dump())
		}
	}
}
