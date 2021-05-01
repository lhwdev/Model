package com.lhwdev.model


interface ModelValue {
	val modelInfo: StructuredModelInfo<*>
	
	fun readModel(index: Int, to: ModelWriter)
	
	fun writeModel(index: Int, from: ModelReader)
}
