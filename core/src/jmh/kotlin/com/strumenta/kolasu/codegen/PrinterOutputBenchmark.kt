package com.strumenta.kolasu.codegen

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.ReferenceByName
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
import java.io.File
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Minimal AST node hierarchy for the printer benchmarks
// ---------------------------------------------------------------------------

data class BenchStatement(val text: String) : Node()
data class BenchFunction(val name: String, val body: MutableList<BenchStatement> = mutableListOf()) : Node()
data class BenchClass(val name: String, val functions: MutableList<BenchFunction> = mutableListOf()) : Node()
data class BenchFile(val pkg: String, val classes: MutableList<BenchClass> = mutableListOf()) : Node()

class BenchPrinter : ASTCodeGenerator<BenchFile>() {
    override fun registerRecordPrinters() {
        recordPrinter<BenchFile> { f ->
            print("package ${f.pkg}")
            println()
            f.classes.forEach { print(it) }
        }
        recordPrinter<BenchClass> { c ->
            println("class ${c.name} {")
            indent()
            c.functions.forEach { print(it) }
            dedent()
            println("}")
        }
        recordPrinter<BenchFunction> { fn ->
            println("fun ${fn.name}() {")
            indent()
            fn.body.forEach { print(it) }
            dedent()
            println("}")
        }
        recordPrinter<BenchStatement> { s ->
            println(s.text)
        }
    }
}

// ---------------------------------------------------------------------------
// Benchmark state
// ---------------------------------------------------------------------------

/**
 * Benchmarks [ASTCodeGenerator.printToString] and [ASTCodeGenerator.printToFile].
 *
 * Background: PrinterOutput.text() (sb.toString()) accounted for 14.66 GB of allocations
 * in a 270-second profile, and the underlying byte[] array for 15.52 GB.
 *
 * Two optimisations are exercised here:
 *  1. initialCapacity: pre-allocating the StringBuilder avoids repeated array doublings.
 *  2. printToFile via writeTo(Writer): writes directly from StringBuilder to file via
 *     writer.append(sb), skipping the intermediate String allocation.
 *
 * Two tree sizes mirror "small fragment" vs "full file" usage patterns:
 *  - small : 1 class, 2 functions, 5 statements each  (~200 chars output)
 *  - large : 5 classes, 10 functions, 20 statements each (~5 KB output)
 *
 * Run with: ./gradlew :core:jmh
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class PrinterOutputBenchmark {

    private val printer = BenchPrinter()
    private lateinit var smallAst: BenchFile
    private lateinit var largeAst: BenchFile
    private lateinit var tempFile: File

    @Setup(Level.Trial)
    fun setup() {
        smallAst = buildAst(classCount = 1, functionsPerClass = 2, statementsPerFunction = 5)
        largeAst = buildAst(classCount = 5, functionsPerClass = 10, statementsPerFunction = 20)
        tempFile = File.createTempFile("kolasu_bench_printer", ".txt").also { it.deleteOnExit() }
    }

    // -------------------------------------------------------------------------
    // printToString — small AST
    // -------------------------------------------------------------------------

    @Benchmark
    fun printToStringSmallDefaultCapacity(bh: Blackhole) {
        bh.consume(printer.printToString(smallAst))
    }

    @Benchmark
    fun printToStringSmallSmallCapacity(bh: Blackhole) {
        // 256 is appropriate for fragment-level printing
        bh.consume(printer.printToString(smallAst, initialCapacity = 256))
    }

    // -------------------------------------------------------------------------
    // printToString — large AST
    // -------------------------------------------------------------------------

    @Benchmark
    fun printToStringLargeDefaultCapacity(bh: Blackhole) {
        bh.consume(printer.printToString(largeAst))
    }

    @Benchmark
    fun printToStringLargeLargeCapacity(bh: Blackhole) {
        // 8192 fits a typical Java/Kotlin file without reallocations
        bh.consume(printer.printToString(largeAst, initialCapacity = 8192))
    }

    // -------------------------------------------------------------------------
    // printToFile — avoids intermediate String via writeTo(Writer)
    // -------------------------------------------------------------------------

    @Benchmark
    fun printToFileSmall(bh: Blackhole) {
        printer.printToFile(smallAst, tempFile)
        bh.consume(tempFile.length())
    }

    @Benchmark
    fun printToFileLarge(bh: Blackhole) {
        printer.printToFile(largeAst, tempFile)
        bh.consume(tempFile.length())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildAst(classCount: Int, functionsPerClass: Int, statementsPerFunction: Int): BenchFile {
        val classes = (1..classCount).map { ci ->
            BenchClass(
                name = "Class$ci",
                functions = (1..functionsPerClass).map { fi ->
                    BenchFunction(
                        name = "method${fi}In$ci",
                        body = (1..statementsPerFunction).map { si ->
                            BenchStatement("val x$si = compute($ci, $fi, $si)")
                        }.toMutableList()
                    )
                }.toMutableList()
            )
        }.toMutableList()
        return BenchFile(pkg = "com.strumenta.bench", classes = classes)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(PrinterOutputBenchmark::class.java.simpleName)
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .addProfiler(GCProfiler::class.java)
                .build()
            Runner(options).run()
        }
    }
}
