package com.strumenta.kolasu.model

import java.io.Serializable

interface Destination

data class CompositeDestination(val elements: List<Destination>) : Destination, Serializable {
    constructor(vararg elements: Destination) : this(elements.toList())
}

data class TextFileDestination(val position: Position?) : Destination, Serializable

object DroppedDestination : Destination

/**
 * We use this as a lightweight alternative, as it does not need us to keep a reference
 * to a Node.
 */
data class NodeIDDestination(val nodeId: String) : Destination
