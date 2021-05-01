package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.Name


fun IrClassSymbol.referenceClass(name: Name): IrClassSymbol =
	owner.referenceClass(name).symbol

fun IrClassSymbol.referenceClass(name: String): IrClassSymbol =
	owner.referenceClass(name).symbol

fun IrClassSymbol.referencePrimaryConstructor(): IrConstructorSymbol =
	owner.referencePrimaryConstructor().symbol

fun IrClassSymbol.referenceConstructor(valueParametersCount: Int): IrConstructorSymbol =
	owner.referenceConstructor(valueParametersCount).symbol


fun IrClassSymbol.referenceFunction(name: Name): IrSimpleFunctionSymbol =
	owner.referenceFunction(name).symbol

fun IrClassSymbol.referenceFunction(name: Name, valueParametersCount: Int): IrSimpleFunctionSymbol =
	owner.referenceFunction(name, valueParametersCount).symbol

fun IrClassSymbol.referenceFunction(name: String): IrSimpleFunctionSymbol =
	owner.referenceFunction(name).symbol

fun IrClassSymbol.referenceFunction(name: String, valueParametersCount: Int): IrSimpleFunctionSymbol =
	owner.referenceFunction(name, valueParametersCount).symbol


fun IrClassSymbol.referenceProperty(name: Name): IrPropertySymbol =
	owner.referenceProperty(name).symbol

fun IrClassSymbol.referenceProperty(name: String): IrPropertySymbol =
	owner.referenceProperty(name).symbol
