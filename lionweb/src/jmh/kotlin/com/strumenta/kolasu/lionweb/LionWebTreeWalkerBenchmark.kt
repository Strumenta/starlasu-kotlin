package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.language.KolasuLanguage
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

/**
 * Benchmarks [LionWebTreeWalker.thisAndAllDescendants] and
 * [LionWebTreeWalker.thisAndAllDescendantsLeavesFirst].
 *
 * The old implementation used Kotlin `sequence { yield() }` coroutines, which allocated
 * a coroutine state-machine object per visited node (41 511 allocations on a 200-node tree).
 * The new implementation is iterative / recursive with a plain ArrayList.
 *
 * Tree sizes:
 *   - small : root + 5 containers × 2 leaves = 16 nodes
 *   - large : root + 100 containers × 2 leaves = 301 nodes
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class LionWebTreeWalkerBenchmark {

    private val walker = LionWebTreeWalker()
    private lateinit var smallLwTree: LWNode
    private lateinit var largeLwTree: LWNode

    @Setup(Level.Trial)
    fun setupTrial() {
        val language = KolasuLanguage("com.strumenta.WalkerBenchLang").apply {
            addClass(BenchRoot::class)
            addClass(BenchContainerNode::class)
            addClass(BenchLeafNode::class)
        }
        val converter = LionWebModelConverter()
        converter.exportLanguageToLionWeb(language)

        converter.clearNodesMapping()
        smallLwTree = converter.exportModelToLionWeb(buildKolasuTree(5, leavesPerContainer = 2))
        converter.clearNodesMapping()
        largeLwTree = converter.exportModelToLionWeb(buildKolasuTree(100, leavesPerContainer = 2))
        converter.clearNodesMapping()
    }

    // -----------------------------------------------------------------------
    // thisAndAllDescendants
    // -----------------------------------------------------------------------

    @Benchmark
    fun descendantsSmall(bh: Blackhole) {
        bh.consume(walker.thisAndAllDescendants(smallLwTree))
    }

    @Benchmark
    fun descendantsLarge(bh: Blackhole) {
        bh.consume(walker.thisAndAllDescendants(largeLwTree))
    }

    // -----------------------------------------------------------------------
    // thisAndAllDescendantsLeavesFirst
    // -----------------------------------------------------------------------

    @Benchmark
    fun leavesFirstSmall(bh: Blackhole) {
        bh.consume(walker.thisAndAllDescendantsLeavesFirst(smallLwTree))
    }

    @Benchmark
    fun leavesFirstLarge(bh: Blackhole) {
        bh.consume(walker.thisAndAllDescendantsLeavesFirst(largeLwTree))
    }

    // -----------------------------------------------------------------------

    private fun buildKolasuTree(containerCount: Int, leavesPerContainer: Int): BenchRoot {
        val containers = (1..containerCount).map { c ->
            BenchContainerNode(
                label = "c-$c",
                leaves = (1..leavesPerContainer).map { l -> BenchLeafNode("l-$c-$l") }.toMutableList()
            )
        }.toMutableList()
        val root = BenchRoot(containers = containers)
        root.assignParents()
        return root
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(LionWebTreeWalkerBenchmark::class.java.simpleName)
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .addProfiler(GCProfiler::class.java)
                .build()
            Runner(options).run()
        }
    }
}
