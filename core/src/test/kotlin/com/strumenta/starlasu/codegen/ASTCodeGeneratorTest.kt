package com.strumenta.starlasu.codegen

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.pos
import com.strumenta.starlasu.transformation.FailingASTTransformation
import com.strumenta.starlasu.transformation.MissingASTTransformation
import com.strumenta.starlasu.traversing.findByDestinationPosition
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ASTCodeGeneratorTest {
    @Test
    fun printSimpleKotlinExpression() {
        val ex =
            KMethodCallExpression(
                KThisExpression(),
                ReferenceByName("myMethod"),
                mutableListOf(KStringLiteral("abc"), KIntLiteral(123), KStringLiteral("qer")),
            )
        val code = KotlinPrinter().printToString(ex)
        assertEquals("""this.myMethod("abc", 123, "qer")""", code)
    }

    @Test
    fun printSimpleFile() {
        val cu =
            KCompilationUnit(
                KPackageDecl("my.splendid.packag"),
                mutableListOf(KImport("my.imported.stuff")),
                mutableListOf(KFunctionDeclaration("foo")),
            )
        val code = KotlinPrinter().printToString(cu)
        assertEquals(
            """package my.splendid.packag
            |
            |import my.imported.stuff
            |
            |
            |fun foo() {
            |}
            |
            """.trimMargin(),
            code,
        )
    }

    @Test
    fun printUsingNodePrinterOverrider() {
        val ex =
            KMethodCallExpression(
                KThisExpression(),
                ReferenceByName("myMethod"),
                mutableListOf(KStringLiteral("abc"), KIntLiteral(123), KStringLiteral("qer")),
            )
        val code = KotlinPrinter().printToString(ex)
        assertEquals("""this.myMethod("abc", 123, "qer")""", code)

        val codeWithNodePrinterOverrider =
            KotlinPrinter()
                .also {
                    it.nodePrinterOverrider = { n: Node ->
                        when (n) {
                            is KStringLiteral -> NodePrinter { output, ast -> output.print("YYY") }
                            is KIntLiteral -> NodePrinter { output, ast -> output.print("XXX") }
                            else -> null
                        }
                    }
                }.printToString(ex)
        assertEquals("""this.myMethod(YYY, XXX, YYY)""", codeWithNodePrinterOverrider)
    }

    @Test
    fun printUntranslatedNodes() {
        val failedNode = KImport("my.imported.stuff")
        failedNode.origin = MissingASTTransformation(failedNode)
        val cu =
            KCompilationUnit(
                KPackageDecl("my.splendid.packag"),
                mutableListOf(failedNode),
                mutableListOf(KFunctionDeclaration("foo")),
            )
        val code = KotlinPrinter().printToString(cu)
        assertEquals(
            """package my.splendid.packag
            |
            |/* Translation of a node is not yet implemented: KImport */
            |
            |fun foo() {
            |}
            |
            """.trimMargin(),
            code,
        )
    }

    @Test
    fun printTransformationFailure() {
        val failedNode = KImport("my.imported.stuff")
        failedNode.origin = FailingASTTransformation(failedNode, "Something made BOOM!")
        val cu =
            KCompilationUnit(
                KPackageDecl("my.splendid.packag"),
                mutableListOf(failedNode),
                mutableListOf(KFunctionDeclaration("foo")),
            )
        val code = KotlinPrinter().printToString(cu)
        assertEquals(
            """package my.splendid.packag
            |
            |/* Something made BOOM! */
            |
            |fun foo() {
            |}
            |
            """.trimMargin(),
            code,
        )
    }

    @Test
    fun findByDestinationPositionAfterPrinting() {
        val abc = KStringLiteral("abc")
        val n123 = KIntLiteral(123)
        val ex =
            KMethodCallExpression(
                KThisExpression(),
                ReferenceByName("myMethod"),
                mutableListOf(abc, n123, KStringLiteral("qer")),
            )
        val code = KotlinPrinter().printToString(ex)
        assertEquals("""this.myMethod("abc", 123, "qer")""", code)

        assertEquals(n123, ex.findByDestinationPosition(pos(1, 22, 1, 23)))
        assertEquals(abc, ex.findByDestinationPosition(pos(1, 15, 1, 18)))
        assertEquals(ex, ex.findByDestinationPosition(pos(1, 13, 1, 14)))
        assertNull(ex.findByDestinationPosition(pos(2, 0, 2, 1)))
    }
}
