package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name


fun FqName.child(name: String): FqName = child(Name.guessByFirstCharacter(name))


fun IrElement.debugName(): String = when(this) {
	is IrDeclarationWithName -> {
		val type = when(this) {
			is IrClass -> "class"
			is IrProperty -> if(isVar) "var" else "val"
			is IrVariable -> if(isVar) "var" else "val"
			is IrLocalDelegatedProperty -> if(isVar) "var" else "val"
			is IrFunction -> "fun"
			is IrTypeAlias -> "typealias"
			is IrTypeParameter -> "typeParameter"
			is IrField -> "field"
			else -> null
		}
		if(type == null) "$name" else "$type $name"
	}
	else -> "$this"
}
