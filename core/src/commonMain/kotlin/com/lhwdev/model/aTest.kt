package com.lhwdev.model


@Model
data class TestModel(val name: String) {
	var age: Int = 4
	var other: Float = 1.5f
		get() {
			if(field > 5) return field - 6
			run {
				if(field < -10) return field * 3
			}
			return field
		}
		set(value) {
			field = value - 4
		}
	
	@Exclude
	var hello = 3
		get() = field + 2
		set(value) { field = value - 2 }
}

@Model
data class TestModelGeneric<T>(var name: String, @ModelInfoParameter val info: ModelInfo<TestModelGeneric<T>>)
