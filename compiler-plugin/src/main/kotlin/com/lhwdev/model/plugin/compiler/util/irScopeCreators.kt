@file:Suppress("NOTHING_TO_INLINE")

package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrSymbol


class IrSimpleElementScope(override val startOffset: Int, override val endOffset: Int) :
	IrElementScope

class IrSimpleBuilderScope(override val startOffset: Int, override val endOffset: Int, override val scope: Scope) :
	IrBuilderScope


val irElementScope: IrElementScope = irElementScope()

fun irElementScope(startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET): IrElementScope =
	IrSimpleElementScope(startOffset, endOffset)

inline fun <R> irElementScope(
	startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET,
	block: IrElementScope.() -> R
): R = irElementScope(startOffset, endOffset).run(block)

inline fun irBuilderScope(
	startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET,
	scope: Scope
): IrBuilderScope = IrSimpleBuilderScope(startOffset, endOffset, scope)

inline fun <R> irBuilderScope(
	startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET,
	scope: Scope,
	block: IrBuilderScope.() -> R
): R = irBuilderScope(startOffset, endOffset, scope).run(block)

fun irBuilderScope(element: IrSymbolOwner): IrBuilderScope =
	IrSimpleBuilderScope(element.startOffset, element.endOffset, Scope(element.symbol))

fun irBuilderScope(symbol: IrSymbol): IrBuilderScope =
	IrSimpleBuilderScope(UNDEFINED_OFFSET, UNDEFINED_OFFSET, Scope(symbol))

fun <T> irDeclarationsScope(element: T): IrDeclarationsScope
	where T : IrSymbolOwner, T : IrDeclarationContainer =
	IrDeclarationsScopeImpl(Scope(element.symbol), element)


val IrElement.scope: IrElementScope get() = IrSimpleElementScope(startOffset, endOffset)
val IrSymbolOwner.scope: IrBuilderScope get() = IrSimpleBuilderScope(startOffset, endOffset, Scope(symbol))
val <T> T.scope: IrDeclarationsScope where T : IrDeclarationContainer, T : IrSymbolOwner
	get() = IrDeclarationsScopeImpl(
		Scope(symbol), this
	)


class IrDeclarationsScopeImpl(override val scope: Scope, val into: IrDeclarationContainer) :
	IrDeclarationsScope {
	override val declarations: MutableList<IrDeclaration> get() = into.declarations
}

