package com.igormich88

import com.igormich.proxy.*

class ProxyHandlerForVector() : ProxyHandler {
    override fun handle(method: Method, args: Array<Any>): Any {
        println("ProxyHandlerForVector: ${method.name}(${args.contentToString()})")
        return when (method.result) {
            ArrayList::class -> ArrayList<Any>()
            Double::class -> 0.0
            Unit::class -> Unit
            else -> throw IllegalStateException()
        }
    }
}

@Suppress("UNCHECKED_CAST")
class ProxyForVector1: com.igormich88.Vector1 {
    override val x:kotlin.Double
        get() = ProxyForVector1.proxy.handle(getX,emptyArray()) as kotlin.Double
    override var y:kotlin.Double
        set(value) = ProxyForVector1.proxy.handle(Method("setY",arrayOf(kotlin.Double::class),Unit::class),arrayOf(value))  as Unit
        get() = ProxyForVector1.proxy.handle(Method("getY",emptyArray(),kotlin.Double::class),emptyArray()) as kotlin.Double
    override var other:kotlin.collections.ArrayList<Int>
        set(value) = ProxyForVector1.proxy.handle(Method("setOther",arrayOf(kotlin.collections.ArrayList::class),Unit::class),arrayOf(value))  as Unit
        get() = ProxyForVector1.proxy.handle(Method("getOther",emptyArray(),kotlin.collections.ArrayList::class),emptyArray()) as kotlin.collections.ArrayList<Int>
    override fun length():kotlin.Double {
        return ProxyForVector1.proxy.handle(Method("length",arrayOf(),kotlin.Double::class),emptyArray()) as kotlin.Double
    }
    override fun equals(other : kotlin.Any?):kotlin.Boolean {
        return ProxyForVector1.proxy.handle(Method("equals",arrayOf(kotlin.Any::class),kotlin.Boolean::class),emptyArray()) as kotlin.Boolean
    }
    override fun hashCode():kotlin.Int {
        return ProxyForVector1.proxy.handle(Method("hashCode",arrayOf(),kotlin.Int::class),emptyArray()) as kotlin.Int
    }
    override fun toString():kotlin.String {
        return ProxyForVector1.proxy.handle(Method("toString",arrayOf(),kotlin.String::class),emptyArray()) as kotlin.String
    }
    companion object {
        val proxy = com.igormich88.ProxyHandlerForVector()
        val getX = Method("getX",emptyArray(),kotlin.Double::class)
    }
}

@WithProxy(ProxyHandlerForVector::class)
interface Vector1 {
    val x: Double
    var y: Double
    var other: ArrayList<Int>
    fun length(): Double
}

fun main() {
    println("4531344")
    val proxy = ProxyProvider.createProxyFor<Vector>()
    proxy.length()
    proxy.other
    proxy.y = 1.0
}
interface Vector {
    val x: Double
    var y: Double
    var other: ArrayList<Int>
    fun length(): Double
}

@ProxyFor(Vector::class)
fun vectorProxy(method: Method, args: Array<Any>): Any {
    println("vectorProxy: ${method.name}(${args.contentToString()})")
    return when (method.result) {
        ArrayList::class -> ArrayList<Any>()
        Double::class -> 0.0
        Unit::class -> Unit
        else -> throw IllegalStateException()
    }
}