package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrGenerator
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.IrGeneratorWithScope
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns


@DslMarker
annotation class IrScopeMarker


@IrScopeMarker
interface IrElementScope : IrGenerator, IrGeneratorContext {
	override val context: IrPluginContext get() = pluginContext
	override val irBuiltIns: IrBuiltIns get() = context.irBuiltIns
	
	val startOffset: Int
	val endOffset: Int
}

interface IrBuilderScope : IrElementScope, IrGeneratorWithScope {
	override val context: IrPluginContext get() = pluginContext
	
	override val scope: Scope
	
	override val irFactory: IrFactory
		get() = (scope.scopeOwnerSymbol.owner as? IrDeclaration)?.factory ?: context.irFactory
	
	val irElement: IrSymbolOwner get() = scope.scopeOwnerSymbol.owner
	
	override val startOffset: Int get() = irElement.startOffset
	override val endOffset: Int get() = irElement.endOffset
}

interface IrStatementsScope : IrBuilderScope {
	operator fun <T : IrStatement> T.unaryPlus(): T
}

interface IrDeclarationsScope : IrBuilderScope {
	val declarations: MutableList<IrDeclaration>
	
	operator fun <T : IrDeclaration> T.unaryPlus(): T {
		declarations += this
		return this
	}
}
