package com.strumenta.starlasu.codegen

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.START_POINT
import com.strumenta.starlasu.model.TextFileDestination
import com.strumenta.starlasu.transformation.PlaceholderASTTransformation
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

/**
 * Know how to print a single node type.
 */
fun interface NodePrinter {
    fun print(
        output: PrinterOutput,
        ast: Node,
    )
}

/**
 * This provides a mechanism to generate code tracking indentation, handling lists, and providing other facilities.
 * This is used in the implementation of NodePrinter and in ASTCodeGenerator.
 */
class PrinterOutput(
    private val nodePrinters: Map<KClass<*>, NodePrinter>,
    private var nodePrinterOverrider: (node: Node) -> NodePrinter? = { _ -> null },
    private val placeholderNodePrinter: NodePrinter? = null,
) {
    private val sb = StringBuilder()
    private var currentPoint = START_POINT
    private var indentationLevel = 0
    private var onNewLine = true
    private var indentationBlock = "    "
    private var newLineStr = "\n"

    fun text() = sb.toString()

    fun println() {
        println("")
    }

    fun println(text: String = "") {
        print(text)
        sb.append(newLineStr)
        currentPoint += newLineStr
        onNewLine = true
    }

    fun printFlag(
        flag: Boolean,
        text: String,
    ) {
        if (flag) {
            print(text)
        }
    }

    fun print(
        text: String,
        allowMultiLine: Boolean = false,
    ) {
        if (text.isEmpty()) {
            return
        }
        var adaptedText = text
        val needPrintln = adaptedText.endsWith("\n")
        if (needPrintln) {
            adaptedText = adaptedText.removeSuffix("\n")
        }
        if (!needPrintln || text.isNotBlank()) {
            considerIndentation()
        }
        require(adaptedText.lines().size < 2 || allowMultiLine) { "Given text span multiple lines: $adaptedText" }
        sb.append(adaptedText)
        currentPoint += adaptedText
        if (needPrintln) {
            println()
        }
    }

    fun ensureSpace() {
        val text = this.text()
        if (text.isEmpty()) {
            return
        }
        if (!text.last().isWhitespace()) {
            print(" ")
        }
    }

    fun <T : Node> printList(
        prefix: String,
        elements: List<T>,
        postfix: String,
        printEvenIfEmpty: Boolean = false,
        separatorLogic: (PrinterOutput.() -> Unit),
    ) {
        val elementPrinter: (T) -> Unit = { el -> print(el) }
        if (elements.isNotEmpty() || printEvenIfEmpty) {
            print(prefix)
            printList(elements, separatorLogic, elementPrinter)
            print(postfix)
        }
    }

    fun <T : Node> printList(
        elements: List<T>,
        separatorLogic: (PrinterOutput.() -> Unit),
        elementPrinter: (T) -> Unit,
    ) {
        var i = 0
        while (i < elements.size) {
            if (i != 0) {
                separatorLogic.invoke(this)
            }
            elementPrinter(elements[i])
            i += 1
        }
    }

    fun print(text: Char) {
        this.print(text.toString())
    }

    fun print(value: Int) {
        this.print(value.toString())
    }

    private fun considerIndentation() {
        if (onNewLine) {
            onNewLine = false
            (1..(indentationLevel)).forEach {
                print(indentationBlock)
            }
        }
    }

    fun print(
        text: String?,
        prefix: String = "",
        postfix: String = "",
    ) {
        if (text == null) {
            return
        }
        print(prefix)
        print(text)
        print(postfix)
    }

    private fun findPrinter(
        ast: Node,
        kclass: KClass<*>,
    ): NodePrinter? {
        val overrider = nodePrinterOverrider(ast)
        if (overrider != null) {
            return overrider
        }
        val properPrinter =
            if (ast.origin is PlaceholderASTTransformation && placeholderNodePrinter != null) {
                placeholderNodePrinter
            } else {
                nodePrinters[kclass]
            }
        if (properPrinter != null) {
            return properPrinter
        }
        val superclass = kclass.superclasses.filter { !it.java.isInterface }.firstOrNull()
        if (superclass != null) {
            return getPrinter(ast, superclass)
        }
        return null
    }

    private fun getPrinter(
        ast: Node,
        kclass: KClass<*> = ast::class,
    ): NodePrinter {
        val printer = findPrinter(ast, kclass)
        return printer ?: throw java.lang.IllegalArgumentException("Unable to print $ast")
    }

    fun print(
        ast: Node?,
        prefix: String = "",
        postfix: String = "",
    ) {
        if (ast == null) {
            return
        }
        print(prefix)
        val printer = getPrinter(ast)
        associate(ast) {
            printer.print(this, ast)
        }
        print(postfix)
    }

    fun println(
        ast: Node?,
        prefix: String = "",
        postfix: String = "",
    ) {
        print(ast, prefix, postfix + "\n")
    }

    fun printEmptyLine() {
        println()
        println()
    }

    fun indent() {
        indentationLevel++
    }

    fun dedent() {
        indentationLevel--
    }

    fun associate(
        ast: Node,
        generation: PrinterOutput.() -> Unit,
    ) {
        val startPoint = currentPoint
        generation()
        val endPoint = currentPoint
        val nodePositionInGeneratedCode = Position(startPoint, endPoint)
        ast.destination = TextFileDestination(position = nodePositionInGeneratedCode)
    }

    fun <T : Node> printList(
        elements: List<T>,
        separator: String = ", ",
        elementPrinter: (T) -> Unit,
    ) {
        var i = 0
        while (i < elements.size) {
            if (i != 0) {
                print(separator)
            }
            elementPrinter(elements[i])
            i += 1
        }
    }

    fun <T : Node> printList(
        elements: List<T>,
        separator: String = ", ",
    ) {
        printList(elements, separator) { el -> print(el) }
    }

    fun <T : Node> printList(
        prefix: String,
        elements: List<T>,
        postfix: String,
        printEvenIfEmpty: Boolean = false,
        separator: String = ", ",
        elementPrinter: (T) -> Unit,
    ) {
        if (elements.isNotEmpty() || printEvenIfEmpty) {
            print(prefix)
            printList(elements, separator, elementPrinter)
            print(postfix)
        }
    }

    fun <T : Node> printList(
        prefix: String,
        elements: List<T>,
        postfix: String,
        printEvenIfEmpty: Boolean = false,
        separator: String = ", ",
    ) {
        printList(prefix, elements, postfix, printEvenIfEmpty, separator) { el -> print(el) }
    }

    fun printOneOf(vararg alternatives: Node?) {
        val notNull = alternatives.filterNotNull()
        if (notNull.size != 1) {
            throw IllegalStateException(
                "Expected exactly one alternative to be not null. " +
                    "Not null alternatives: $notNull",
            )
        }
        print(notNull.first())
    }
}
