package com.strumenta.starlasu.model

import java.io.Serializable

interface Origin {
    @Internal
    val position: Position?

    @Internal
    val sourceText: String?

    @Internal
    val source: Source?
        get() = position?.source
}

class SimpleOrigin(
    override val position: Position?,
    override val sourceText: String? = null,
) : Origin,
    Serializable

data class CompositeOrigin(
    val elements: List<Origin>,
    override val position: Position?,
    override val sourceText: String?,
) : Origin,
    Serializable
