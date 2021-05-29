package com.lhwdev.model

import kotlin.reflect.KClass


// TODO: Not supported yet
@Target(AnnotationTarget.PROPERTY)
annotation class WithModel(val modelInfo: KClass<*>)
