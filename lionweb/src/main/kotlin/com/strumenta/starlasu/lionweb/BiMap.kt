package com.strumenta.starlasu.lionweb

import java.util.IdentityHashMap

/**
 * This class is thread-safe.
 */
class BiMap<A, B>(
    val usingIdentity: Boolean = false,
) {
    val `as`: Set<A>
        get() = synchronized(this) { internalAsToBs.keys }
    val bs: Set<B>
        get() = synchronized(this) { internalBsToAs.keys }
    val asToBsMap: Map<A, B>
        get() = internalAsToBs
    val bsToAsMap: Map<B, A>
        get() = internalBsToAs

    private val internalAsToBs = if (usingIdentity) IdentityHashMap() else mutableMapOf<A, B>()
    private val internalBsToAs = if (usingIdentity) IdentityHashMap() else mutableMapOf<B, A>()

    fun associate(
        a: A,
        b: B,
    ) {
        synchronized(this) {
            internalAsToBs[a] = b
            internalBsToAs[b] = a
        }
    }

    fun byA(a: A): B? = synchronized(this) { internalAsToBs[a] }

    fun byB(b: B): A? = synchronized(this) { internalBsToAs[b] }

    fun containsA(a: A): Boolean = synchronized(this) { internalAsToBs.containsKey(a) }

    fun containsB(b: B): Boolean = synchronized(this) { internalBsToAs.containsKey(b) }

    fun clear() {
        synchronized(this) {
            internalAsToBs.clear()
            internalBsToAs.clear()
        }
    }
}
