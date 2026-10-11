package com.strumenta.starlasu.model

import java.io.Serializable

/**
 * Where an AST node ended up, e.g. after code generation or an AST-to-AST transformation.
 * See [ASTNode.destination].
 */
interface Destination

/**
 * A destination made of several destinations, e.g. for a node translated into multiple nodes.
 */
data class CompositeDestination(
    val elements: List<Destination>,
) : Destination,
    Serializable {
    constructor(vararg elements: Destination) : this(elements.toList())
}

/**
 * The [position] of the code generated for a node, as recorded by
 * [com.strumenta.starlasu.codegen.PrinterOutput.associate].
 */
data class TextFileDestination(
    val position: Position?,
) : Destination,
    Serializable

/**
 * Marks a node as dropped: it has no counterpart in the output. See [withDroppedDestination].
 */
object DroppedDestination : Destination

/**
 * The positions this destination corresponds to, if any.
 * A [TextFileDestination] has its own position, a [CompositeDestination] has the positions of its elements,
 * and an [ASTNode] used as a destination has its own position. Other destinations have no position.
 */
val Destination.positions: List<Position>
    get() =
        when (this) {
            is TextFileDestination -> listOfNotNull(position)
            is CompositeDestination -> elements.flatMap { it.positions }
            is ASTNode -> listOfNotNull(position)
            else -> emptyList()
        }
