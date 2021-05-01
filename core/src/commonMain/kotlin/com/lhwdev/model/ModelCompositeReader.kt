package com.lhwdev.model


interface ModelCompositeReader {
	companion object {
		const val INVALID = -1
	}
	
	fun readerForCurrent(model: ModelDescriptor, index: Int): ModelReader
	fun hasNext(index: Int): Boolean
	
	fun readCollectionSize(model: ModelDescriptor): Int
	fun readInt(model: ModelDescriptor, index: Int): Int
	fun readFloat(model: ModelDescriptor, index: Int): Float
	fun readString(model: ModelDescriptor, index: Int): String
	fun <T> readModel(model: ModelDescriptor, index: Int, info: ModelInfo<T>): T
}
