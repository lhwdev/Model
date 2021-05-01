package com.lhwdev.model


interface ModelWriter {
	fun writeByte(value: Byte)
	fun writeShort(value: Short)
	fun writeInt(value: Int)
	fun writeLong(value: Long)
	fun writeFloat(value: Float)
	fun writeDouble(value: Double)
	fun writeBoolean(value: Boolean)
	fun writeChar(value: Char)
	
	fun writeString(value: String)
	fun <T> writeModel(model: T, info: ModelInfo<T>)
}
