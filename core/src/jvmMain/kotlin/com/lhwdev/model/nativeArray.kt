package com.lhwdev.model

import kotlin.reflect.KClass


@Suppress("UNCHECKED_CAST")
actual fun <T, C : Any> List<T>.toNativeArrayImpl(componentClass: KClass<C>): Array<T> {
	val array = java.lang.reflect.Array.newInstance(componentClass.java, size) as Array<T>
	for(i in 0 until size) array[i] = this[i]
	return array
}

@Suppress("UNCHECKED_CAST")
internal actual fun <T, C : Any> unsafeNativeArrayOf(size: Int, componentClass: KClass<C>): Array<T> =
	java.lang.reflect.Array.newInstance(componentClass.java, size) as Array<T>
