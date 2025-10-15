package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.ids.ConstantSourceIdProvider
import com.strumenta.starlasu.ids.SimpleSourceIdProvider
import com.strumenta.starlasu.ids.SourceIdProvider
import com.strumenta.starlasu.ids.StructuralNodeIdProvider
import com.strumenta.starlasu.model.ASTNode
import io.lionweb.utils.CommonChecks

class StructuralLionWebNodeIdProvider(
    sourceIdProvider: SourceIdProvider = SimpleSourceIdProvider(),
) : StructuralNodeIdProvider(sourceIdProvider) {
    constructor(customSourceId: String) : this(ConstantSourceIdProvider(customSourceId))

    override fun id(kNode: ASTNode): String {
        val id = super.id(kNode)
        if (!CommonChecks.isValidID(id)) {
            throw IllegalStateException("An invalid LionWeb Node ID has been produced: $id. Produced for $kNode")
        }
        return id
    }
}
