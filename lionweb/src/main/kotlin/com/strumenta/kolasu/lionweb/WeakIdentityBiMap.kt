@file:Suppress("EqualsOrHashCode", "MemberVisibilityCanBePrivate", "UNCHECKED_CAST")

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.HashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Weak + identity-based bi-directional map (JDK 8 compatible).
 *
 * - Identity semantics everywhere (===), hash via System.identityHashCode
 * - Both sides are weak (keys and "values" are stored as WeakKey on both maps)
 * - Automatic cleanup via ReferenceQueue
 * - Thread-safe via a single ReentrantLock
 *
 * Caveat: maps use Any as key type to allow heterogeneous keys (WeakKey and LookupKey)
 * for O(1) lookups; type safety is enforced by this class’ invariants.
 */
class WeakIdentityBiMap<A : Any, B : Any> {

    /**
     * Weak map key that:
     *  - stores stabilized identity hash (System.identityHashCode)
     *  - compares by referential equality (=== on referents)
     *  - remembers which side (forward/backward) it belongs to
     *  - keeps a pointer to the counterpart WeakKey on the opposite map
     */
    private class WeakKey<T : Any>(
        referent: T,
        q: ReferenceQueue<Any>,
        val forwardSide: Boolean
    ) : WeakReference<T>(referent, q) {
        val hash: Int = System.identityHashCode(referent)

        @Volatile var counterpart: WeakKey<*>? = null
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            val a = this.get() ?: return false
            return when (other) {
                is WeakKey<*> -> a === other.get()
                is LookupKey<*> -> a === other.strong
                else -> false
            }
        }
    }

    /**
     * Strong lookup key to query/remove without allocating a new WeakReference.
     * Matches WeakKey by referential equality and uses identity hash.
     */
    private class LookupKey<T : Any>(val strong: T) {
        private val hash = System.identityHashCode(strong)
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = when (other) {
            is WeakKey<*> -> strong === other.get()
            is LookupKey<*> -> strong === other.strong
            else -> false
        }
    }

    // Using Any for key type allows us to use both WeakKey and LookupKey as keys (hash/equals-compatible).
    private val forward: MutableMap<Any, WeakKey<B>> = HashMap()
    private val backward: MutableMap<Any, WeakKey<A>> = HashMap()

    private val queue: ReferenceQueue<Any> = ReferenceQueue()
    private val lock = ReentrantLock()

    /**
     * Drain the reference queue and remove dead pairs from both maps.
     * Must be called under lock.
     */
    private fun drainQueueLocked() {
        var ref = queue.poll() as WeakKey<*>?
        while (ref != null) {
            if (ref.forwardSide) {
                val kRef = ref as WeakKey<A>
                val vRef = forward.remove(kRef)
                if (vRef != null) backward.remove(vRef)
            } else {
                val vRef = ref as WeakKey<B>
                val kRef = backward.remove(vRef)
                if (kRef != null) forward.remove(kRef)
            }
            // Also try removing via counterpart if still present
            val other = ref.counterpart
            if (other != null) {
                if (other.forwardSide) {
                    forward.remove(other)
                } else {
                    backward.remove(other)
                }
            }
            ref = queue.poll() as WeakKey<*>?
        }
    }

    fun associate(k: A, v: B) = lock.withLock {
        drainQueueLocked()

        // Ensure bijection (1-1)
        val oldVRef = forward.remove(LookupKey(k))
        if (oldVRef != null) backward.remove(oldVRef)

        val oldKRef = backward.remove(LookupKey(v))
        if (oldKRef != null) forward.remove(oldKRef)

        val kRef = WeakKey(k, queue, forwardSide = true)
        val vRef = WeakKey(v, queue, forwardSide = false)
        kRef.counterpart = vRef
        vRef.counterpart = kRef

        forward[kRef] = vRef
        backward[vRef] = kRef
    }


    fun byA(k: A): B? = lock.withLock {
        drainQueueLocked()
        forward[LookupKey(k)]?.get()
    }

    fun byB(v: B): A? = lock.withLock {
        drainQueueLocked()
        backward[LookupKey(v)]?.get()
    }

    /** Remove association for k, if present. Thread-safe. */
    fun removeByA(k: A) = lock.withLock {
        drainQueueLocked()
        val vRef = forward.remove(LookupKey(k))
        if (vRef != null) backward.remove(vRef)
    }

    /** Remove association for v, if present. Thread-safe. */
    fun removeByB(v: B) = lock.withLock {
        drainQueueLocked()
        val kRef = backward.remove(LookupKey(v))
        if (kRef != null) forward.remove(kRef)
    }

    /** Current number of live pairs (after a drain). Thread-safe. */
    fun size(): Int = lock.withLock {
        drainQueueLocked()
        forward.size // forward and backward always have same cardinality
    }

    /** Optional explicit cleanup hook. Thread-safe. */
    fun cleanUp() = lock.withLock {
        drainQueueLocked()
    }

    fun clear() {
        lock.withLock {
            forward.clear()
            backward.clear()
        }
    }

    fun containsA(k: A): Boolean = lock.withLock {
        drainQueueLocked()
        forward.containsKey(LookupKey(k))
    }

    fun containsB(v: B): Boolean = lock.withLock {
        drainQueueLocked()
        backward.containsKey(LookupKey(v))
    }

    /**
     * Returns the current live keys as a strong Set<K>.
     * The result is a snapshot — changes after this call are not reflected.
     */
    val `as`: Set<A>
        get() = lock.withLock {
            drainQueueLocked()
            forward.keys
                .asSequence()
                .mapNotNull { (it as WeakKey<A>).get() }
                .toSet()
        }

    /**
     * Returns the current live values as a strong Set<V>.
     * The result is a snapshot — changes after this call are not reflected.
     */
    val bs: Set<B>
        get() = lock.withLock {
            drainQueueLocked()
            forward.values
                .asSequence()
                .mapNotNull { it.get() }
                .toSet()
        }
}
