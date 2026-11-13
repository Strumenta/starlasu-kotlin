package com.strumenta.kolasu.testing

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * Runs [action] on [threads] workers started simultaneously, [iterations] times each.
 * Fails if any worker throws or if the run does not terminate within [timeoutSeconds]
 * (possible deadlock). Shared by the thread-safety regression tests of all modules.
 */
fun runConcurrently(
    threads: Int = 8,
    iterations: Int = 300,
    timeoutSeconds: Long = 30,
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
            executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS),
            "Concurrent test did not terminate within ${timeoutSeconds}s (possible deadlock)"
        )
    } finally {
        executor.shutdownNow()
    }
    assertTrue(
        errors.isEmpty(),
        "Concurrent execution produced errors: ${errors.firstOrNull()?.stackTraceToString()}"
    )
}
