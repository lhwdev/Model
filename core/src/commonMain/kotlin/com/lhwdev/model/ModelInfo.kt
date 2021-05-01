package com.lhwdev.model


interface ModelInfo<out T> {
	val modelDescriptor: ModelDescriptor
	
	fun create(from: ModelReader): T
}


interface StructuredModelInfo<out T> : ModelInfo<T> {
	override val modelDescriptor: StructuredModelDescriptor
}

fun StructuredModelInfo<*>.modelInfoAt(index: Int): ModelInfo<*> = modelDescriptor.getElementAt(index)



@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ModelInfoParameter
