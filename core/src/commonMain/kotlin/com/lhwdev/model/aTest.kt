package com.lhwdev.model


@Model
data class TestModel(val name: String)

@Model
data class TestModelGeneric<T>(var name: String, @ModelInfoParameter val info: ModelInfo<TestModelGeneric<T>>)
