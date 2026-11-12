package com.strumenta.kolasu.lionweb

import java.util.Collections
import java.util.IdentityHashMap

/**
 * This class is thread-safe.
 *
 * The map and set accessors return snapshots: safe to iterate while other threads call
 * [associate], but each call allocates a copy of the registry, so single-key lookups on
 * hot paths should use [byA]/[byB]/[findAByB] instead. When [usingIdentity] is true the
 * snapshots preserve identity semantics: keys that are distinct but equal are kept.
 */
class BiMap<A, B>(val usingIdentity: Boolean = false) {
    val `as`: Set<A>
        get() = synchronized(this) {
            if (usingIdentity) identitySetOf(_asToBs.keys) else _asToBs.keys.toSet()
        }
    val bs: Set<B>
        get() = synchronized(this) {
            if (usingIdentity) identitySetOf(_bsToAs.keys) else _bsToAs.keys.toSet()
        }
    val asToBsMap: Map<A, B>
        get() = synchronized(this) {
            if (usingIdentity) IdentityHashMap(_asToBs) else _asToBs.toMap()
        }
    val bsToAsMap: Map<B, A>
        get() = synchronized(this) {
            if (usingIdentity) IdentityHashMap(_bsToAs) else _bsToAs.toMap()
        }

    private val _asToBs = if (usingIdentity) IdentityHashMap() else mutableMapOf<A, B>()
    private val _bsToAs = if (usingIdentity) IdentityHashMap() else mutableMapOf<B, A>()

    private fun <E> identitySetOf(keys: Set<E>): Set<E> =
        Collections.newSetFromMap(IdentityHashMap<E, Boolean>()).also { it.addAll(keys) }

    fun associate(a: A, b: B) {
        synchronized(this) {
            _asToBs[a] = b
            _bsToAs[b] = a
        }
    }

    fun byA(a: A): B? = synchronized(this) { _asToBs[a] }
    fun byB(b: B): A? = synchronized(this) { _bsToAs[b] }

    /**
     * Returns the key associated to the first B value satisfying [predicate], iterating
     * under the lock without copying the map.
     */
    fun findAByB(predicate: (B) -> Boolean): A? = synchronized(this) {
        _bsToAs.entries.firstOrNull { predicate(it.key) }?.value
    }

    fun containsA(a: A): Boolean = synchronized(this) { _asToBs.containsKey(a) }
    fun containsB(b: B): Boolean = synchronized(this) { _bsToAs.containsKey(b) }
    fun clear() {
        synchronized(this) {
            _asToBs.clear()
            _bsToAs.clear()
        }
    }
}
