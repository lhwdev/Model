# 🚧 Model (toy project)

**Structuralizes any Kotlin classes**, which enables:
- Serialization
- Realtime synchronization with database
- Observing mutation
- Zero-cost reflection(only properties)
- Simple dumping
- ...and unlimited possibilities


## Examples
**All codes below are just PoC, some of them not implemented yet.**


### Serialization & Synchronization
``` kotlin
@Model
data class User(var name: String, val id: Int, val children: MutableModelList<User>)

val serializer = JsonSerializer()
val user = serializer.hydrate(File("data.json")) // can be anything like file, socket, directory, ...
println(user.id) // lazily read(depends on implementation)

user.name = "Hello, world!" // synchornized with original file
user.children += User(name = "Jack", id = 123, children = mutableModelListOf()) // also synchronized
// note that MutableModelList is also a model class
```


### Observing mutation
``` kotlin
@Model
class MyModel {
	var data = 123
}

val myModel = MyModel()
observeModels(onChange = { model -> println(model) }) {
	myModel.data = 7
}
```


### Zero-cost reflection
``` kotlin
@Model
data class MyModel(val value: Long, var value2: String)

val model = MyModel(123L, "Hello!")
model.writeModel(model.modelInfo.children["value"], 4L)
```


### Dumping
``` kotlin
@Model
data class Node(val data: String, @Dump(primaryStructure = true) val children: ModelList<Node>)

val node = getNode()
println(dumpModelStructure(node))
```

output:
``` text
Node data="hello, world!"
 |- Node data="Wow!"
 |- Node data="simple dumping!"
     |- Node data="such wow. very amaze."
 |- Node data="ho"
```
