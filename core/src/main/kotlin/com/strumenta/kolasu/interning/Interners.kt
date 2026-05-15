package com.strumenta.kolasu.interning

import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.parsing.TokenCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * Canonicalizes [Point] instances so that equal points share the same object.
 *
 * **Key design: Int-packed keys to avoid hash collisions**
 *
 * The naive approach of using `Long` as a map key causes a severe hash-collision problem.
 * `Long.hashCode()` is defined as `(int)(value ^ (value >>> 32))`. For a key packed as
 * `(line << 32) | col`, this yields `hashCode = col XOR line`. For typical source code
 * (line ≤ 10 000, col ≤ 120) only ~10 000 distinct hash values exist, so 500 K entries
 * spread across 10 K buckets → 49 entries/bucket average. `ConcurrentHashMap` treefies
 * buckets above 8 entries, producing ~500 K `TreeNode` objects and O(log 49) lookups.
 *
 * This implementation uses an `Int` key = `(line shl 16) or column` for the common case
 * (line ≤ 65 535, col ≤ 65 535). `Int.hashCode()` returns the int itself, and
 * `ConcurrentHashMap`'s spread function computes `key XOR (key >>> 16) = ((line shl 16)|col) XOR line`,
 * which is injective over the domain → every entry lands in a distinct bucket → no tree nodes.
 *
 * **Lifecycle:** prefer one interner per pipeline run, not a long-lived singleton,
 * so the cached Points can be collected with the pipeline's other objects.
 *
 * Thread safety: all operations are safe for concurrent use.
 */
class PointInterner(val maxSize: Int = DEFAULT_MAX_SIZE) {

    // Primary cache: int key = (line shl 16) or column — valid for line ≤ 65535, col ≤ 65535.
    // Covers virtually all real source code with excellent hash distribution (no tree nodes).
    private val primary = ConcurrentHashMap<Int, Point>(minOf(maxSize, 8192))

    // Overflow for pathological cases (line > 65535 or col > 65535). Negligible in practice.
    private val overflow = ConcurrentHashMap<Long, Point>(4)

    /** Returns a canonical [Point] equal to `Point(line, column)`. */
    fun intern(line: Int, column: Int): Point =
        if (line in 1..0xFFFF && column in 0..0xFFFF) {
            internPrimary(line, column)
        } else {
            internOverflow(line, column)
        }

    private fun internPrimary(line: Int, column: Int): Point {
        if (primary.size >= maxSize) {
            return Point(line, column)
        }
        val key = (line shl 16) or column
        val existing = primary[key]
        if (existing != null) {
            return existing
        }
        val point = Point(line, column)
        return primary.putIfAbsent(key, point) ?: point
    }

    private fun internOverflow(line: Int, column: Int): Point {
        val key = line.toLong().shl(32) or column.toLong().and(0xFFFFFFFFL)
        return overflow.getOrPut(key) { Point(line, column) }
    }

    /** Convenience overload that interns an already-constructed [Point]. */
    fun intern(point: Point): Point = intern(point.line, point.column)

    /** Number of distinct Points currently held in both caches. */
    val size: Int get() = primary.size + overflow.size

    companion object {
        /** Default upper bound: ~64 K distinct points (≈ 2–3 MB, far cheaper than millions of duplicates). */
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
        TokenCategory.OPERATOR
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
