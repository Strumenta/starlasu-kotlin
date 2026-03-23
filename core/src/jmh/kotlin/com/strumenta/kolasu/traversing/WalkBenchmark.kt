package com.strumenta.kolasu.traversing

import com.strumenta.kolasu.model.Node
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
// Minimal AST node types used by the benchmarks
// ---------------------------------------------------------------------------

data class WalkLeaf(val value: String) : Node()

data class WalkContainer(
    val label: String,
    val leaves: MutableList<WalkLeaf> = mutableListOf()
) : Node()

data class WalkRoot(
    val containers: MutableList<WalkContainer> = mutableListOf()
) : Node()

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Builds a tree with [numContainers] containers each holding [leavesPerContainer] leaves. */
private fun buildTree(numContainers: Int, leavesPerContainer: Int): WalkRoot {
    val root = WalkRoot()
    repeat(numContainers) { ci ->
        val container = WalkContainer("c$ci")
        repeat(leavesPerContainer) { li ->
            container.leaves.add(WalkLeaf("v$ci-$li"))
        }
        root.containers.add(container)
    }
    return root
}

// ---------------------------------------------------------------------------
// Benchmark state
// ---------------------------------------------------------------------------

@State(Scope.Thread)
open class WalkBenchmarkState {

    /** Small tree: 1 root + 3 containers + 4 leaves = 8 nodes */
    lateinit var smallRoot: WalkRoot

    /** Large tree: 1 root + 20 containers + 15 leaves each = 301 nodes */
    lateinit var largeRoot: WalkRoot

    @Setup(Level.Trial)
    fun setup() {
        smallRoot = buildTree(numContainers = 3, leavesPerContainer = 4)
        largeRoot = buildTree(numContainers = 20, leavesPerContainer = 15)
    }
}

// ---------------------------------------------------------------------------
// Benchmarks
// ---------------------------------------------------------------------------

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
open class WalkBenchmark {

    private val state = WalkBenchmarkState()

    @Setup(Level.Trial)
    fun setup() = state.setup()

    // -- walk() ---------------------------------------------------------------

    @Benchmark
    fun walkSmall(bh: Blackhole) {
        for (node in state.smallRoot.walk()) bh.consume(node)
    }

    @Benchmark
    fun walkLarge(bh: Blackhole) {
        for (node in state.largeRoot.walk()) bh.consume(node)
    }

    // -- children -------------------------------------------------------------

    @Benchmark
    fun childrenSmall(bh: Blackhole) {
        // Measures cost of Node.children on root + all containers
        bh.consume(state.smallRoot.children)
        for (container in state.smallRoot.containers) bh.consume(container.children)
    }

    @Benchmark
    fun childrenLarge(bh: Blackhole) {
        bh.consume(state.largeRoot.children)
        for (container in state.largeRoot.containers) bh.consume(container.children)
    }

    // -- walkLeavesFirst() ----------------------------------------------------

    @Benchmark
    fun walkLeavesFirstSmall(bh: Blackhole) {
        for (node in state.smallRoot.walkLeavesFirst()) bh.consume(node)
    }

    @Benchmark
    fun walkLeavesFirstLarge(bh: Blackhole) {
        for (node in state.largeRoot.walkLeavesFirst()) bh.consume(node)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val opt = OptionsBuilder()
                .include(WalkBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()
            Runner(opt).run()
        }
    }
}
