package com.strumenta.starlasu.parsing

import com.strumenta.simplelang.SimpleLangLexer
import com.strumenta.simplelang.SimpleLangParser
import com.strumenta.starlasu.mapping.ParseTreeToASTTransformer
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.model.StringSource
import com.strumenta.starlasu.transformation.FaultTolerance
import com.strumenta.starlasu.transformation.MappingException
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueSeverity
import com.strumenta.starlasu.validation.IssueType
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.TokenStream
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

open class SimpleLangStarlasuParser :
    StarlasuParser<
        Node,
        SimpleLangParser,
        SimpleLangParser.CompilationUnitContext,
        StarlasuANTLRToken,
    >(ANTLRTokenFactory()) {
    override fun createANTLRLexer(charStream: CharStream): Lexer = SimpleLangLexer(charStream)

    override fun createANTLRParser(tokenStream: TokenStream): SimpleLangParser = SimpleLangParser(tokenStream)

    override fun parseTreeToAst(
        parseTreeRoot: SimpleLangParser.CompilationUnitContext,
        considerPosition: Boolean,
        issues: MutableList<Issue>,
        source: Source?,
    ): Node? = null

    override fun clearCaches() {
        super.clearCaches()
        cachesCounter++
    }

    public var cachesCounter = 0
}

class FailingMappingSimpleLangStarlasuParser : SimpleLangStarlasuParser() {
    override fun parseTreeToAst(
        parseTreeRoot: SimpleLangParser.CompilationUnitContext,
        considerPosition: Boolean,
        issues: MutableList<Issue>,
        source: Source?,
    ): Node? = throw IllegalStateException("boom")
}

class StrictTransformerSimpleLangStarlasuParser : SimpleLangStarlasuParser() {
    override fun parseTreeToAst(
        parseTreeRoot: SimpleLangParser.CompilationUnitContext,
        considerPosition: Boolean,
        issues: MutableList<Issue>,
        source: Source?,
    ): Node? = ParseTreeToASTTransformer(FaultTolerance.STRICT).transform(parseTreeRoot) as Node?
}

class StarlasuParserTest {
    @Test
    fun testLexing() {
        val parser = SimpleLangStarlasuParser()
        val result =
            parser.parse(
                """set a = 10
            |set b = ""
            |display c
                """.trimMargin(),
            )
        assertNotNull(result)
        val lexingResult = (parser.tokenFactory as ANTLRTokenFactory).extractTokens(result)
        assertNotNull(lexingResult)
        assertEquals(11, lexingResult.tokens.size)
        val text = lexingResult.tokens.map { it.text }
        assertEquals(listOf("set", "a", "=", "10", "set", "b", "=", "\"\"", "display", "c", "<EOF>"), text)
    }

    @Test
    fun clearCache() {
        val parser = SimpleLangStarlasuParser()
        parser.parse(
            """set a = 10
            |set b = ""
            |display c
            """.trimMargin(),
        )
        parser.executionsToNextCacheClean = 0
        assertEquals(0, parser.cachesCounter)
        parser.parse(
            """set a = 10
            |set b = ""
            |display c
            """.trimMargin(),
        )
        assertEquals(1, parser.cachesCounter)
    }

    @Test
    fun issuesAreCapitalized() {
        val parser = SimpleLangStarlasuParser()
        val result =
            parser.parse(
                """set set a = 10
            |display c
                """.trimMargin(),
            )
        assert(result.issues.isNotEmpty())
        assertNotNull(result.issues.find { it.message.startsWith("Extraneous input 'set'") })
        assertNotNull(result.issues.find { it.message.startsWith("Mismatched input 'c'") })
    }

    @Test
    fun issuesIncludeSource() {
        val parser = SimpleLangStarlasuParser()
        val source = StringSource("set set a = 10 $")
        val result = parser.parse("set set a = 10 $", true, false, source)
        assert(result.issues.any { it.type == IssueType.LEXICAL })
        assert(result.issues.any { it.type == IssueType.SYNTACTIC })
        assert(result.issues.all { it.position?.source === source })
    }

    @Test
    fun issuesHaveNotFlatPosition() {
        val parser = SimpleLangStarlasuParser()
        val result =
            parser.parse(
                """set set a = 10
            |display c
                """.trimMargin(),
            )
        assert(result.issues.isNotEmpty())
        assert(result.issues.none { it.position?.isFlat ?: false })
        val extraneousInput = result.issues.find { it.message.startsWith("Extraneous input 'set'") }!!
        assertEquals(Position(Point(1, 4), Point(1, 7)), extraneousInput.position?.copy(source = null))
        val mismatchedInput = result.issues.find { it.message.startsWith("Mismatched input 'c'") }!!
        assertEquals(Position(Point(2, 8), Point(2, 9)), mismatchedInput.position?.copy(source = null))
    }

    @Test
    fun mappingFailureWithoutSyntaxErrorsThrowsMappingException() {
        val parser = FailingMappingSimpleLangStarlasuParser()
        val e =
            assertFailsWith<MappingException> {
                parser.parse("set a = 10\ndisplay 10")
            }
        assertIs<IllegalStateException>(e.cause)
        assertEquals("boom", e.cause!!.message)
        assertEquals(emptyList(), e.issues)
    }

    @Test
    fun mappingFailureWithUnmappedNodeThrowsMappingException() {
        val parser = StrictTransformerSimpleLangStarlasuParser()
        val e =
            assertFailsWith<MappingException> {
                parser.parse("set a = 10\ndisplay 10")
            }
        assertNotNull(e.cause)
        assertEquals(emptyList(), e.issues)
    }

    @Test
    fun mappingFailureAfterSyntaxErrorsIsReportedAsIssue() {
        val parser = FailingMappingSimpleLangStarlasuParser()
        val result = parser.parse("set set a = 10\ndisplay c")
        assertNull(result.root)
        assertNotNull(result.issues.find { it.message.startsWith("Extraneous input 'set'") })
        val mappingIssues = result.issues.filter { it.type == IssueType.TRANSLATION }
        assertEquals(1, mappingIssues.size)
        assertEquals(IssueSeverity.ERROR, mappingIssues[0].severity)
        assertTrue(mappingIssues[0].message.contains("boom"))
    }
}
