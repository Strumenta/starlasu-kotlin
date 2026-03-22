// JMH dependencies (added to lionweb/build.gradle):
//   kaptJmh "org.openjdk.jmh:jmh-generator-annprocess:1.37"
//   jmhImplementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
//   jmhImplementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
// Plugin: id 'me.champeau.jmh' version '0.7.2'
//
// Run with: ./gradlew :lionweb:jmh

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
import io.lionweb.model.Node as LWNode

/**
 * Benchmarks [LionWebModelConverter.importModelFromLionWeb].
 *
 * The converter is shared across all invocations in a trial so that its internal
 * JIT-friendly caches (kClassCache, factoryCache) are warm, mirroring production.
 * The nodesMapping is flushed between invocations via [LionWebModelConverter.clearNodesMapping]
 * so that each import starts from a clean mapping.
 *
 * Two tree sizes are exercised:
 *   - small : root + 2 containers × 1 leaf = 5 nodes
 *   - large : root + 100 containers × 1 leaf = 201 nodes
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class ImportModelBenchmark {

    private lateinit var converter: LionWebModelConverter
    private lateinit var smallLwTree: LWNode
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

        converter.clearNodesMapping()
        smallLwTree = converter.exportModelToLionWeb(buildKolasuTree(2))
        converter.clearNodesMapping()
        largeLwTree = converter.exportModelToLionWeb(buildKolasuTree(100))
        converter.clearNodesMapping()
    }

    /** Reset the nodesMapping before every invocation; keeps kClassCache/factoryCache warm. */
    @Setup(Level.Invocation)
    fun resetMapping() {
        converter.clearNodesMapping()
    }

    @Benchmark
    fun importSmallTree(bh: Blackhole) {
        bh.consume(converter.importModelFromLionWeb(smallLwTree))
    }

    @Benchmark
    fun importLargeTree(bh: Blackhole) {
        bh.consume(converter.importModelFromLionWeb(largeLwTree))
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
