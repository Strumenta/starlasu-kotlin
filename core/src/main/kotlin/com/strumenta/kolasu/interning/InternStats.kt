package com.strumenta.kolasu.interning

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight diagnostic tracker for measuring duplication of value objects.
 *
 * Add this temporarily to a class's construction path, run a representative
 * pipeline, then call [report] to see duplication ratios before committing
 * to an interning strategy.
 *
 * Example usage inside a constructor body:
 * ```kotlin
 * init {
 *     PointStats.record(this)
 * }
 * ```
 *
 * @param name  label used in the [report] output.
 * @param topN  how many most-frequent values to print in the report.
 */
class InternStats<T : Any>(
    val name: String,
    private val topN: Int = 20,
    private val maxTrackedValues: Int = 500_000,
) {
    private val total = AtomicLong(0)
    private val counts = ConcurrentHashMap<T, AtomicLong>(1024)

    fun record(value: T) {
        total.incrementAndGet()
        if (counts.size < maxTrackedValues || counts.containsKey(value)) {
            counts.getOrPut(value) { AtomicLong(0) }.incrementAndGet()
        }
    }

    /** Distinct values observed (capped at [maxTrackedValues]). */
    val distinctCount: Int get() = counts.size

    /** Total calls to [record]. */
    val totalCount: Long get() = total.get()

    /**
     * Fraction of constructions that are exact duplicates of an already-seen value.
     * 1.0 means every construction is a duplicate; 0.0 means every value is unique.
     */
    val duplicationRatio: Double
        get() {
            val t = totalCount
            val d = distinctCount.toLong()
            return if (t == 0L) 0.0 else 1.0 - (d.toDouble() / t)
        }

    /**
     * Estimated shallow memory that interning could save, assuming [shallowSizeBytes] per instance.
     * This is an approximation: it counts (total - distinct) duplicate instances × object size.
     */
    fun estimatedSavingsBytes(shallowSizeBytes: Int): Long {
        val duplicates = totalCount - distinctCount
        return duplicates * shallowSizeBytes
    }

    /** Formatted summary suitable for logging or printing. */
    fun report(): String = buildString {
        val t = totalCount
        val d = distinctCount
        val dup = duplicationRatio
        appendLine("=== InternStats[$name] ===")
        appendLine("  total constructed : $t")
        appendLine("  distinct values   : $d")
        appendLine("  duplication ratio : ${"%.1f%%".format(dup * 100)}")
        if (d < maxTrackedValues) {
            val top = counts.entries
                .sortedByDescending { it.value.get() }
                .take(topN)
            appendLine("  top $topN most frequent values:")
            top.forEach { (v, c) ->
                appendLine("    ${c.get().toString().padStart(10)}x  $v")
            }
        } else {
            appendLine("  (value tracking truncated at $maxTrackedValues distinct entries)")
        }
    }
}

/**
 * Pre-built [InternStats] instances for Kolasu value types.
 *
 * These are intentionally **not** active by default. Instrument them temporarily
 * by adding `PointStats.record(this)` to `Point.init { }` or similar, then run
 * a representative pipeline and call the corresponding `report()`.
 */
object KolasuInternStats {
    val point = InternStats<Pair<Int, Int>>("Point (line, column)")
    val position = InternStats<String>("Position [start→end]")

    fun reportAll(): String = buildString {
        append(point.report())
        append(position.report())
    }
}