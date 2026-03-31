package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
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
 * Benchmarks for [pointDeserializer] and [positionDeserializer].
 *
 * Background: in a 605-second JFR recording of PipelineOrchestratorCLI, pointDeserializer
 * accounted for 1.63 GB of allocations from repeated removePrefix+split calls. The optimized
 * version uses indexOf+substring (avoiding the intermediate List and one extra String).
 *
 * Each @Benchmark call represents one deserialization, mirroring the hot path where every
 * AST node carries a start+end Position (i.e. 2 Points per node).
 *
 * Run with:  ./gradlew :lionweb:jmh
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class PrimitiveSerializationBenchmark {

    // Pre-serialized strings to avoid measuring serialization in the deserialization benchmarks
    private lateinit var pointSmall: String // "L1:0"
    private lateinit var pointLarge: String // "L9999:9999"
    private lateinit var pointTypical: String // "L42:100"
    private lateinit var positionSmall: String // "L1:0-L1:0"
    private lateinit var positionTypical: String // "L3:5-L27:200"
    private lateinit var positionLarge: String // "L9999:9999-L9999:9999"

    // Reference objects for the serializer benchmarks
    private val pointRef = Point(42, 100)
    private val positionRef = Position(Point(3, 5), Point(27, 200))

    @Setup(Level.Trial)
    fun setup() {
        pointSmall = pointSerializer.serialize(Point(1, 0))!!
        pointLarge = pointSerializer.serialize(Point(9999, 9999))!!
        pointTypical = pointSerializer.serialize(Point(42, 100))!!
        positionSmall = positionSerializer.serialize(Position(Point(1, 0), Point(1, 0)))!!
        positionTypical = positionSerializer.serialize(Position(Point(3, 5), Point(27, 200)))!!
        positionLarge = positionSerializer.serialize(Position(Point(9999, 9999), Point(9999, 9999)))!!
    }

    // -------------------------------------------------------------------------
    // Point deserialization
    // -------------------------------------------------------------------------

    @Benchmark
    fun deserializePointSmall(bh: Blackhole) {
        bh.consume(pointDeserializer.deserialize(pointSmall))
    }

    @Benchmark
    fun deserializePointTypical(bh: Blackhole) {
        bh.consume(pointDeserializer.deserialize(pointTypical))
    }

    @Benchmark
    fun deserializePointLarge(bh: Blackhole) {
        bh.consume(pointDeserializer.deserialize(pointLarge))
    }

    // -------------------------------------------------------------------------
    // Position deserialization (exercises both pointDeserializer calls internally)
    // -------------------------------------------------------------------------

    @Benchmark
    fun deserializePositionSmall(bh: Blackhole) {
        bh.consume(positionDeserializer.deserialize(positionSmall))
    }

    @Benchmark
    fun deserializePositionTypical(bh: Blackhole) {
        bh.consume(positionDeserializer.deserialize(positionTypical))
    }

    @Benchmark
    fun deserializePositionLarge(bh: Blackhole) {
        bh.consume(positionDeserializer.deserialize(positionLarge))
    }

    // -------------------------------------------------------------------------
    // Serialization (baseline — should be fast already)
    // -------------------------------------------------------------------------

    @Benchmark
    fun serializePoint(bh: Blackhole) {
        bh.consume(pointSerializer.serialize(pointRef))
    }

    @Benchmark
    fun serializePosition(bh: Blackhole) {
        bh.consume(positionSerializer.serialize(positionRef))
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(PrimitiveSerializationBenchmark::class.java.simpleName)
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .addProfiler(GCProfiler::class.java)
                .build()
            Runner(options).run()
        }
    }
}
