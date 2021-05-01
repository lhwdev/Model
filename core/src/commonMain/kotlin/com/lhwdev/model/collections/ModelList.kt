package com.lhwdev.model.collections

import com.lhwdev.model.*


fun <E> mutableModelListOf(elementInfo: ModelInfo<E>): MutableModelList<E> =
	MutableModelListImpl(elementInfo, mutableListOf())

inline fun <E> ModelList(elementInfo: ModelInfo<E>, size: Int, block: (index: Int) -> E): ModelList<E> =
	ModelListImpl(elementInfo, List(size, block))

inline fun <E> MutableModelList(elementInfo: ModelInfo<E>, size: Int, block: (index: Int) -> E): MutableModelList<E> =
	MutableModelListImpl(elementInfo, MutableList(size, block))

interface ModelList<out E> : List<E>, ModelValue {
	val elementInfo: ModelInfo<E>
	override val modelInfo: ListModelInfo<ModelList<E>, E>
}

interface MutableModelList<E> : MutableList<E>, ModelList<E>


class ModelListImpl<out E>(override val elementInfo: ModelInfo<E>, private val list: List<E>) : ModelList<E>,
	List<E> by list {
	override fun readModel(index: Int, to: ModelWriter) {
		to.writeModel(list[index], elementInfo)
	}
	
	override fun writeModel(index: Int, from: ModelReader): Nothing {
		error("Read-only ModelList")
	}
	
	override val modelInfo: ReadOnlyListModelInfo<E> = ReadOnlyListModelInfo(elementInfo)
}

class MutableModelListImpl<E>(override val elementInfo: ModelInfo<E>, private val list: MutableList<E>) :
	MutableModelList<E>, MutableList<E> by list {
	override fun readModel(index: Int, to: ModelWriter) {
		to.writeModel(list[index], elementInfo)
	}
	
	override fun writeModel(index: Int, from: ModelReader) {
		list[index] = from.readModel(elementInfo)
	}
	
	override val modelInfo: MutableListModelInfo<E> = MutableListModelInfo(elementInfo)
}
