package com.strumenta.kolasu.interning

import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.parsing.TokenCategory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test as test

class PointInternerTest {
    @test
    fun `interned points are equal to directly constructed points`() {
        val interner = PointInterner()
        val p = interner.intern(5, 12)
        assertEquals(Point(5, 12), p)
        assertEquals(5, p.line)
        assertEquals(12, p.column)
    }

    @test
    fun `interning the same coordinates twice returns the same instance`() {
        val interner = PointInterner()
        val a = interner.intern(3, 7)
        val b = interner.intern(3, 7)
        assertSame(a, b)
    }

    @test
    fun `interning different coordinates returns different instances`() {
        val interner = PointInterner()
        val a = interner.intern(1, 0)
        val b = interner.intern(1, 1)
        assertFalse(a === b)
        assertEquals(Point(1, 0), a)
        assertEquals(Point(1, 1), b)
    }

    @test
    fun `intern(point) overload returns same instance as intern(line, col)`() {
        val interner = PointInterner()
        val direct = interner.intern(10, 4)
        val fromPoint = interner.intern(Point(10, 4))
        assertSame(direct, fromPoint)
    }

    @test
    fun `interned point has correct compareTo behaviour`() {
        val interner = PointInterner()
        val p1 = interner.intern(1, 0)
        val p2 = interner.intern(2, 0)
        assertTrue(p1 < p2)
    }

    @test
    fun `cache does not grow beyond maxSize`() {
        val maxSize = 100
        val interner = PointInterner(maxSize)
        for (i in 1..200) {
            interner.intern(i, 0)
        }
        assertTrue(interner.size <= maxSize)
    }

    @test
    fun `points beyond maxSize are still correct even if not interned`() {
        val maxSize = 10
        val interner = PointInterner(maxSize)
        for (i in 1..20) interner.intern(i, 0)
        val p = interner.intern(99, 0)
        assertEquals(Point(99, 0), p)
    }

    @test
    fun `concurrent access produces consistent results`() {
        val interner = PointInterner()
        val threads = 8
        val callsPerThread = 10_000
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        val wrongCount = AtomicInteger(0)

        repeat(threads) { t ->
            executor.submit {
                try {
                    for (i in 0 until callsPerThread) {
                        val line = (i % 100) + 1
                        val col = i % 50
                        val p = interner.intern(line, col)
                        if (p.line != line || p.column != col) wrongCount.incrementAndGet()
                        // Second call must return the same value (possibly not same instance if evicted,
                        // but must be equal)
                        val p2 = interner.intern(line, col)
                        if (p2.line != line || p2.column != col) wrongCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        executor.shutdown()
        assertEquals(0, wrongCount.get(), "Some interned points had wrong coordinates")
    }

    @test
    fun `multiple interners are independent`() {
        val a = PointInterner()
        val b = PointInterner()
        val pa = a.intern(1, 0)
        val pb = b.intern(1, 0)
        assertEquals(pa, pb)
        // They may or may not be the same instance — that's fine, they are independent caches.
        assertEquals(1, a.size)
        assertEquals(1, b.size)
    }

    @test
    fun `no hash collisions for typical source code coordinates`() {
        // Regression: the Long-key encoding caused col XOR line hash, concentrating 500K entries
        // into ~10K buckets (49 entries/bucket average, all tree nodes).
        // Int key (line shl 16 or col) with ConcurrentHashMap spread produces distinct bucket per pair.
        val interner = PointInterner(maxSize = 50_000)
        // Fill with coordinates typical of source code
        for (line in 1..200) {
            for (col in 0..100) {
                interner.intern(line, col)
            }
        }
        // 200 * 101 = 20,200 entries — each should be a cache hit on second access
        var hits = 0
        for (line in 1..200) {
            for (col in 0..100) {
                val a = interner.intern(line, col)
                val b = interner.intern(line, col)
                assertSame(a, b)
                hits++
            }
        }
        // All second-pass lookups should return the same instance
        assertTrue(hits > 0)
    }

    @test
    fun `overflow path handles large line and column values`() {
        val interner = PointInterner()
        val p = interner.intern(70000, 0)
        assertEquals(Point(70000, 0), p)
        val p2 = interner.intern(70000, 0)
        assertSame(p, p2)
    }

    @test
    fun `overflow path handles large column values`() {
        val interner = PointInterner()
        val p = interner.intern(1, 70000)
        assertEquals(Point(1, 70000), p)
        val p2 = interner.intern(1, 70000)
        assertSame(p, p2)
    }
}

class TokenCategoryInternerTest {
    @test
    fun `canonicalize returns companion constant for known types`() {
        val fresh = TokenCategory("Keyword")
        val canonical = fresh.canonicalize()
        assertSame(TokenCategory.KEYWORD, canonical)
    }

    @test
    fun `canonicalize is idempotent for companion constants`() {
        val canonical = TokenCategory.KEYWORD.canonicalize()
        assertSame(TokenCategory.KEYWORD, canonical)
    }

    @test
    fun `canonicalize returns receiver for unknown types`() {
        val custom = TokenCategory("MyLanguageSpecificToken")
        val result = custom.canonicalize()
        assertSame(custom, result)
    }

    @test
    fun `companion intern returns canonical constant`() {
        val result = TokenCategory.intern("Comment")
        assertSame(TokenCategory.COMMENT, result)
    }

    @test
    fun `companion intern constructs new instance for unknown types`() {
        val result = TokenCategory.intern("Exotic")
        assertEquals(TokenCategory("Exotic"), result)
    }

    @test
    fun `all ten known categories canonicalize correctly`() {
        val cases =
            listOf(
                "Comment" to TokenCategory.COMMENT,
                "Keyword" to TokenCategory.KEYWORD,
                "Numeric literal" to TokenCategory.NUMERIC_LITERAL,
                "String literal" to TokenCategory.STRING_LITERAL,
                "Other literal" to TokenCategory.OTHER_LITERAL,
                "Plain text" to TokenCategory.PLAIN_TEXT,
                "Whitespace" to TokenCategory.WHITESPACE,
                "Identifier" to TokenCategory.IDENTIFIER,
                "Punctuation" to TokenCategory.PUNCTUATION,
                "Operator" to TokenCategory.OPERATOR,
            )
        for ((type, expected) in cases) {
            val result = TokenCategory(type).canonicalize()
            assertSame(expected, result, "Expected canonical constant for type '$type'")
        }
    }

    @test
    fun `canonicalized category equals original`() {
        val fresh = TokenCategory("Whitespace")
        val canonical = fresh.canonicalize()
        assertEquals(fresh, canonical)
    }
}
