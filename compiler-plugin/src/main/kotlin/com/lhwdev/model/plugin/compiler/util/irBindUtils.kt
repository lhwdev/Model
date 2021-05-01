package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContextImpl
import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinker
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.lazy.IrLazySymbolTable
import org.jetbrains.kotlin.ir.linkage.IrDeserializer
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.SymbolTable
import java.util.WeakHashMap


interface IrPluginContextWithLinker : IrPluginContext {
	val linker: KotlinIrLinker
}


private val sLinkerCache = WeakHashMap<IrPluginContext, KotlinIrLinker>()

val IrPluginContext.linker: KotlinIrLinker
	get() = when(this) {
		is IrPluginContextImpl -> linker as KotlinIrLinker
		is IrPluginContextWithLinker -> linker
		else -> sLinkerCache[this] ?: error("wtf")
	}

fun provideLinker(pluginContext: IrPluginContext, linker: KotlinIrLinker) {
	sLinkerCache[pluginContext] = linker
}

fun IrPluginContext.bindSymbol(symbol: IrSymbol) {
	val linker = linker
	linker.getDeclaration(symbol)
	linker.postProcess()
}


@ObsoleteDescriptorBasedAPI
val IrPluginContext.realSymbolTable: SymbolTable
	get() = when(val st = symbolTable) {
		is SymbolTable -> st
		is IrLazySymbolTable -> TODO()
		else -> error("cannot get SymbolTable")
	}
