package com.strumenta.starlasu.model

import java.io.Serializable

/**
 * What an AST node was derived from, such as a parse tree node or another AST node.
 */
interface Origin {
    @Internal
    val position: Position?

    @Internal
    val sourceText: String?

    @Internal
    val source: Source?
        get() = position?.source
}

/**
 * An [Origin] given only by a position and, optionally, the corresponding source text.
 */
class SimpleOrigin(
    override val position: Position?,
    override val sourceText: String? = null,
) : Origin,
    Serializable

/**
 * An [Origin] made of several origins, e.g. for a node derived from multiple nodes.
 */
data class CompositeOrigin(
    val elements: List<Origin>,
    override val position: Position?,
    override val sourceText: String?,
) : Origin,
    Serializable
