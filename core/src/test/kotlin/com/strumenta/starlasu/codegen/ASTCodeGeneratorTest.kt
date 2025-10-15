package com.strumenta.starlasu.codegen

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.transformation.FailingASTTransformation
import com.strumenta.starlasu.transformation.MissingASTTransformation
import org.junit.Test
import kotlin.test.assertEquals

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
}
