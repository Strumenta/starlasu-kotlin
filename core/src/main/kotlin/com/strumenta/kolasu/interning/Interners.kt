package com.strumenta.kolasu.interning

import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.parsing.TokenCategory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Canonicalizes [Point] instances so that equal points share the same object.
 *
 * Points are pure-value immutable objects (two ints) that appear millions of times
 * in large pipelines. A bounded cache reduces allocations without risk: the cache
 * simply evicts new points once full rather than blocking or throwing.
 *
 * **Lifecycle:** prefer one interner per pipeline run, not a long-lived singleton,
 * so the cached Points can be collected with the pipeline's other objects.
 *
 * Thread safety: all operations are safe for concurrent use.
 */
class PointInterner(val maxSize: Int = DEFAULT_MAX_SIZE) {

    private val cache = ConcurrentHashMap<Long, Point>(minOf(maxSize, 8192))
    private val _hits = AtomicLong(0)
    private val _misses = AtomicLong(0)

    /** Returns a canonical [Point] equal to `Point(line, column)`. */
    fun intern(line: Int, column: Int): Point {
        // If cache is full, skip interning to avoid unbounded growth.
        if (cache.size >= maxSize) {
            _misses.incrementAndGet()
            return Point(line, column)
        }
        val key = line.toLong().shl(32) or column.toLong()
        val existing = cache[key]
        if (existing != null) {
            _hits.incrementAndGet()
            return existing
        }
        val point = Point(line, column)
        val winner = cache.putIfAbsent(key, point) ?: point
        if (winner === point) _misses.incrementAndGet() else _hits.incrementAndGet()
        return winner
    }

    /** Convenience overload that interns an already-constructed [Point]. */
    fun intern(point: Point): Point = intern(point.line, point.column)

    /** Number of distinct Points currently held in the cache. */
    val size: Int get() = cache.size

    /** Total number of [intern] calls that found an existing entry. */
    val hits: Long get() = _hits.get()

    /** Total number of [intern] calls that created a new entry (or skipped due to full cache). */
    val misses: Long get() = _misses.get()

    /** Fraction of calls satisfied from the cache. Returns NaN if no calls have been made. */
    val hitRate: Double
        get() {
            val total = hits + misses
            return if (total == 0L) Double.NaN else hits.toDouble() / total
        }

    /** Human-readable summary of cache utilisation. */
    fun report(): String {
        val total = hits + misses
        return buildString {
            append("PointInterner: size=${cache.size}/$maxSize")
            append(", calls=$total")
            append(", hits=$hits")
            append(", hitRate=${if (total > 0) "%.1f%%".format(hitRate * 100) else "n/a"}")
        }
    }

    companion object {
        /** Default upper bound: ~64 K distinct points (≈ 2–3 MB, plenty for large codebases). */
        const val DEFAULT_MAX_SIZE = 65_536
    }
}

// ---------------------------------------------------------------------------
// TokenCategory canonicalization
// ---------------------------------------------------------------------------

private val KNOWN_TOKEN_CATEGORIES: Map<String, TokenCategory> by lazy {
    listOf(
        TokenCategory.COMMENT,
        TokenCategory.KEYWORD,
        TokenCategory.NUMERIC_LITERAL,
        TokenCategory.STRING_LITERAL,
        TokenCategory.OTHER_LITERAL,
        TokenCategory.PLAIN_TEXT,
        TokenCategory.WHITESPACE,
        TokenCategory.IDENTIFIER,
        TokenCategory.PUNCTUATION,
        TokenCategory.OPERATOR,
    ).associateBy { it.type }
}

/**
 * Returns the canonical [TokenCategory] companion constant for this category if one exists,
 * otherwise returns the receiver unchanged.
 *
 * Callers that construct `TokenCategory("Keyword")` should call `.canonicalize()` (or switch
 * to using the constant directly) to avoid millions of equal-but-distinct instances.
 */
fun TokenCategory.canonicalize(): TokenCategory = KNOWN_TOKEN_CATEGORIES[type] ?: this

/**
 * Factory that returns the canonical companion constant when [type] matches a known category,
 * otherwise constructs a new [TokenCategory].
 *
 * Prefer this over `TokenCategory(type)` in hot paths.
 */
fun TokenCategory.Companion.intern(type: String): TokenCategory =
    KNOWN_TOKEN_CATEGORIES[type] ?: TokenCategory(type)