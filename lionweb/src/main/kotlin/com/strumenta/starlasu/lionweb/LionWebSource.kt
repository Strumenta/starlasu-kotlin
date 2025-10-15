package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.model.SourceWithID
import io.lionweb.utils.CommonChecks

data class LionWebSource(
    val sourceId: String,
) : Source(),
    SourceWithID {
    override fun sourceID(): String = sourceId

    init {
        if (!CommonChecks.isValidID(sourceId)) {
            throw IllegalArgumentException("Illegal SourceId provided")
        }
    }
}
