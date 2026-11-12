package com.strumenta.kolasu.lionweb

import java.util.Collections
import java.util.IdentityHashMap

/**
 * This class is thread-safe.
 *
 * The map and set accessors return immutable snapshots, safe to iterate while other
 * threads call [associate]. Snapshots are cached and rebuilt only after a mutation, so
 * repeated reads between two mutations cost O(1) instead of copying the registry each
 * time; a snapshot may lag behind concurrent mutations, as usual for snapshot semantics.
 * For single-key lookups on hot paths prefer [byA]/[byB]/[findAByB]. When
 * [usingIdentity] is true the snapshots preserve identity semantics: keys that are
 * distinct but equal are kept.
 */
class BiMap<A, B>(val usingIdentity: Boolean = false) {

    private val _asToBs = if (usingIdentity) IdentityHashMap() else mutableMapOf<A, B>()
    private val _bsToAs = if (usingIdentity) IdentityHashMap() else mutableMapOf<B, A>()

    @Volatile
    private var asSnapshot: Set<A>? = null

    @Volatile
    private var bsSnapshot: Set<B>? = null

    @Volatile
    private var asToBsSnapshot: Map<A, B>? = null

    @Volatile
    private var bsToAsSnapshot: Map<B, A>? = null

    val `as`: Set<A>
        get() = asSnapshot ?: synchronized(this) {
            asSnapshot ?: snapshotAs().also { asSnapshot = it }
        }
    val bs: Set<B>
        get() = bsSnapshot ?: synchronized(this) {
            bsSnapshot ?: snapshotBs().also { bsSnapshot = it }
        }
    val asToBsMap: Map<A, B>
        get() = asToBsSnapshot ?: synchronized(this) {
            asToBsSnapshot ?: snapshotAsToBs().also { asToBsSnapshot = it }
        }
    val bsToAsMap: Map<B, A>
        get() = bsToAsSnapshot ?: synchronized(this) {
            bsToAsSnapshot ?: snapshotBsToAs().also { bsToAsSnapshot = it }
        }

    private fun snapshotAs(): Set<A> =
        if (usingIdentity) identitySetOf(_asToBs.keys) else _asToBs.keys.toSet()

    private fun snapshotBs(): Set<B> =
        if (usingIdentity) identitySetOf(_bsToAs.keys) else _bsToAs.keys.toSet()

    private fun snapshotAsToBs(): Map<A, B> =
        if (usingIdentity) IdentityHashMap(_asToBs) else _asToBs.toMap()

    private fun snapshotBsToAs(): Map<B, A> =
        if (usingIdentity) IdentityHashMap(_bsToAs) else _bsToAs.toMap()

    private fun <E> identitySetOf(keys: Set<E>): Set<E> =
        Collections.newSetFromMap(IdentityHashMap<E, Boolean>()).also { it.addAll(keys) }

    private fun invalidateSnapshots() {
        asSnapshot = null
        bsSnapshot = null
        asToBsSnapshot = null
        bsToAsSnapshot = null
    }

    fun associate(a: A, b: B) {
        synchronized(this) {
            _asToBs[a] = b
            _bsToAs[b] = a
            invalidateSnapshots()
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
            invalidateSnapshots()
        }
    }
}
