// JMH dependencies (added to lionweb/build.gradle):
//   kaptJmh "org.openjdk.jmh:jmh-generator-annprocess:1.37"
//   jmhImplementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
//   jmhImplementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
// Plugin: id 'me.champeau.jmh' version '0.7.2'
//
// Run with: ./gradlew :lionweb:jmh

package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.language.KolasuLanguage
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.model.assignParents
import io.lionweb.model.Node as LWNode
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Minimal AST node types used exclusively by these benchmarks
// ---------------------------------------------------------------------------

/** Leaf node carrying a string payload. */
data class BenchLeafNode(val value: String) : Node()

/** Container node holding a list of leaves and a self-reference. */
data class BenchContainerNode(
    val label: String,
    val leaves: MutableList<BenchLeafNode> = mutableListOf(),
    val next: ReferenceByName<BenchContainerNode>? = null
) : Node()

/** Root node of the benchmark AST. */
data class BenchRoot(
    val containers: MutableList<BenchContainerNode> = mutableListOf()
) : Node()

// ---------------------------------------------------------------------------
// Benchmark class
// ---------------------------------------------------------------------------

/**
 * Measures the average time (and, via GCProfiler in main(), the heap allocation)
 * of [LionWebModelConverter.importModelFromLionWeb] for trees of two sizes.
 *
 * The converter is shared across all iterations inside one trial so that the
 * internal JIT-friendly caches (kClassCache, factoryCache) are warm — exactly
 * as they are in production.  The nodesMapping is flushed between invocations
 * via [LionWebModelConverter.clearNodesMapping] so that each import starts from
 * a clean mapping without accumulating stale entries.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class ImportModelBenchmark {

    private lateinit var converter: LionWebModelConverter

    /** Pre-built LionWeb tree with ~5 nodes (root + 2 containers × 1 leaf each). */
    private lateinit var smallLwTree: LWNode

    /** Pre-built LionWeb tree with ~202 nodes (root + 100 containers × 1 leaf each). */
    private lateinit var largeLwTree: LWNode

    @Setup(Level.Trial)
    fun setupTrial() {
        val language = KolasuLanguage("com.strumenta.BenchLang").apply {
            addClass(BenchRoot::class)
            addClass(BenchContainerNode::class)
            addClass(BenchLeafNode::class)
        }

        converter = LionWebModelConverter()
        converter.exportLanguageToLionWeb(language)

        // Export ASTs once; the resulting LWNode trees are reused across all iterations.
        converter.clearNodesMapping()
        smallLwTree = converter.exportModelToLionWeb(buildKolasuTree(containerCount = 2))
        converter.clearNodesMapping()
        largeLwTree = converter.exportModelToLionWeb(buildKolasuTree(containerCount = 100))

        // Leave nodesMapping empty so the first invocation setup is a no-op.
        converter.clearNodesMapping()
    }

    /**
     * Reset the nodesMapping before every benchmark invocation.
     * Keeps kClassCache and factoryCache warm (they are never cleared).
     */
    @Setup(Level.Invocation)
    fun resetMapping() {
        converter.clearNodesMapping()
    }

    // -----------------------------------------------------------------------
    // Benchmark methods
    // -----------------------------------------------------------------------

    /** Import a small tree (root + 2 containers + 2 leaves = 5 nodes). */
    @Benchmark
    fun importSmallTree(bh: Blackhole) {
        bh.consume(converter.importModelFromLionWeb(smallLwTree))
    }

    /** Import a larger tree (root + 100 containers + 100 leaves = 201 nodes). */
    @Benchmark
    fun importLargeTree(bh: Blackhole) {
        bh.consume(converter.importModelFromLionWeb(largeLwTree))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildKolasuTree(containerCount: Int): BenchRoot {
        val containers = (1..containerCount).map { i ->
            BenchContainerNode(
                label = "container-$i",
                leaves = mutableListOf(BenchLeafNode("leaf-$i"))
            )
        }.toMutableList()
        val root = BenchRoot(containers = containers)
        root.assignParents()
        return root
    }

    // -----------------------------------------------------------------------
    // Main entry point — runs with GCProfiler to expose memory allocation rates
    // -----------------------------------------------------------------------

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(ImportModelBenchmark::class.java.simpleName)
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
