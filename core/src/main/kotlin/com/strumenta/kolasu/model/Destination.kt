package com.strumenta.kolasu.model

import java.io.Serializable

interface Destination

data class CompositeDestination(val elements: List<Destination>) : Destination, Serializable {
    constructor(vararg elements: Destination) : this(elements.toList())
}

data class TextFileDestination(val position: Position?) : Destination, Serializable

object DroppedDestination : Destination
