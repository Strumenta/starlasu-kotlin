package com.strumenta.starlasu.utils

class Memoize1<in T, out R>(
    val f: (T) -> R,
) : (T) -> R {
    private val values = mutableMapOf<T, R>()

    override fun invoke(x: T): R = values.getOrPut(x) { f(x) }
}

fun <T, R> ((T) -> R).memoize(): (T) -> R = Memoize1(this)

class MemoizeExtension1<in S, in T, out R>(
    val f: (S, T) -> R,
) : (S, T) -> R {
    private val values = mutableMapOf<T, R>()

    override fun invoke(
        context: S,
        argument: T,
    ): R = values.getOrPut(argument) { f(context, argument) }
}

fun <S, T, R> ((S, T) -> R).memoize(): (S, T) -> R = MemoizeExtension1(this)
