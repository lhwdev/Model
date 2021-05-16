package com.lhwdev.model


val currentModelManager: ModelManager get() = TODO()


abstract class ModelManager {
	abstract fun <T> onReadProperty(model: ModelValue, index: Int, value: T): T
	abstract fun <T> onWriteProperty(model: ModelValue, index: Int, value: T): T
}
