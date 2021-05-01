package com.lhwdev.model.test

import com.lhwdev.model.*
import com.lhwdev.model.collections.ModelList
import com.lhwdev.model.collections.modelListOf


@Model
class MyState(var parameter: Int) {
	@Exclude
	private var cache = 123
	
	var name: String = "ho!"
	var age: Float = 100.0f
	
	val children: ModelList<MyState> = modelListOf()
}


// @Model
class MyState2(parameter: Int, val manager: ModelManager = currentModelManager, from: ModelCompositeReader? = null) :
	ModelValue {
	companion object {
		private object ModelInfoImpl : StructuredModelInfo<MyState2> {
			override val modelDescriptor: StructuredModelDescriptor = StructuredModelDescriptor {
				mutableProperty("parameter", PrimitiveModelInfo.Int)
				mutableProperty("name", PrimitiveModelInfo.String)
				mutableProperty("age", PrimitiveModelInfo.Float)
				modelProperty("children", ModelInfoImpl)
			}
			
			override fun create(from: ModelReader): MyState2 = from.readComposite(this) {
				val state = MyState2(readInt(modelDescriptor, 0))
				state.readModelAll(this, 1 /* name~ */)
				state
			}
		}
		
		fun modelInfo(): StructuredModelInfo<MyState2> = ModelInfoImpl
	}
	
	override val modelInfo: StructuredModelInfo<MyState2> get() = modelInfo()
	
	var parameter: Int = parameter
		get() = manager.onReadProperty(this, 0, field)
		set(value) = manager.onWriteProperty(this, 0) { field = value }
	
	@Exclude
	private var cache = 123
	
	var name: String = from?.readString(modelInfo.modelDescriptor, 1) ?: "ho!"
		get() = manager.onReadProperty(this, 1, field)
		set(value) = manager.onWriteProperty(this, 1) { field = value }
	
	var age: Float = from?.readFloat(modelInfo.modelDescriptor, 1) ?: 100.0f
		get() = manager.onReadProperty(this, 2, field)
		set(value) = manager.onWriteProperty(this, 2) { field = value }
	
	val children: ModelList<MyState2> =
		@Suppress("UNCHECKED_CAST")
		from?.readModel(modelInfo.modelDescriptor, 3, stubModelListInfo as ModelInfo<ModelList<MyState2>>)
			?: modelListOf()
		get() = manager.onReadProperty(this, 3, field)
	
	
	override fun readModel(index: Int, to: ModelWriter): Unit = when(index) {
		0 -> to.writeInt(parameter)
		1 -> to.writeString(name)
		2 -> to.writeFloat(age)
		3 -> to.writeModel(children, /*ModelList*/ stubModelListInfo)
		else -> throw IllegalArgumentException()
	}
	
	override fun writeModel(index: Int, from: ModelReader): Unit = when(index) {
		0 -> parameter = from.readInt()
		1 -> name = from.readString()
		2 -> age = from.readFloat()
		else -> throw IllegalArgumentException()
	}
}

private val stubModelListInfo = object : ModelInfo<ModelList<*>> {
	override val modelDescriptor: ModelDescriptor get() = TODO()
	override fun create(from: ModelReader): ModelList<*> = TODO()
}
