package com.strumenta.kolasu.lionweb

import java.util.IdentityHashMap

/**
 * This class is thread-safe.
 */
class BiMap<A, B>(val usingIdentity: Boolean = false) {
    val `as`: Set<A>
        get() = synchronized(this) { _asToBs.keys.toSet() }
    val bs: Set<B>
        get() = synchronized(this) { _bsToAs.keys.toSet() }
    val asToBsMap: Map<A, B>
        get() = synchronized(this) { _asToBs.toMap() }
    val bsToAsMap: Map<B, A>
        get() = synchronized(this) { _bsToAs.toMap() }

    private val _asToBs = if (usingIdentity) IdentityHashMap() else mutableMapOf<A, B>()
    private val _bsToAs = if (usingIdentity) IdentityHashMap() else mutableMapOf<B, A>()

    fun associate(a: A, b: B) {
        synchronized(this) {
            _asToBs[a] = b
            _bsToAs[b] = a
        }
    }

    fun byA(a: A): B? = synchronized(this) { _asToBs[a] }
    fun byB(b: B): A? = synchronized(this) { _bsToAs[b] }

    fun containsA(a: A): Boolean = synchronized(this) { _asToBs.containsKey(a) }
    fun containsB(b: B): Boolean = synchronized(this) { _bsToAs.containsKey(b) }
    fun clear() {
        synchronized(this) {
            _asToBs.clear()
            _bsToAs.clear()
        }
    }
}
