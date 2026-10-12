package com.strumenta.starlasu.codegen

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrinterOutputTest {
    @Test
    fun currentColumnAndNewLineState() {
        val output = PrinterOutput(emptyMap())
        assertEquals(0, output.currentColumn)
        assertTrue(output.onNewLine)
        assertFalse(output.endsWithNewline())
        output.print("abc")
        assertEquals(3, output.currentColumn)
        assertFalse(output.onNewLine)
        assertFalse(output.endsWithNewline())
        output.println()
        assertEquals(0, output.currentColumn)
        assertTrue(output.onNewLine)
        assertTrue(output.endsWithNewline())
    }

    @Test
    fun trimTrailingNewline() {
        val output = PrinterOutput(emptyMap())
        assertFalse(output.trimTrailingNewline())
        output.println("ab")
        output.println("cde")
        assertTrue(output.trimTrailingNewline())
        assertEquals("ab\ncde", output.text())
        assertEquals(3, output.currentColumn)
        assertFalse(output.onNewLine)
        assertEquals('e', output.lastNonWhitespaceChar())
        assertFalse(output.trimTrailingNewline())
        output.print("f")
        assertEquals("ab\ncdef", output.text())
    }

    @Test
    fun printSeparatorAfterElementEndingWithNewline() {
        val output = PrinterOutput(emptyMap())
        output.println("foo(")
        output.indent()
        output.println("{")
        output.println("}")
        output.printSeparatorAfterElement(",")
        output.print("b")
        output.printSeparatorAfterElement(",")
        output.dedent()
        assertEquals("foo(\n    {\n    },\n    b,", output.text())
    }
}
