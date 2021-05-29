package com.lhwdev.model


private var sModelManager: ModelManagerController? = null
val currentModelManager: ModelManager get() = sModelManager!!.manager

fun attachModelManager(manager: ModelManagerController) {
	if(sModelManager != null) error("ModelManager already attached")
	sModelManager = manager
	manager.initialize(attachHandle = { sModelManager = it })
}


abstract class ModelManagerController {
	abstract fun initialize(attachHandle: (ModelManagerController) -> Unit)
	abstract val manager: ModelManager
}

abstract class ModelManager {
	abstract fun <T> onReadProperty(model: ModelValue, index: Int, value: T): T
	abstract fun <T> onWriteProperty(model: ModelValue, index: Int, value: T): T
}
