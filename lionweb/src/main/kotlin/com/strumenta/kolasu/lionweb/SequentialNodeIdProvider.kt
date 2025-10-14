package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.model.ASTNode
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

    override fun registerMapping(
        kNode: ASTNode,
        nodeId: String,
    ) {
        if (cache.containsKey(kNode)) {
            require(cache[kNode] == nodeId)
        } else {
            cache[kNode] = nodeId
        }
    }
}
