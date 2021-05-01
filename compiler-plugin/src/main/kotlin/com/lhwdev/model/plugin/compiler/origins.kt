package com.lhwdev.model.plugin.compiler

import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin


class IrCustomDeclarationOrigin(val name: String) : IrDeclarationOrigin {
	override val isSynthetic: Boolean = true
	
	override fun toString(): String = name
}


object DeclarationOrigins {
	val modelInfo = IrCustomDeclarationOrigin("[Model] modelInfo")
}
