package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.model.Node
import java.util.IdentityHashMap

class SequentialNodeIdProvider(startId: Long = 1L) : NodeIdProvider {

    private val cache = IdentityHashMap<KNode, String>()
    private var next = startId

    @Synchronized
    override fun id(kNode: Node): String {
        return cache.getOrPut(kNode) {
            (next++).toString()
        }
    }

    override var parentProvider: NodeIdProvider?
        get() = null
        set(value) {
            throw UnsupportedOperationException()
        }

    @Synchronized
    override fun registerMapping(kNode: Node, nodeId: String) {
        if (cache.containsKey(kNode)) {
            require(cache[kNode] == nodeId)
        } else {
            cache[kNode] = nodeId
        }
    }
}
