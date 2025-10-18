package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.ids.NodeIdProvider
import com.strumenta.starlasu.model.ASTNode
import java.util.IdentityHashMap

class SequentialNodeIdProvider(
    startId: Long = 1L,
) : NodeIdProvider {
    private val cache = IdentityHashMap<ASTNode, String>()
    private var next = startId

    override fun id(kNode: ASTNode): String =
        cache.getOrPut(kNode) {
            (next++).toString()
        }

    override var parentProvider: NodeIdProvider?
        get() = null
        set(value) {
            throw UnsupportedOperationException()
        }
}
