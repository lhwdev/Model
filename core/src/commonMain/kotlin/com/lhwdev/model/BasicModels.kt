package com.lhwdev.model

import com.lhwdev.model.collections.ModelList
import com.lhwdev.model.collections.MutableModelList
import com.lhwdev.model.collections.mutableModelListOf
import kotlin.reflect.KClass


internal expect fun <T, C : Any> List<T>.toNativeArrayImpl(componentClass: KClass<C>): Array<T>

internal expect fun <T, C : Any> unsafeNativeArrayOf(size: Int, componentClass: KClass<C>): Array<T>


class ArrayModelInfo<E>(private val elementClass: KClass<*>, val elementInfo: ModelInfo<E>) : ModelInfo<Array<E>> {
	override val modelDescriptor: CollectionModelDescriptor<E> = object : ListModelDescriptor<E> {
		override val elementInfo: ModelInfo<E> get() = this@ArrayModelInfo.elementInfo
		@Suppress("UNCHECKED_CAST")
		override val elementClass: KClass<Any> get() = this@ArrayModelInfo.elementClass as KClass<Any>
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun create(from: ModelReader): Array<E> = from.readComposite(this) read@ {
		val size = readCollectionSize(modelDescriptor)
		if(size != ModelCompositeReader.INVALID) {
			val array: Array<E> = unsafeNativeArrayOf(size, elementClass)
			for(index in 0 until size) array[index] = readModel(modelDescriptor, index, elementInfo)
			return@read array
		}
		
		val list = mutableListOf<E>()
		var index = 0
		while(true) {
			if(!hasNext(index)) break
			readModel(modelDescriptor, index, elementInfo)
			index++
		}
		list.toNativeArrayImpl(elementClass)
	}
}


interface CollectionModelInfo<out T, out E> : StructuredModelInfo<T> {
	override val modelDescriptor: CollectionModelDescriptor<E>
}

interface ListModelInfo<out T, out E> : CollectionModelInfo<T, E> {
	override val modelDescriptor: ListModelDescriptor<E>
}


abstract class ListLikeModelInfo<out T, out E>(val elementInfo: ModelInfo<E>) : ListModelInfo<T, E> {
	override val modelDescriptor: ListModelDescriptor<E> = object : ListModelDescriptor<E> {
		override val elementInfo: ModelInfo<E> get() = this@ListLikeModelInfo.elementInfo
		override val elementClass: KClass<Any> get() = elementInfo.modelDescriptor.kClass
	}
}

class ReadOnlyListModelInfo<out E>(elementInfo: ModelInfo<E>) : ListLikeModelInfo<ModelList<E>, E>(elementInfo) {
	override fun create(from: ModelReader): ModelList<E> = from.readComposite(this) read@ {
		val size = readCollectionSize(modelDescriptor)
		if(size != ModelCompositeReader.INVALID) {
			return@read ModelList(elementInfo, size) { index -> readModel(modelDescriptor, index, elementInfo) }
		}
		
		val list = mutableModelListOf(elementInfo)
		var index = 0
		while(true) {
			if(!hasNext(index)) break
			readModel(modelDescriptor, index, elementInfo)
			index++
		}
		
		list
	}
}


class MutableListModelInfo<E>(elementInfo: ModelInfo<E>) : ListLikeModelInfo<MutableModelList<E>, E>(elementInfo) {
	override fun create(from: ModelReader): MutableModelList<E> = from.readComposite(this) read@ {
		val size = readCollectionSize(modelDescriptor)
		if(size != ModelCompositeReader.INVALID) {
			return@read MutableModelList(elementInfo, size) { index -> readModel(modelDescriptor, index, elementInfo) }
		}
		
		val list = mutableModelListOf(elementInfo)
		var index = 0
		while(true) {
			if(!hasNext(index)) break
			readModel(modelDescriptor, index, elementInfo)
			index++
		}
		
		list
	}
}

