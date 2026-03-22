package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.language.KolasuLanguage
import com.strumenta.kolasu.model.assignParents
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
 * Benchmarks [LionWebModelConverter.exportModelToLionWeb].
 *
 * The hotspot here is [StructuralLionWebNodeIdProvider.id], which recursively recomputes
 * the ID of every ancestor for each node (O(N×depth) calls to containingProperty() and
 * indexInContainingProperty(), each allocating List<PropertyDescription> ~356 MB on large
 * models).  [com.strumenta.kolasu.ids.CachingNodeIDProvider] intercepts recursive parent
 * ID lookups and is now the default wrapper.
 *
 * Two tree sizes:
 *   - small : root + 10 containers × 1 leaf = 21 nodes
 *   - large : root + 200 containers × 1 leaf = 401 nodes
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class ExportModelBenchmark {

    private lateinit var converter: LionWebModelConverter
    private lateinit var smallTree: BenchRoot
    private lateinit var largeTree: BenchRoot

    @Setup(Level.Trial)
    fun setupTrial() {
        val language = KolasuLanguage("com.strumenta.ExportBenchLang").apply {
            addClass(BenchRoot::class)
            addClass(BenchContainerNode::class)
            addClass(BenchLeafNode::class)
        }
        converter = LionWebModelConverter()
        converter.exportLanguageToLionWeb(language)

        smallTree = buildKolasuTree(10)
        largeTree = buildKolasuTree(200)
    }

    /** Clear between invocations so each export starts with a fresh nodesMapping. */
    @Setup(Level.Invocation)
    fun resetMapping() {
        converter.clearNodesMapping()
    }

    @Benchmark
    fun exportSmallTree(bh: Blackhole) {
        bh.consume(converter.exportModelToLionWeb(smallTree))
    }

    @Benchmark
    fun exportLargeTree(bh: Blackhole) {
        bh.consume(converter.exportModelToLionWeb(largeTree))
    }

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

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(ExportModelBenchmark::class.java.simpleName)
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .addProfiler(GCProfiler::class.java)
                .build()
            Runner(options).run()
        }
    }
}
