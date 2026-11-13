package com.strumenta.kolasu.utils

class Memoize1<in T, out R>(val f: (T) -> R) : (T) -> R {
    private val values = mutableMapOf<T, R>()

    /**
     * The computation runs outside the cache lock: memoized functions are expected to be
     * pure, so a duplicated computation on a concurrent miss is acceptable, while holding
     * the monitor across [f] would deadlock when memoized computations invoke other
     * memoizers (including this one, recursively).
     */
    override fun invoke(x: T): R {
        synchronized(values) {
            if (values.containsKey(x)) {
                @Suppress("UNCHECKED_CAST")
                return values[x] as R
            }
        }
        val computed = f(x)
        return synchronized(values) { values.putIfAbsent(x, computed) ?: computed }
    }
}

fun <T, R> ((T) -> R).memoize(): (T) -> R = Memoize1(this)

class MemoizeExtension1<in S, in T, out R>(val f: (S, T) -> R) : (S, T) -> R {
    private val values = mutableMapOf<T, R>()

    override fun invoke(context: S, argument: T): R {
        synchronized(values) {
            if (values.containsKey(argument)) {
                @Suppress("UNCHECKED_CAST")
                return values[argument] as R
            }
        }
        val computed = f(context, argument)
        return synchronized(values) { values.putIfAbsent(argument, computed) ?: computed }
    }
}

fun <S, T, R> ((S, T) -> R).memoize(): (S, T) -> R = MemoizeExtension1(this)
