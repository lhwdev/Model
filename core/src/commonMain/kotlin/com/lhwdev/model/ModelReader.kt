package com.lhwdev.model


interface ModelReader {
	fun readByte(): Byte
	fun readShort(): Short
	fun readInt(): Int
	fun readLong(): Long
	fun readFloat(): Float
	fun readDouble(): Double
	fun readBoolean(): Boolean
	fun readChar(): Char
	fun readString(): String
	fun <T> readModel(info: ModelInfo<T>): T
	
	fun beginCompositeReader(info: ModelInfo<*>): ModelCompositeReader
	fun endCompositeReader(reader: ModelCompositeReader)
}


inline fun <R> ModelReader.readComposite(info: ModelInfo<*>, block: ModelCompositeReader.() -> R): R {
	val reader = beginCompositeReader(info)
	return try {
		reader.block()
	} finally {
		endCompositeReader(reader)
	}
}
