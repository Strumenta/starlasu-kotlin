package com.strumenta.starlasu.traversing

import java.util.ArrayDeque
import java.util.Deque

/**
 * Some Kotlinization of Deques used as a stack.
 */
typealias Stack<T> = Deque<T>

fun <T> mutableStackOf(): Stack<T> = ArrayDeque()

fun <T> Stack<T>.pushAll(elements: Collection<T>) {
    elements.reversed().forEach(this::push)
}

fun <T> mutableStackOf(vararg elements: T): Stack<T> {
    val stack = mutableStackOf<T>()
    stack.pushAll(elements.asList())
    return stack
}
