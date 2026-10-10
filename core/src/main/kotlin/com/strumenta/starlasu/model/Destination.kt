package com.strumenta.starlasu.model

import java.io.Serializable

interface Destination

data class CompositeDestination(
    val elements: List<Destination>,
) : Destination,
    Serializable {
    constructor(vararg elements: Destination) : this(elements.toList())
}

data class TextFileDestination(
    val position: Position?,
) : Destination,
    Serializable

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
