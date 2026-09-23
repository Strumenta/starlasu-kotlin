@file:Suppress("DEPRECATION")

package com.strumenta.starlasu.parsing

import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.transformation.NodeFactory
import com.strumenta.starlasu.transformation.TransformationRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Verifies that the deprecated Kolasu 1.5 names resolve to the Starlasu 1.7 types.
 */
class KolasuAliasesTest {
    @Test
    fun `KolasuToken is StarlasuToken`() {
        val token: KolasuToken = StarlasuToken(TokenCategory.PLAIN_TEXT, Position(Point(1, 0), Point(1, 3)), "abc")
        assertEquals("abc", token.text)
        assertSame(StarlasuToken::class.java, KolasuToken::class.java)
    }

    @Test
    fun `NodeFactory is TransformationRule`() {
        assertSame(TransformationRule::class.java, NodeFactory::class.java)
        assertSame(StarlasuParser::class.java, KolasuParser::class.java)
        assertSame(StarlasuANTLRToken::class.java, KolasuANTLRToken::class.java)
        assertSame(StarlasuANTLRLexer::class.java, KolasuANTLRLexer::class.java)
        assertSame(StarlasuLexer::class.java, KolasuLexer::class.java)
        assertSame(StarlasuParserInstantiator::class.java, KolasuParserInstantiator::class.java)
    }
}
