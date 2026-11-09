package com.strumenta.kolasu

import com.strumenta.kolasu.ids.CachingNodeIDProvider
import com.strumenta.kolasu.ids.MyNonRoot
import com.strumenta.kolasu.ids.MyRoot
import com.strumenta.kolasu.ids.StructuralNodeIdProvider
import com.strumenta.kolasu.ids.caching
import com.strumenta.kolasu.language.KolasuLanguage
import com.strumenta.kolasu.model.SyntheticSource
import com.strumenta.kolasu.model.assignParents
import com.strumenta.kolasu.model.codeAtPosition
import com.strumenta.kolasu.model.pos
import com.strumenta.kolasu.parsing.SimpleLangKolasuParser
import com.strumenta.kolasu.transformation.ASTTransformer
import com.strumenta.kolasu.transformation.CU
import com.strumenta.kolasu.traversing.FastWalker
import com.strumenta.kolasu.utils.memoize
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Regression tests for the thread-safety fixes: they exercise the shared mutable state that
 * was previously unsynchronized (caches, interners, rule maps, memoizers).
 */
class ThreadSafetyTest {
    /**
     * Runs [action] on [threads] workers started simultaneously, [iterations] times each.
     * Fails if any worker throws or if the run does not terminate (possible deadlock).
     */
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

    // LinesSplitter (Position.kt): shared access-order LinkedHashMap. Even reads mutate it.
    @Test
    fun codeAtPositionIsThreadSafe() {
        val codes = listOf(
            (1..50).joinToString("\n") { "line $it content" },
            (1..30).joinToString("\n") { "other $it text here" },
            (1..40).joinToString("\n") { "more $it stuff!!!" }
        )
        runConcurrently(threads = 8, iterations = 500) { w, i ->
            val code = codes[(w + i) % codes.size]
            val position = pos(1, 0, 2, 5)
            val extracted = code.codeAtPosition(position)
            // codeAtPosition splits keeping the trailing newline: lines()[0] has none
            val expected = code.lines()[0] + "\n" + code.lines()[1].substring(0, 5)
            assertEquals(expected, extracted)
        }
    }

    // KolasuParser: the shared PredictionContextCache was an unsynchronized HashMap.
    // Note: `display` takes a literal-only expression in SimpleLang, so we use a valid
    // (and prediction-heavy) input — `display c` would legitimately produce issues.
    @Test
    fun sharedParserIsThreadSafe() {
        val parser = SimpleLangKolasuParser()
        val code =
            """set a = 10
            |set b = ""
            |display 10 + 20 * 30
            """.trimMargin()
        val referenceRoot = parser.parseFirstStage(code).root!!.toStringTree()
        runConcurrently(threads = 8, iterations = 40) { _, _ ->
            val result = parser.parseFirstStage(code)
            assertTrue(result.issues.isEmpty(), "Unexpected parsing issues: ${result.issues}")
            assertEquals(referenceRoot, result.root!!.toStringTree())
        }
    }

    // ASTTransformer: transform() writes NO_CHILD_NODE markers into the shared
    // NodeFactory.children map; factories is mutated by registerNodeFactory.
    @Test
    fun sharedTransformerIsThreadSafe() {
        data class SrcNode(val v: Int)

        val transformer = ASTTransformer()
        transformer.registerNodeFactory(SrcNode::class) { _, _ -> CU() }
        runConcurrently(threads = 8, iterations = 300) { _, i ->
            val result = transformer.transform(SrcNode(i))
            assertIs<CU>(result)
        }
    }

    // CachingNodeIDProvider: IdentityHashMap cache shared across pipeline threads.
    @Test
    fun cachingNodeIdProviderIsThreadSafe() {
        val children = (1..20).map { MyNonRoot(it) }
        val ast = MyRoot(foos = children.toMutableList())
        ast.assignParents()
        ast.setSourceForTree(SyntheticSource("S1"))
        val provider: CachingNodeIDProvider = StructuralNodeIdProvider().caching()

        // Precompute the expected IDs single-threaded
        val expected = children.associateWith { provider.id(it) }

        runConcurrently(threads = 8, iterations = 300) { w, i ->
            val node = children[(w + i) % children.size]
            assertEquals(expected[node], provider.id(node))
        }
    }

    // Memoize1: plain HashMap before.
    @Test
    fun memoizeIsThreadSafe() {
        val memoized = { x: Int -> x * 2 }.memoize()
        runConcurrently(threads = 8, iterations = 500) { w, i ->
            val input = (w * 31 + i) % 64
            assertEquals(input * 2, memoized(input))
        }
    }

    // FastWalker: WeakHashMap shared if the walker is reused across threads.
    @Test
    fun fastWalkerIsThreadSafe() {
        val ast = MyRoot(foos = (1..30).map { MyNonRoot(it) }.toMutableList())
        val walker = FastWalker(ast)
        val expected = walker.walk().toList()
        runConcurrently(threads = 8, iterations = 200) { _, _ ->
            assertEquals(expected, walker.walk().toList())
        }
    }

    // KolasuLanguage: mutable registration lists exposed through live-view getters.
    @Test
    fun kolasuLanguageIsThreadSafe() {
        val language = KolasuLanguage("test.language")
        val classes = listOf(MyRoot::class, MyNonRoot::class)
        runConcurrently(threads = 4, iterations = 100) { w, i ->
            language.addClass(classes[(w + i) % classes.size])
            // Iterate the (now snapshot) lists while others register classes
            language.astClasses.forEach { it.simpleName }
            language.enumClasses.forEach { it.simpleName }
        }
        classes.forEach { assertTrue(language.astClasses.contains(it)) }
    }
}
