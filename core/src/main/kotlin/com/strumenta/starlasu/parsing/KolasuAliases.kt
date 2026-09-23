package com.strumenta.starlasu.parsing

import com.strumenta.starlasu.model.Node
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext

/*
 * Kolasu 1.5 names of the parsing types. They are kept only to ease the migration of 1.5 language modules
 * to Starlasu 1.7 (see docs/migration-1.5-to-1.7.md) and will be removed in a future release.
 */

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("StarlasuParser<R, P, C, T>"))
typealias KolasuParser<R, P, C, T> = StarlasuParser<R, P, C, T>

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("StarlasuANTLRLexer<T>"))
typealias KolasuANTLRLexer<T> = StarlasuANTLRLexer<T>

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("StarlasuLexer<T>"))
typealias KolasuLexer<T> = StarlasuLexer<T>

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("StarlasuToken"))
typealias KolasuToken = StarlasuToken

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("StarlasuANTLRToken"))
typealias KolasuANTLRToken = StarlasuANTLRToken

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("StarlasuParserInstantiator"))
typealias KolasuParserInstantiator = StarlasuParserInstantiator

/**
 * Type-checked helper so that the aliases above keep the same type bounds as the original classes.
 */
@Suppress("unused", "DEPRECATION")
private fun <R : Node, P : Parser, C : ParserRuleContext, T : KolasuToken> boundsCheck(
    parser: KolasuParser<R, P, C, T>,
) = parser
