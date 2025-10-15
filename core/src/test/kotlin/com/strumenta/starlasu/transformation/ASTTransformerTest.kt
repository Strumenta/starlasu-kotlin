package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.mapping.translateCasted
import com.strumenta.starlasu.mapping.translateList
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.children
import com.strumenta.starlasu.model.hasValidParents
import com.strumenta.starlasu.model.withOrigin
import com.strumenta.starlasu.testing.assertASTsAreEqual
import com.strumenta.starlasu.traversing.walkDescendants
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueSeverity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

data class CU(
    var statements: List<Node> = listOf(),
) : Node()

abstract class Statement : Node()

data class DisplayIntStatement(
    val value: Int,
) : Statement()

data class SetStatement(
    var variable: String = "",
    val value: Int = 0,
) : Statement()

enum class Operator {
    PLUS,
    MULT,
}

sealed class Expression : Node()

data class IntLiteral(
    val value: Int,
) : Expression()

data class GenericBinaryExpression(
    val operator: Operator,
    val left: Expression,
    val right: Expression,
) : Node()

data class Mult(
    val left: Expression,
    val right: Expression,
) : Node()

data class Sum(
    val left: Expression,
    val right: Expression,
) : Node()

sealed class ALangExpression : Node()

data class ALangIntLiteral(
    val value: Int,
) : ALangExpression()

data class ALangSum(
    val left: ALangExpression,
    val right: ALangExpression,
) : ALangExpression()

data class ALangMult(
    val left: ALangExpression,
    val right: ALangExpression,
) : ALangExpression()

sealed class BLangExpression : Node()

data class BLangIntLiteral(
    val value: Int,
) : BLangExpression()

data class BLangSum(
    val left: BLangExpression,
    val right: BLangExpression,
) : BLangExpression()

data class BLangMult(
    val left: BLangExpression,
    val right: BLangExpression,
) : BLangExpression()

enum class Type {
    INT,
    STR,
}

sealed class TypedExpression(
    open var type: Type? = null,
) : Node()

data class TypedLiteral(
    var value: String,
    override var type: Type?,
) : TypedExpression(type)

data class TypedSum(
    var left: TypedExpression,
    var right: TypedExpression,
    override var type: Type? = null,
) : TypedExpression(type)

data class TypedConcat(
    var left: TypedExpression,
    var right: TypedExpression,
    override var type: Type? = null,
) : TypedExpression(type)

class ASTTransformerTest {
    @Test
    fun testIdentitiyTransformer() {
        val transformer = ASTTransformer()
        transformer
            .registerNodeFactory(CU::class, CU::class)
            .withChild(CU::statements, CU::statements)
        transformer.registerIdentityTransformation(DisplayIntStatement::class)
        transformer.registerIdentityTransformation(SetStatement::class)

        val cu =
            CU(
                statements =
                    listOf(
                        SetStatement(variable = "foo", value = 123),
                        DisplayIntStatement(value = 456),
                    ),
            )
        val transformedCU = transformer.transform(cu)!!
        assertASTsAreEqual(cu, transformedCU, considerPosition = true)
        assertTrue { transformedCU.hasValidParents() }
        assertEquals(transformedCU.origin, cu)
    }

    /**
     * Example of transformation to perform a refactoring within the same language.
     */
    @Test
    fun translateBinaryExpression() {
        val myTransformer =
            ASTTransformer(allowGenericNode = false).apply {
                registerNodeFactory(GenericBinaryExpression::class) { source: GenericBinaryExpression ->
                    when (source.operator) {
                        Operator.MULT ->
                            Mult(
                                transform(source.left) as Expression,
                                transform(source.right) as Expression,
                            )

                        Operator.PLUS ->
                            Sum(
                                transform(source.left) as Expression,
                                transform(source.right) as Expression,
                            )
                    }
                }
                registerIdentityTransformation(IntLiteral::class)
            }
        assertASTsAreEqual(
            Mult(IntLiteral(7), IntLiteral(8)),
            myTransformer.transform(GenericBinaryExpression(Operator.MULT, IntLiteral(7), IntLiteral(8)))!!,
        )
        assertASTsAreEqual(
            Sum(IntLiteral(7), IntLiteral(8)),
            myTransformer.transform(GenericBinaryExpression(Operator.PLUS, IntLiteral(7), IntLiteral(8)))!!,
        )
    }

    /**
     * Example of transformation to perform a translation to another language.
     */
    @Test
    fun translateAcrossLanguages() {
        val myTransformer =
            ASTTransformer(allowGenericNode = false).apply {
                registerNodeFactory(ALangIntLiteral::class) { source: ALangIntLiteral -> BLangIntLiteral(source.value) }
                registerNodeFactory(ALangSum::class) { source: ALangSum ->
                    BLangSum(
                        transform(source.left) as BLangExpression,
                        transform(source.right) as BLangExpression,
                    )
                }
                registerNodeFactory(ALangMult::class) { source: ALangMult ->
                    BLangMult(
                        transform(source.left) as BLangExpression,
                        transform(source.right) as BLangExpression,
                    )
                }
            }
        assertASTsAreEqual(
            BLangMult(
                BLangSum(
                    BLangIntLiteral(1),
                    BLangMult(BLangIntLiteral(2), BLangIntLiteral(3)),
                ),
                BLangIntLiteral(4),
            ),
            myTransformer.transform(
                ALangMult(
                    ALangSum(
                        ALangIntLiteral(1),
                        ALangMult(ALangIntLiteral(2), ALangIntLiteral(3)),
                    ),
                    ALangIntLiteral(4),
                ),
            )!!,
        )
    }

    /**
     * Example of transformation to perform a simple type calculation.
     */
    @Test
    fun computeTypes() {
        val myTransformer =
            ASTTransformer(allowGenericNode = false).apply {
                registerIdentityTransformation(TypedSum::class).withFinalizer {
                    if (it.left.type == Type.INT && it.right.type == Type.INT) {
                        it.type = Type.INT
                    } else {
                        addIssue(
                            "Illegal types for sum operation. Only integer values are allowed. " +
                                "Found: (${it.left.type?.name ?: "null"}, ${it.right.type?.name ?: "null"})",
                            IssueSeverity.ERROR,
                            it.position,
                        )
                    }
                }
                registerIdentityTransformation(TypedConcat::class).withFinalizer {
                    if (it.left.type == Type.STR && it.right.type == Type.STR) {
                        it.type = Type.STR
                    } else {
                        addIssue(
                            "Illegal types for concat operation. Only string values are allowed. " +
                                "Found: (${it.left.type?.name ?: "null"}, ${it.right.type?.name ?: "null"})",
                            IssueSeverity.ERROR,
                            it.position,
                        )
                    }
                }
                registerIdentityTransformation(TypedLiteral::class)
            }
        // sum - legal
        assertASTsAreEqual(
            TypedSum(
                TypedLiteral("1", Type.INT),
                TypedLiteral("1", Type.INT),
                Type.INT,
            ),
            myTransformer.transform(
                TypedSum(
                    TypedLiteral("1", Type.INT),
                    TypedLiteral("1", Type.INT),
                ),
            )!!,
        )
        assertEquals(0, myTransformer.issues.size)
        // concat - legal
        assertASTsAreEqual(
            TypedConcat(
                TypedLiteral("test", Type.STR),
                TypedLiteral("test", Type.STR),
                Type.STR,
            ),
            myTransformer.transform(
                TypedConcat(
                    TypedLiteral("test", Type.STR),
                    TypedLiteral("test", Type.STR),
                ),
            )!!,
        )
        assertEquals(0, myTransformer.issues.size)
        // sum - error
        assertASTsAreEqual(
            TypedSum(
                TypedLiteral("1", Type.INT),
                TypedLiteral("test", Type.STR),
                null,
            ),
            myTransformer.transform(
                TypedSum(
                    TypedLiteral("1", Type.INT),
                    TypedLiteral("test", Type.STR),
                ),
            )!!,
        )
        assertEquals(1, myTransformer.issues.size)
        assertEquals(
            Issue.semantic(
                "Illegal types for sum operation. Only integer values are allowed. Found: (INT, STR)",
                IssueSeverity.ERROR,
            ),
            myTransformer.issues[0],
        )
        // concat - error
        assertASTsAreEqual(
            TypedConcat(
                TypedLiteral("1", Type.INT),
                TypedLiteral("test", Type.STR),
                null,
            ),
            myTransformer.transform(
                TypedConcat(
                    TypedLiteral("1", Type.INT),
                    TypedLiteral("test", Type.STR),
                ),
            )!!,
        )
        assertEquals(2, myTransformer.issues.size)
        assertEquals(
            Issue.semantic(
                "Illegal types for concat operation. Only string values are allowed. Found: (INT, STR)",
                IssueSeverity.ERROR,
            ),
            myTransformer.issues[1],
        )
    }

    @Test
    fun testDroppingNodes() {
        val transformer = ASTTransformer()
        transformer
            .registerNodeFactory(CU::class, CU::class)
            .withChild(CU::statements, CU::statements)
        transformer.registerNodeFactory(DisplayIntStatement::class) { _ -> null }
        transformer.registerIdentityTransformation(SetStatement::class)

        val cu =
            CU(
                statements =
                    listOf(
                        DisplayIntStatement(value = 456),
                        SetStatement(variable = "foo", value = 123),
                    ),
            )
        val transformedCU = transformer.transform(cu)!! as CU
        assertTrue { transformedCU.hasValidParents() }
        assertEquals(transformedCU.origin, cu)
        assertEquals(1, transformedCU.statements.size)
        assertASTsAreEqual(cu.statements[1], transformedCU.statements[0])
    }

    @Test
    fun testNestedOrigin() {
        val transformer = ASTTransformer()
        transformer
            .registerNodeFactory(CU::class, CU::class)
            .withChild(CU::statements, CU::statements)
        transformer.registerNodeFactory(DisplayIntStatement::class) { s ->
            s.withOrigin(GenericNode())
        }

        val cu =
            CU(
                statements =
                    listOf(
                        DisplayIntStatement(value = 456),
                    ),
            )
        val transformedCU = transformer.transform(cu)!! as CU
        assertTrue { transformedCU.hasValidParents() }
        assertEquals(transformedCU.origin, cu)
        assertIs<GenericNode>(transformedCU.statements[0].origin)
    }

    @Test
    fun testTransformingOneNodeToMany() {
        val transformer = ASTTransformer()
        transformer
            .registerNodeFactory(BarRoot::class, BazRoot::class)
            .withChild(BazRoot::stmts, BarRoot::stmts)
        transformer.registerMultipleNodeFactory(BarStmt::class) { s ->
            listOf(BazStmt("${s.desc}-1"), BazStmt("${s.desc}-2"))
        }

        val original =
            BarRoot(
                stmts =
                    mutableListOf(
                        BarStmt("a"),
                        BarStmt("b"),
                    ),
            )
        val transformed = transformer.transform(original) as BazRoot
        assertTrue { transformed.hasValidParents() }
        assertEquals(transformed.origin, original)
        assertASTsAreEqual(
            BazRoot(
                mutableListOf(
                    BazStmt("a-1"),
                    BazStmt("a-2"),
                    BazStmt("b-1"),
                    BazStmt("b-2"),
                ),
            ),
            transformed,
        )
    }

    @Test
    fun testUnmappedNode() {
        val transformer1 = ASTTransformer(allowGenericNode = false)
        transformer1
            .registerNodeFactory(BarRoot::class, BazRoot::class)
            .withChild(BazRoot::stmts) { stmts }
        val original =
            BarRoot(
                stmts =
                    mutableListOf(
                        BarStmt("a"),
                        BarStmt("b"),
                    ),
            )
        val bazRoot1 = transformer1.transform(original) as BazRoot
        assertASTsAreEqual(
            BazRoot(
                mutableListOf(
                    BazStmt(null),
                    BazStmt(null),
                ),
            ),
            bazRoot1,
        )
        assertIs<MissingASTTransformation>(bazRoot1.stmts[0].origin)
    }

    @Test
    fun testIdentityTransformation() {
        val transformer = ASTTransformer(defaultTransformation = IDENTTITY_TRANSFORMATION)

        val original =
            BarRoot(
                stmts =
                    mutableListOf(
                        BarStmt("a"),
                        BarStmt("b"),
                    ),
            )
        val transformed = transformer.transform(original) as Node
        assertASTsAreEqual(
            original,
            transformed,
        )
    }

    @Test
    fun testPartialIdentityTransformation() {
        val transformer1 = ASTTransformer(defaultTransformation = IDENTTITY_TRANSFORMATION)
        transformer1.registerNodeFactory(BarRoot::class) { original: BarRoot, astTransformer: ASTTransformer, _ ->
            FooRoot(
                desc = "#children = ${original.children.size}",
                stmts = astTransformer.translateList(original.stmts),
            )
        }
        val original =
            BarRoot(
                stmts =
                    mutableListOf(
                        BarStmt("a"),
                        BarStmt("b"),
                    ),
            )
        val transformed = transformer1.transform(original) as FooRoot
        assertASTsAreEqual(
            FooRoot(
                "#children = 2",
                mutableListOf(
                    BarStmt("a"),
                    BarStmt("b"),
                ),
            ),
            transformed,
        )
    }

    @Test
    fun testIdentityTransformationOfIntermediateNodes() {
        val transformer1 = ASTTransformer(defaultTransformation = IDENTTITY_TRANSFORMATION)
        transformer1.registerNodeFactory(BarRoot::class) { original: BarRoot, astTransformer: ASTTransformer, _ ->
            FooRoot(
                desc = "#children = ${original.children.size}",
                stmts = astTransformer.translateList(original.stmts),
            )
        }
        val original =
            AA(
                a = "my_a",
                child =
                    AB(
                        b = "my_b",
                        child =
                            AC(
                                c = "my_c",
                                children =
                                    mutableListOf(
                                        AD("my_d1"),
                                        AD("my_d2"),
                                        AD("my_d3"),
                                    ),
                            ),
                    ),
            )
        // All identity
        assertASTsAreEqual(
            AA(
                a = "my_a",
                child =
                    AB(
                        b = "my_b",
                        child =
                            AC(
                                c = "my_c",
                                children =
                                    mutableListOf(
                                        AD("my_d1"),
                                        AD("my_d2"),
                                        AD("my_d3"),
                                    ),
                            ),
                    ),
            ),
            transformer1.transform(original) as AA,
        )
        // All identity besides AA
        transformer1.registerNodeFactory(AA::class) { original, t, _ ->
            BA("your_" + original.a.removePrefix("my_"), t.translateCasted(original.child))
        }
        assertASTsAreEqual(
            BA(
                a = "your_a",
                child =
                    AB(
                        b = "my_b",
                        child =
                            AC(
                                c = "my_c",
                                children =
                                    mutableListOf(
                                        AD("my_d1"),
                                        AD("my_d2"),
                                        AD("my_d3"),
                                    ),
                            ),
                    ),
            ),
            transformer1.transform(original) as AA,
        )
        // All identity besides AA and AB
        transformer1.registerNodeFactory(AB::class) { original, t, _ ->
            BB("your_" + original.b.removePrefix("my_"), t.translateCasted(original.child))
        }
        assertASTsAreEqual(
            BA(
                a = "your_a",
                child =
                    BB(
                        b = "your_b",
                        child =
                            AC(
                                c = "my_c",
                                children =
                                    mutableListOf(
                                        AD("my_d1"),
                                        AD("my_d2"),
                                        AD("my_d3"),
                                    ),
                            ),
                    ),
            ),
            transformer1.transform(original) as AA,
        )
        // All identity besides AA and AB and AD
        transformer1.registerNodeFactory(AD::class) { original, t, _ ->
            BD("your_" + original.d.removePrefix("my_"))
        }
        assertASTsAreEqual(
            BA(
                a = "your_a",
                child =
                    BB(
                        b = "your_b",
                        child =
                            AC(
                                c = "my_c",
                                children =
                                    mutableListOf(
                                        BD("your_d1"),
                                        BD("your_d2"),
                                        BD("your_d3"),
                                    ),
                            ),
                    ),
            ),
            transformer1.transform(original) as AA,
        )
    }

    @Test
    fun testIdentityTransformationOfIntermediateNodesWithOrigin() {
        val transformer1 = ASTTransformer(defaultTransformation = IDENTTITY_TRANSFORMATION)
        transformer1.registerNodeFactory(AA::class) { original, t, _ ->
            BA("your_" + original.a.removePrefix("my_"), t.translateCasted(original.child))
        }
        val original =
            AA(
                a = "my_a",
                child =
                    AB(
                        b = "my_b",
                        child =
                            AC(
                                c = "my_c",
                                children =
                                    mutableListOf(
                                        AD("my_d1"),
                                    ),
                            ),
                    ),
            )
        val transformedAST = transformer1.transform(original) as BA

        // verify that the origin is set correctly
        assertEquals(transformedAST.origin, original)
        // verify that the descendants have the correct origin as well
        assertEquals(
            transformedAST.walkDescendants(AB::class).first().origin,
            original.walkDescendants(AB::class).first(),
        )
        assertEquals(
            transformedAST.walkDescendants(AC::class).first().origin,
            original.walkDescendants(AC::class).first(),
        )
    }
}

data class BazRoot(
    var stmts: MutableList<BazStmt> = mutableListOf(),
) : Node()

data class BazStmt(
    val desc: String? = null,
) : Node()

data class BarRoot(
    var stmts: MutableList<BarStmt> = mutableListOf(),
) : Node()

data class BarStmt(
    val desc: String,
) : Node()

data class FooRoot(
    var desc: String,
    var stmts: MutableList<BarStmt> = mutableListOf(),
) : Node()

open class AA(
    var a: String,
    val child: AB,
) : Node()

open class AB(
    var b: String,
    val child: AC,
) : Node()

open class AC(
    var c: String,
    val children: MutableList<AD>,
) : Node()

open class AD(
    var d: String,
) : Node()

class BA(
    a: String,
    child: AB,
) : AA(a, child)

class BB(
    b: String,
    child: AC,
) : AB(b, child)

class BD(
    d: String,
) : AD(d)
