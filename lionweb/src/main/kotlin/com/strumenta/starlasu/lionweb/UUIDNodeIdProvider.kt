package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.ids.NodeIdProvider
import com.strumenta.starlasu.model.ASTNode
import java.util.IdentityHashMap
import java.util.UUID

/**
 * Assigns a random UUID to each node. A node keeps its ID. Setting [parentProvider] is not supported.
 */
class UUIDNodeIdProvider : NodeIdProvider {
    private val cache = IdentityHashMap<ASTNode, String>()

    override fun id(kNode: ASTNode): String =
        cache.getOrPut(kNode) {
            UUID.randomUUID().toString()
        }

    override var parentProvider: NodeIdProvider?
        get() = null
        set(value) {
            throw UnsupportedOperationException()
        }
}
