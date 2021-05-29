package com.lhwdev.model

import com.lhwdev.model.collections.MutableModelList


fun main() {

}


class ExampleModelManagerController : ModelManagerController() {
	override fun initialize(attachHandle: (ModelManagerController) -> Unit) {
	}
	
	override val manager: ModelManager = object : ModelManager() {
		override fun <T> onReadProperty(model: ModelValue, index: Int, value: T): T {
			return value
		}
		
		override fun <T> onWriteProperty(model: ModelValue, index: Int, value: T): T {
			return value
		}
	}
}


@Model
data class User(var name: String, var age: Int, val children: MutableModelList<User>)
