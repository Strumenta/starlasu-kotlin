package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.model.SourceWithID
import io.lionweb.utils.IdUtils

data class LionWebSource(
    val sourceId: String,
) : Source(),
    SourceWithID {
    override fun sourceID(): String = sourceId

    init {
        if (!IdUtils.isValidID(sourceId)) {
            throw IllegalArgumentException("Illegal SourceId provided")
        }
    }
}
