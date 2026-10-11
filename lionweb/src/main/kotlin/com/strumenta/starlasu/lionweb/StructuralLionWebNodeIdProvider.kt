package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.ids.ConstantSourceIdProvider
import com.strumenta.starlasu.ids.SimpleSourceIdProvider
import com.strumenta.starlasu.ids.SourceIdProvider
import com.strumenta.starlasu.ids.StructuralNodeIdProvider
import com.strumenta.starlasu.model.ASTNode
import io.lionweb.utils.IdUtils

/**
 * A [StructuralNodeIdProvider] that throws [IllegalStateException] if it produces an invalid LionWeb ID.
 */
class StructuralLionWebNodeIdProvider(
    sourceIdProvider: SourceIdProvider = SimpleSourceIdProvider(),
) : StructuralNodeIdProvider(sourceIdProvider) {
    constructor(customSourceId: String) : this(ConstantSourceIdProvider(customSourceId))

    override fun id(kNode: ASTNode): String {
        val id = super.id(kNode)
        if (!IdUtils.isValidID(id)) {
            throw IllegalStateException("An invalid LionWeb Node ID has been produced: $id. Produced for $kNode")
        }
        return id
    }
}
