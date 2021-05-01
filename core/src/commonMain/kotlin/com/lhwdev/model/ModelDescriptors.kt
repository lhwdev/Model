package com.lhwdev.model

import kotlin.reflect.KClass


interface ModelDescriptor {
	val kClass: KClass<Any> get() = error("Not implemented")
}

interface StructuredModelDescriptor : ModelDescriptor {
	val elementCount: Int
	fun getElementName(index: Int): String
	fun getElementAt(index: Int): ModelInfo<*>
}


data class ModelProperty(
	val name: String,
	val info: ModelInfo<*>,
	val isMutable: Boolean = false,
	val isModel: Boolean = false
)

interface CollectionModelDescriptor<out E> : StructuredModelDescriptor {
	val elementInfo: ModelInfo<E>
	val elementClass: KClass<Any> // type parameter cannot be nullable
}

interface ListModelDescriptor<out E> : CollectionModelDescriptor<E> {
	override val elementCount: Int get() = 1
	
	override fun getElementName(index: Int): String = when(index) {
		0 -> "element"
		else -> error("index out of bound [0, 0]: $index")
	}
	
	override fun getElementAt(index: Int): ModelInfo<*> = elementInfo
}

class StructuredModelDescriptorImpl(private val properties: List<ModelProperty>) : StructuredModelDescriptor {
	override val elementCount: Int get() = properties.size
	override fun getElementName(index: Int): String = properties[index].name
	override fun getElementAt(index: Int): ModelInfo<*> = properties[index].info
}

inline fun StructuredModelDescriptor(block: StructuredModelDescriptorBuilder.() -> Unit): StructuredModelDescriptor =
	StructuredModelDescriptorBuilder().apply(block).build()

class StructuredModelDescriptorBuilder {
	private val properties = mutableListOf<ModelProperty>()
	
	fun property(name: String, info: ModelInfo<*>) {
		properties += ModelProperty(name, info)
	}
	
	fun mutableProperty(name: String, info: ModelInfo<*>) {
		properties += ModelProperty(name, info, isMutable = true)
	}
	
	fun modelProperty(name: String, info: ModelInfo<*>) {
		properties += ModelProperty(name, info, isModel = true)
	}
	
	fun mutableModelProperty(name: String, info: ModelInfo<*>) {
		properties += ModelProperty(name, info, isMutable = true, isModel = true)
	}
	
	
	fun build(): StructuredModelDescriptor = StructuredModelDescriptorImpl(properties)
}
