# Model Compiler Plugin
- 🚧 work in progress


## PoC

from:
```kotlin
@Model
class MyState(var parameter: Int) {
	@Exclude
	private var cache = 123
	
	var name: String = "ho!"
	var age: Float = 100.0f
	
	val children: ModelList<MyState> = modelListOf()
}
```

to:
```kotlin
@Model
class MyState(parameter: Int, val manager: ModelManager? = null, from: ModelCompositeReader? = null) : ModelValue {
	companion object : ModelInfo<MyState> {
		override val modelDescriptor: ModelDescriptor = StructuredModelDescriptor {
			mutableProperty("parameter", PrimitiveModelDescriptor.Int)
			mutableProperty("name", PrimitiveModelDescriptor.String)
			mutableProperty("age", PrimitiveModelDescriptor.Float)
			modelProperty("children", MyState)
		}

		override fun create(from: ModelReader) = MyState(from.readInt(modelDescriptor, 0)).also {
			it.readAllModel(from)
		}
	}

	override val modelInfo: ModelInfo<MyState> get() = MyState

	var parameter: Int = parameter
		get() = manager.onReadProperty(this, 0, field)
		set(value) = manager.onWriteProperty(this, 0) { field = value }

	@Exclude
	private var cache = 123

	var name: String = if(from == null) "ho!" else from.readString(modelDescriptor, 1)
		get() = manager.onReadProperty(this, 1, field)
		set(value) = manager.onWriteProperty(this, 1) { field = value }
	
	var age: Float = if(from == null) 100.0f else from.readFloat(modelDescriptor, 1)
		get() = manager.onReadProperty(this, 2, field)
		set(value) = manager.onWriteProperty(this, 2) { field = value }

	val children: ModelList<MyState> = if(from == null) modelListOf() else from.readModel(modelDescriptor, 3)
		get() = manager.onReadProperty(this, 3)


	override fun readModel(index: Int, to: Encoder): Unit = when(index) {
		0 -> to.writeInt(parameter)
		1 -> to.writeString(name)
		2 -> to.writeFloat(age)
		3 -> to.writeModel(children, MyState)
		else -> nope()
	}

	override fun writeModel(index: Int, from: Decoder): Unit = when(index) {
		0 -> parameter = from.readInt()
		1 -> name = from.readString()
		2 -> age = from.readFloat()
		3 -> readOnly()
		else -> nope()
	}
}
```
