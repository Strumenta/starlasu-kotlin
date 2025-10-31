package com.strumenta.starlasu.parsing

import com.strumenta.simplelang.SimpleLangLexer
import com.strumenta.simplelang.SimpleLangParser
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.validation.Issue
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.TokenStream
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        assertEquals(Position(Point(1, 4), Point(1, 7)), extraneousInput.position)
        val mismatchedInput = result.issues.find { it.message.startsWith("Mismatched input 'c'") }!!
        assertEquals(Position(Point(2, 8), Point(2, 9)), mismatchedInput.position)
    }
}
