package com.lhwdev.model


sealed class PrimitiveModelDescriptor : ModelDescriptor {
	object Byte : PrimitiveModelDescriptor()
	object Short : PrimitiveModelDescriptor()
	object Int : PrimitiveModelDescriptor()
	object Long : PrimitiveModelDescriptor()
	object Float : PrimitiveModelDescriptor()
	object Double : PrimitiveModelDescriptor()
	object Boolean : PrimitiveModelDescriptor()
	object Char : PrimitiveModelDescriptor()
	object String : PrimitiveModelDescriptor()
}


sealed class PrimitiveModelInfo<T> : ModelInfo<T> {
	object Byte : PrimitiveModelInfo<kotlin.Byte>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Byte
		override fun create(from: ModelReader): kotlin.Byte = from.readByte()
	}
	
	object Short : PrimitiveModelInfo<kotlin.Short>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Short
		override fun create(from: ModelReader): kotlin.Short = from.readShort()
	}
	
	object Int : PrimitiveModelInfo<kotlin.Int>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Int
		override fun create(from: ModelReader): kotlin.Int = from.readInt()
	}
	
	object Long : PrimitiveModelInfo<kotlin.Long>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Long
		override fun create(from: ModelReader): kotlin.Long = from.readLong()
	}
	
	object Float : PrimitiveModelInfo<kotlin.Float>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Float
		override fun create(from: ModelReader): kotlin.Float = from.readFloat()
	}
	
	object Double : PrimitiveModelInfo<kotlin.Double>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Double
		override fun create(from: ModelReader): kotlin.Double = from.readDouble()
	}
	
	object Boolean : PrimitiveModelInfo<kotlin.Boolean>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Boolean
		override fun create(from: ModelReader): kotlin.Boolean = from.readBoolean()
	}
	
	object Char : PrimitiveModelInfo<kotlin.Char>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.Char
		override fun create(from: ModelReader): kotlin.Char = from.readChar()
	}
	
	object String : PrimitiveModelInfo<kotlin.String>() {
		override val modelDescriptor: ModelDescriptor = PrimitiveModelDescriptor.String
		override fun create(from: ModelReader): kotlin.String = from.readString()
	}
	
}

