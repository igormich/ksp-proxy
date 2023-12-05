package com.igormich.proxy

import kotlin.reflect.KClass

class Method(val name: String, val args: Array<KClass<*>>, val result: KClass<*>)

interface ProxyHandler {
    fun handle(method: Method, args: Array<Any>): Any
}


@Target(AnnotationTarget.FUNCTION)
annotation class ProxyFor(val clazz: KClass<*>)

@Target(AnnotationTarget.CLASS)
annotation class WithProxy(val clazz: KClass<out ProxyHandler>)