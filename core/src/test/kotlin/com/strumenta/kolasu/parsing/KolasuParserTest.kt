package com.strumenta.kolasu.parsing

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.Source
import com.strumenta.kolasu.validation.Issue
import com.strumenta.simplelang.SimpleLangLexer
import com.strumenta.simplelang.SimpleLangParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.atn.ParserATNSimulator
import org.antlr.v4.runtime.atn.PredictionContextCache
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

open class SimpleLangKolasuParser : KolasuParser<Node, SimpleLangParser, SimpleLangParser.CompilationUnitContext,
    KolasuANTLRToken>(ANTLRTokenFactory()) {
    override fun createANTLRLexer(charStream: CharStream): Lexer {
        return SimpleLangLexer(charStream)
    }

    override fun createANTLRParser(tokenStream: TokenStream): SimpleLangParser {
        return SimpleLangParser(tokenStream)
    }

    override fun parseTreeToAst(
        parseTreeRoot: SimpleLangParser.CompilationUnitContext,
        considerPosition: Boolean,
        issues: MutableList<Issue>,
        source: Source?
    ): Node? = null

    override fun clearCaches() {
        super.clearCaches()
        cachesCounter++
    }

    public var cachesCounter = 0
}

class KolasuParserTest {

    @Test
    fun testLexing() {
        val parser = SimpleLangKolasuParser()
        val result = parser.parse(
            """set a = 10
            |set b = ""
            |display c
            """.trimMargin()
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
        val parser = SimpleLangKolasuParser()
        parser.parse(
            """set a = 10
            |set b = ""
            |display c
            """.trimMargin()
        )
        parser.executionsToNextCacheClean = 0
        assertEquals(0, parser.cachesCounter)
        parser.parse(
            """set a = 10
            |set b = ""
            |display c
            """.trimMargin()
        )
        assertEquals(1, parser.cachesCounter)
    }

    @Test
    fun issuesAreCapitalized() {
        val parser = SimpleLangKolasuParser()
        val result = parser.parse(
            """set set a = 10
            |display c
            """.trimMargin()
        )
        assert(result.issues.isNotEmpty())
        assertNotNull(result.issues.find { it.message.startsWith("Extraneous input 'set'") })
        assertNotNull(result.issues.find { it.message.startsWith("Mismatched input 'c'") })
    }

    @Test
    fun issuesHaveNotFlatPosition() {
        val parser = SimpleLangKolasuParser()
        val result = parser.parse(
            """set set a = 10
            |display c
            """.trimMargin()
        )
        assert(result.issues.isNotEmpty())
        assert(result.issues.none { it.position?.isFlat ?: false })
        val extraneousInput = result.issues.find { it.message.startsWith("Extraneous input 'set'") }!!
        assertEquals(Position(Point(1, 4), Point(1, 7)), extraneousInput.position)
        val mismatchedInput = result.issues.find { it.message.startsWith("Mismatched input 'c'") }!!
        assertEquals(Position(Point(2, 8), Point(2, 9)), mismatchedInput.position)
    }

    /**
     * Traces lexer/parser creation and lets the subclass keep its own configured
     * interpreter, as dependent projects do when configuring their parsers.
     */
    class HookedParser : SimpleLangKolasuParser() {
        var createdLexers = 0
        var createdParsers = 0
        var lastParser: SimpleLangParser? = null
        var attachedSimulator: ParserATNSimulator? = null
        val myCache = PredictionContextCache()

        override fun createANTLRLexer(charStream: CharStream): Lexer {
            createdLexers++
            return super.createANTLRLexer(charStream)
        }

        override fun createANTLRParser(tokenStream: TokenStream): SimpleLangParser {
            createdParsers++
            return super.createANTLRParser(tokenStream).also { lastParser = it }
        }

        override fun attachParserInterpreter(parser: SimpleLangParser) {
            attachedSimulator = ParserATNSimulator(
                parser,
                parser.atn,
                parser.interpreter.decisionToDFA,
                myCache
            )
            parser.interpreter = attachedSimulator
        }
    }

    @Test
    fun clearCachesDoesNotInstantiateLexerOrParser() {
        // Regression: clearCaches() used to create a lexer and a parser just to clear
        // their DFA, triggering subclass configuration side-effects. It now clears the
        // recorded DFA arrays directly.
        val parser = HookedParser()
        parser.parseFirstStage("set a = 10")
        assertTrue(parser.createdLexers > 0 && parser.createdParsers > 0)
        val lexers = parser.createdLexers
        val parsers = parser.createdParsers
        parser.clearCaches()
        assertEquals(lexers, parser.createdLexers, "clearCaches() instantiated a new lexer")
        assertEquals(parsers, parser.createdParsers, "clearCaches() instantiated a new parser")
    }

    @Test
    fun createParserHonorsSubclassConfiguredInterpreter() {
        // Regression: createParser used to unconditionally overwrite the interpreter
        // configured by the subclass. It now goes through the attachParserInterpreter
        // hook, which subclasses can override to keep their own configuration.
        val parser = HookedParser()
        parser.parseFirstStage("set a = 10")
        assertSame(parser.attachedSimulator, parser.lastParser!!.interpreter)
    }

    @Test
    fun configuredLexerInterpreterStateIsPreserved() {
        // Regression: replacing the lexer interpreter discarded state configured by
        // subclasses in createANTLRLexer — in ANTLR the position lives in the simulator,
        // so a fresh LexerATNSimulator reset it. The interpreter is no longer replaced.
        val parser = object : SimpleLangKolasuParser() {
            override fun createANTLRLexer(charStream: CharStream): Lexer {
                return super.createANTLRLexer(charStream).apply { line = 100 }
            }
        }
        assertEquals(100, parser.parseFirstStage("set a = 10").root!!.start.line)
    }

    @Test
    fun clearCachesClearsDFAWhenInterpreterHookIsOverridden() {
        // Regression: the DFA arrays to clear were recorded inside the overridable
        // attachParserInterpreter hook, so a subclass overriding it (like HookedParser,
        // which does not call super) left them null and clearCaches() silently stopped
        // clearing the DFA. They are now recorded after the hook, so the effective
        // interpreter's DFA is always tracked.
        val parser = HookedParser()
        parser.parseFirstStage("set a = 10")
        val decisionToDFA = parser.lastParser!!.interpreter.decisionToDFA
        val before = decisionToDFA.toList()
        parser.clearCaches()
        assertTrue(
            decisionToDFA.indices.all { decisionToDFA[it] !== before[it] },
            "clearCaches() did not replace the recorded DFA elements"
        )
        // Behavioural check: the parser still works after the DFA was cleared — the
        // first parse re-warms the DFA from scratch.
        assertNotNull(parser.parseFirstStage("set a = 10").root)
    }
}
