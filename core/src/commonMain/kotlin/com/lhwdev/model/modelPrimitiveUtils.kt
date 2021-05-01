package com.lhwdev.model


fun ModelValue.readModelAll(from: ModelReader, fromIndex: Int, toIndex: Int = modelInfo.modelDescriptor.elementCount) {
	for(i in fromIndex until toIndex) {
		writeModel(i, from)
	}
}

fun ModelValue.readModelAll(
	from: ModelCompositeReader,
	fromIndex: Int,
	toIndex: Int = modelInfo.modelDescriptor.elementCount
) {
	val descriptor = modelInfo.modelDescriptor
	for(i in fromIndex until toIndex) {
		writeModel(i, from.readerForCurrent(descriptor, i))
	}
}
