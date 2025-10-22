package com.strumenta.starlasu.mapping

import com.strumenta.simplelang.AntlrEntityLexer
import com.strumenta.simplelang.AntlrEntityParser
import com.strumenta.simplelang.AntlrScriptLexer
import com.strumenta.simplelang.AntlrScriptParser
import com.strumenta.simplelang.SimpleLangLexer
import com.strumenta.simplelang.SimpleLangParser
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.children
import com.strumenta.starlasu.model.hasValidParents
import com.strumenta.starlasu.model.invalidPositions
import com.strumenta.starlasu.parsing.withParseTreeNode
import com.strumenta.starlasu.testing.assertASTsAreEqual
import com.strumenta.starlasu.transformation.ASTTransformer
import com.strumenta.starlasu.transformation.TrivialFactoryOfParseTreeToASTTransform
import com.strumenta.starlasu.transformation.registerTrivialPTtoASTConversion
import com.strumenta.starlasu.transformation.unwrap
import com.strumenta.starlasu.traversing.walk
import com.strumenta.starlasu.validation.Issue
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.dfa.DFA
import org.junit.Test
import java.util.BitSet
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

data class CU(
    val specifiedPosition: Position? = null,
    var statements: List<Node> = listOf(),
) : Node(specifiedPosition)

data class DisplayIntStatement(
    val specifiedPosition: Position? = null,
    val value: Int,
) : Node(specifiedPosition)

data class SetStatement(
    val specifiedPosition: Position? = null,
    var variable: String = "",
    val value: Int = 0,
) : Node(specifiedPosition)

data class EModule(
    override val name: String,
    val entities: MutableList<EEntity>,
) : Node(),
    Named

data class EEntity(
    override val name: String,
    val features: MutableList<EFeature>,
) : Node(),
    Named

data class EFeature(
    override val name: String,
    val type: EType,
) : Node(),
    Named

sealed class EType : Node()

class EStringType : EType()

class EBooleanType : EType()

data class EEntityRefType(
    val entity: ReferenceByName<EEntity>,
) : EType()

data class SScript(
    val statements: MutableList<SStatement>,
) : Node()

sealed class SStatement : Node()

data class SCreateStatement(
    val entity: ReferenceByName<EEntity>,
    val name: String? = null,
) : SStatement()

data class SSetStatement(
    val feature: ReferenceByName<EFeature>,
    val instance: SExpression,
    val value: SExpression,
) : SStatement()

data class SPrintStatement(
    val message: SExpression,
) : SStatement()

sealed class SExpression : Node()

data class SStringLiteral(
    val value: String,
) : SExpression()

data class SIntegerLiteral(
    val value: Int,
) : SExpression()

data class SDivision(
    val left: SExpression,
    val right: SExpression,
) : SExpression()

data class SSubtraction(
    val left: SExpression,
    val right: SExpression,
) : SExpression()

data class SMultiplication(
    val left: SExpression,
    val right: SExpression,
) : SExpression()

data class SSum(
    val left: SExpression,
    val right: SExpression,
) : SExpression()

data class SConcat(
    val left: SExpression,
    val right: SExpression,
) : SExpression()

data class SFeatureAccess(
    val feature: ReferenceByName<EFeature>,
    val container: SExpression,
) : SExpression()

data class SInstanceById(
    val entity: ReferenceByName<EEntity>,
    val index: SExpression,
) : SExpression()

class ParseTreeToASTTransformerTest {
    @Test
    fun testParseTreeTransformer() {
        val code = "set foo = 123\ndisplay 456"
        val lexer = SimpleLangLexer(CharStreams.fromString(code))
        val parser = SimpleLangParser(CommonTokenStream(lexer))
        val pt = parser.compilationUnit()

        val transformer = ParseTreeToASTTransformer()
        configure(transformer)

        val cu =
            CU(
                statements =
                    listOf(
                        SetStatement(variable = "foo", value = 123).withParseTreeNode(pt.statement(0)),
                        DisplayIntStatement(value = 456).withParseTreeNode(pt.statement(1)),
                    ),
            ).withParseTreeNode(pt)
        val transformedCU = transformer.transform(pt)!!
        assertASTsAreEqual(cu, transformedCU, considerPosition = true)
        assertTrue { transformedCU.hasValidParents() }
        assertNull(transformedCU.invalidPositions().firstOrNull())
    }

    @Test
    fun testTransformationWithErrors() {
        val code = "set foo = \ndisplay @@@"
        val lexer = SimpleLangLexer(CharStreams.fromString(code))
        val parser = SimpleLangParser(CommonTokenStream(lexer))
        val pt = parser.compilationUnit()
        assertEquals(2, parser.numberOfSyntaxErrors)

        val transformer = ParseTreeToASTTransformer()
        configure(transformer)

        val cu =
            CU(
                statements =
                    listOf(
                        SetStatement(variable = "DUMMY").withParseTreeNode(pt.statement(0)),
                        DisplayIntStatement(value = 0).withParseTreeNode(pt.statement(1)),
                    ),
            ).withParseTreeNode(pt)
        val transformedCU = transformer.transform(pt)!! as CU
        assertASTsAreEqual(cu, transformedCU, considerPosition = true)
        assertTrue { transformedCU.hasValidParents() }
        assertNull(transformedCU.invalidPositions().firstOrNull())
    }

    @Test
    fun testGenericASTTransformer() {
        val code = "set foo = 123\ndisplay 456"
        val lexer = SimpleLangLexer(CharStreams.fromString(code))
        val parser = SimpleLangParser(CommonTokenStream(lexer))
        val pt = parser.compilationUnit()

        val transformer = ASTTransformer()
        configure(transformer)

        // Compared to ParseTreeToASTTransformer, the base class ASTTransformer does not assign a parse tree node
        // to each AST node
        val cu =
            CU(
                statements =
                    listOf(
                        SetStatement(variable = "foo", value = 123),
                        DisplayIntStatement(value = 456),
                    ),
            )
        val transformedCU = transformer.transform(pt)!!
        assertASTsAreEqual(cu, transformedCU, considerPosition = true)
        assertTrue { transformedCU.hasValidParents() }
    }

    private fun configure(transformer: ASTTransformer) {
        transformer
            .registerTransform(SimpleLangParser.CompilationUnitContext::class, CU::class)
            .withChild(CU::statements, SimpleLangParser.CompilationUnitContext::statement)
        transformer.registerTransform(SimpleLangParser.DisplayStmtContext::class) { ctx ->
            if (ctx.exception != null || ctx.expression().exception != null) {
                // We throw a custom error so that we can check that it's recorded in the AST
                throw IllegalStateException("Parse error")
            }
            DisplayIntStatement(
                value =
                    ctx
                        .expression()
                        .INT_LIT()
                        .text
                        .toInt(),
            )
        }
        transformer.registerTransform(SimpleLangParser.SetStmtContext::class) { ctx ->
            if (ctx.exception != null || ctx.expression().exception != null) {
                // We throw a custom error so that we can check that it's recorded in the AST
                throw IllegalStateException("Parse error")
            }
            SetStatement(
                variable = ctx.ID().text,
                value =
                    ctx
                        .expression()
                        .INT_LIT()
                        .text
                        .toInt(),
            )
        }
    }

    class MyErrorListener : ANTLRErrorListener {
        override fun syntaxError(
            p0: Recognizer<*, *>?,
            p1: Any?,
            line: Int,
            column: Int,
            message: String?,
            p5: RecognitionException?,
        ): Unit = throw RuntimeException("L$line:$column: $message")

        override fun reportAmbiguity(
            p0: Parser?,
            p1: DFA?,
            p2: Int,
            p3: Int,
            p4: Boolean,
            p5: BitSet?,
            p6: ATNConfigSet?,
        ) {
            // nothing to do
        }

        override fun reportAttemptingFullContext(
            p0: Parser?,
            p1: DFA?,
            p2: Int,
            p3: Int,
            p4: BitSet?,
            p5: ATNConfigSet?,
        ) {
            // nothing to do
        }

        override fun reportContextSensitivity(
            p0: Parser?,
            p1: DFA?,
            p2: Int,
            p3: Int,
            p4: Int,
            p5: ATNConfigSet?,
        ) {
            // nothing to do
        }
    }

    private fun parseEntities(code: String): AntlrEntityParser.ModuleContext {
        val errorListener = MyErrorListener()

        val lexer = AntlrEntityLexer(CharStreams.fromString(code))
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val parser = AntlrEntityParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        return parser.module()
    }

    private fun parseScript(code: String): AntlrScriptParser.ScriptContext {
        val errorListener = MyErrorListener()

        val lexer = AntlrScriptLexer(CharStreams.fromString(code))
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val parser = AntlrScriptParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        return parser.script()
    }

    @Test
    fun testSimpleEntitiesTransformer() {
        val transformer = ParseTreeToASTTransformer(allowGenericNode = false)
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.ModuleContext, EModule>()
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.EntityContext, EEntity>()
        val expectedAST =
            EModule(
                "M",
                mutableListOf(
                    EEntity("FOO", mutableListOf()),
                    EEntity("BAR", mutableListOf()),
                ),
            )
        val actualAST =
            transformer.transform(
                parseEntities(
                    """
                    module M {
                       entity FOO { }
                       entity BAR { }
                    }
                    """.trimIndent(),
                ),
            )!!
        assertASTsAreEqual(expectedAST, actualAST)
    }

    @Test
    fun testEntitiesWithFeaturesTransformer() {
        val transformer = ParseTreeToASTTransformer(allowGenericNode = false)
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.ModuleContext, EModule>()
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.EntityContext, EEntity>()
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.FeatureContext, EFeature>()
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.String_typeContext, EStringType>()
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.Boolean_typeContext, EBooleanType>()
        transformer.registerTrivialPTtoASTConversion<AntlrEntityParser.Entity_typeContext, EEntityRefType>(
            "target" to "entity",
        )
        val expectedAST =
            EModule(
                "M",
                mutableListOf(
                    EEntity(
                        "FOO",
                        mutableListOf(
                            EFeature("A", EStringType()),
                            EFeature("B", EBooleanType()),
                        ),
                    ),
                    EEntity(
                        "BAR",
                        mutableListOf(
                            EFeature("C", EEntityRefType(ReferenceByName("FOO"))),
                        ),
                    ),
                ),
            )
        val actualAST =
            transformer.transform(
                parseEntities(
                    """
                    module M {
                       entity FOO {
                        A: string;
                        B: boolean;
                         
                       }
                       entity BAR {
                         C: FOO;
                       }
                    }
                    """.trimIndent(),
                ),
            )!!
        assertASTsAreEqual(expectedAST, actualAST)
    }

    @Test
    fun testScriptTransformer() {
        val transformer = ParseTreeToASTTransformer(allowGenericNode = false)
        transformer.registerTrivialPTtoASTConversion<AntlrScriptParser.ScriptContext, SScript>()
        transformer.registerTrivialPTtoASTConversion<AntlrScriptParser.Create_statementContext, SCreateStatement>(
            AntlrScriptParser.Create_statementContext::var_name to SCreateStatement::name,
        )
        transformer.registerTrivialPTtoASTConversion<AntlrScriptParser.Set_statementContext, SSetStatement>()
        transformer.registerTrivialPTtoASTConversion<AntlrScriptParser.Entity_by_id_expressionContext, SInstanceById>(
            AntlrScriptParser.Entity_by_id_expressionContext::id to SInstanceById::index,
        )
        transformer.registerTrivialPTtoASTConversion<AntlrScriptParser.Int_literal_expressionContext, SIntegerLiteral>(
            AntlrScriptParser.Int_literal_expressionContext::INT_VALUE to SIntegerLiteral::value,
        )
        transformer.registerTransform(AntlrScriptParser.String_literal_expressionContext::class) { pt, t ->
            SStringLiteral(pt.text.removePrefix("'").removeSuffix("'"))
        }
        transformer.registerTransform(AntlrScriptParser.Div_mult_expressionContext::class) { pt, t ->
            when (pt.op.text) {
                "/" -> {
                    TrivialFactoryOfParseTreeToASTTransform.trivialTransform<
                        AntlrScriptParser
                            .Div_mult_expressionContext,
                        SDivision,
                    >()(
                        pt,
                        t,
                    )
                }

                "*" -> {
                    TrivialFactoryOfParseTreeToASTTransform.trivialTransform<
                        AntlrScriptParser
                            .Div_mult_expressionContext,
                        SMultiplication,
                    >()(
                        pt,
                        t,
                    )
                }

                else -> TODO()
            }
        }
        transformer.registerTransform(AntlrScriptParser.Sum_sub_expressionContext::class) { pt, t ->
            when (pt.op.text) {
                "+" -> {
                    TrivialFactoryOfParseTreeToASTTransform.trivialTransform<
                        AntlrScriptParser
                            .Sum_sub_expressionContext,
                        SSum,
                    >()(
                        pt,
                        t,
                    )
                }

                "-" -> {
                    TrivialFactoryOfParseTreeToASTTransform.trivialTransform<
                        AntlrScriptParser
                            .Sum_sub_expressionContext,
                        SSubtraction,
                    >()(
                        pt,
                        t,
                    )
                }

                else -> TODO()
            }
        }
        transformer.unwrap<AntlrScriptParser.Parens_expressionContext, SExpression>(
            AntlrScriptParser.Parens_expressionContext::expression,
        )
        transformer.registerTrivialPTtoASTConversion<AntlrScriptParser.Print_statementContext, SPrintStatement>()
        transformer.registerTrivialPTtoASTConversion<AntlrScriptParser.Concat_expressionContext, SConcat>()
        transformer.registerTrivialPTtoASTConversion<
            AntlrScriptParser.Feature_access_expressionContext,
            SFeatureAccess,
        >(
            AntlrScriptParser.Feature_access_expressionContext::instance to SFeatureAccess::container,
        )
        val expectedAST =
            SScript(
                mutableListOf(
                    SCreateStatement(ReferenceByName("Client")),
                    SSetStatement(
                        ReferenceByName("name"),
                        SInstanceById(
                            ReferenceByName("Client"),
                            SIntegerLiteral(1),
                        ),
                        SStringLiteral("ACME Inc."),
                    ),
                    SCreateStatement(ReferenceByName("Product")),
                    SSetStatement(
                        ReferenceByName("value"),
                        SInstanceById(
                            ReferenceByName("Product"),
                            SIntegerLiteral(2),
                        ),
                        SDivision(SSum(SIntegerLiteral(1500), SIntegerLiteral(200)), SIntegerLiteral(2)),
                    ),
                    SPrintStatement(
                        SConcat(
                            SStringLiteral("Value of Product #2 is: "),
                            SFeatureAccess(
                                ReferenceByName("value"),
                                SInstanceById(
                                    ReferenceByName("Product"),
                                    SIntegerLiteral(2),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        val actualAST =
            transformer.transform(
                parseScript(
                    """
                    create Client
                    set name of Client #1 to 'ACME Inc.'
                    create Product
                    set value of Product #2 to (1500 + 200) / 2
                    print concat 'Value of Product #2 is: ' and value of Product #2
                    """.trimIndent(),
                ),
            )!!
        actualAST.walk().forEach { parent ->
            parent.children.forEach { child ->
                assertSame(parent, child.parent)
            }
        }
        assertASTsAreEqual(expectedAST, actualAST)
    }

    // Ensure that https://github.com/Strumenta/starlasu-kotlin/issues/241 is fixed
    @Test
    fun transformChildFactory() {
        val ctx = EntCtx("foo", listOf(EntCtxFeature("bar", EntCtxStringType())))
        val transformer = EntTransformer()
        val ast = transformer.transform(ctx)
        assertASTsAreEqual(Ent("foo", listOf(EntFeature("bar", EntStringType()))), ast!!)
        assertASTsAreEqual(
            Ent("foo", listOf(EntFeature(null, null))),
            transformer.transform(EntCtx("foo", listOf(null, EntCtxFeature(), null)))!!,
        )
    }
}

class EntTransformer(
    issues: MutableList<Issue> = mutableListOf(),
) : ParseTreeToASTTransformer(issues, allowGenericNode = false) {
    init {
        registerTransform(EntCtx::class) { ctx -> Ent(ctx.name) }
            .withChild(Ent::features, EntCtx::features)
        registerTransform(EntCtxFeature::class) { ctx -> EntFeature(name = ctx.name) }
            .withChild(EntFeature::type, EntCtxFeature::type)
        this.registerTransform(EntCtxStringType::class, EntStringType::class)
    }
}

data class EntCtx(
    val name: String,
    val features: List<EntCtxFeature?>,
)

data class EntCtxFeature(
    val name: String? = null,
    var type: EntCtxType? = null,
)

open class EntCtxType

open class EntCtxPrimitiveType : EntCtxType()

class EntCtxStringType : EntCtxPrimitiveType()

data class Ent(
    override val name: String,
    var features: List<EntFeature> = listOf(),
) : Node(),
    Named

data class EntFeature(
    override val name: String? = null,
    var type: EntType? = null,
) : Node(),
    PossiblyNamed

open class EntType : Node()

open class EntPrimitiveType : EntType()

class EntStringType : EntPrimitiveType()
