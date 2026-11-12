package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.model.Node
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

data class ConcurrentTestNode(val v: Int) : Node()

/**
 * Regression tests for the thread-safety fixes in the lionweb module.
 */
class ThreadSafetyTest {
    private fun runConcurrently(
        threads: Int = 8,
        iterations: Int = 300,
        action: (workerIndex: Int, iteration: Int) -> Unit
    ) {
        val errors = ConcurrentLinkedQueue<Throwable>()
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            repeat(threads) { w ->
                executor.submit {
                    try {
                        startLatch.await()
                        repeat(iterations) { i -> action(w, i) }
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }
            startLatch.countDown()
            executor.shutdown()
            assertTrue(
                executor.awaitTermination(120, TimeUnit.SECONDS),
                "Concurrent test did not terminate (possible deadlock)"
            )
        } finally {
            executor.shutdownNow()
        }
        assertTrue(
            errors.isEmpty(),
            "Concurrent execution produced errors: ${errors.firstOrNull()?.stackTraceToString()}"
        )
    }

    // BiMap: the as/bs/asToBsMap/bsToAsMap getters used to expose live views of the
    // internal maps, so iterating them while another thread called associate() threw
    // ConcurrentModificationException. They now return snapshots.
    @Test
    fun biMapGettersAreThreadSafe() {
        val map = BiMap<Int, String>()
        map.associate(0, "zero")
        runConcurrently(threads = 8, iterations = 300) { w, i ->
            map.associate(w * 10_000 + i + 1, "v${w}x$i")
            // Iterate the views: they must be snapshots, not live views
            map.asToBsMap.entries.forEach { it.value.length }
            map.bsToAsMap.entries.forEach { it.value.hashCode() }
            map.`as`.forEach { it.hashCode() }
            map.bs.forEach { it.hashCode() }
        }
        assertEquals(2401, map.asToBsMap.size)
    }

    // BiMap: with usingIdentity = true, snapshots must preserve identity semantics —
    // two distinct keys that compare equal must not collapse into one entry.
    @Test
    fun biMapSnapshotsPreserveIdentitySemantics() {
        data class Key(val value: Int)

        val a = Key(1)
        val b = Key(1)
        val map = BiMap<Key, String>(usingIdentity = true)
        map.associate(a, "first")
        map.associate(b, "second")

        assertEquals("first", map.byA(a))
        assertEquals("second", map.byA(b))
        assertEquals(2, map.asToBsMap.size)
        assertEquals(2, map.bsToAsMap.size)
        assertEquals(2, map.`as`.size)
        assertEquals(2, map.bs.size)
        assertSame(a, map.findAByB { it == "first" })
    }

    // SequentialNodeIdProvider: IdentityHashMap + non-atomic counter before.
    @Test
    fun sequentialNodeIdProviderIsThreadSafe() {
        val provider = SequentialNodeIdProvider()
        val nodes = (1..40).map { ConcurrentTestNode(it) }
        // Each node must keep a stable ID and distinct nodes must get distinct IDs
        val ids = ConcurrentLinkedQueue<Pair<Node, String>>()
        runConcurrently(threads = 8, iterations = 200) { w, i ->
            val node = nodes[(w + i) % nodes.size]
            ids.add(node to provider.id(node))
        }
        val byNode = ids.groupBy({ it.first }, { it.second })
        byNode.forEach { (node, nodeIds) ->
            assertEquals(1, nodeIds.toSet().size, "Node $node got different IDs: $nodeIds")
        }
        assertEquals(byNode.size, ids.map { it.second }.toSet().size, "Distinct nodes shared an ID")
    }

    // UUIDNodeIdProvider: IdentityHashMap before.
    @Test
    fun uuidNodeIdProviderIsThreadSafe() {
        val provider = UUIDNodeIdProvider()
        val nodes = (1..40).map { ConcurrentTestNode(it) }
        runConcurrently(threads = 8, iterations = 200) { w, i ->
            val node = nodes[(w + i) % nodes.size]
            assertEquals(provider.id(node), provider.id(node))
        }
        // Stability across the run: the same node must return the same UUID
        val firstIds = nodes.map { provider.id(it) }
        val secondIds = nodes.map { provider.id(it) }
        assertEquals(firstIds, secondIds)
    }

    // PerformanceLogger: unsynchronized singleton with mutable map and file appends.
    @Test
    fun performanceLoggerIsThreadSafe() {
        val tmpFile = File.createTempFile("performance-log-test", ".csv")
        val original = PerformanceLogger.file
        PerformanceLogger.file = tmpFile
        try {
            runConcurrently(threads = 8, iterations = 200) { _, i ->
                val t0 = PerformanceLogger.log("operation - start")
                PerformanceLogger.log("operation - end", t0)
            }
            assertTrue(PerformanceLogger.sumByStep("operation") >= 0)
        } finally {
            PerformanceLogger.file = original
            tmpFile.delete()
        }
    }
}
