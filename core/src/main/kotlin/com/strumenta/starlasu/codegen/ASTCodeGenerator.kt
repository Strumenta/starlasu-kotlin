package com.strumenta.starlasu.codegen

import com.strumenta.starlasu.model.Node
import java.io.File
import kotlin.reflect.KClass

/**
 * Transforms an AST into code. This can be done on an AST obtained from parsing, or built programmatically.
 * It would work also on AST obtained from parsing and then modified. It should be noted that it does not perform
 * lexical preservation: comments are lost, whitespace is re-organized. It is effectively equivalent to auto-formatting.
 *
 * The logic for printing the different elements of the language must be defined in subclasses. This logic could be
 * potentially expressed in a DSL, with multi-platform generators. It would permit to have code generators usable from
 * all the Starlasu platforms.
 */
abstract class ASTCodeGenerator<R : Node> {
    protected val nodePrinters: MutableMap<KClass<*>, NodePrinter> = HashMap()
    protected open val placeholderNodePrinter: NodePrinter?
        get() = null
    var nodePrinterOverrider: (node: Node) -> NodePrinter? = { _ -> null }

    protected abstract fun registerRecordPrinters()

    init {
        registerRecordPrinters()
    }

    protected inline fun <reified N1 : Node> recordPrinter(crossinline generation: PrinterOutput.(ast: N1) -> Unit) {
        nodePrinters[N1::class] =
            NodePrinter { output: PrinterOutput, ast: Node ->
                output.generation(ast as N1)
            }
    }

    fun printToString(ast: R): String {
        val printerOutput = PrinterOutput(this.nodePrinters, nodePrinterOverrider, placeholderNodePrinter)
        configurePrinter(printerOutput)
        return printerOutput.apply { this.print(ast, prefix, postfix) }.text()
    }

    fun printToFile(
        root: R,
        file: File,
    ) {
        file.writeText(printToString(root))
    }

    protected open fun configurePrinter(printerOutput: PrinterOutput) {}

    open val prefix: String get() = ""
    open val postfix: String get() = ""
}
