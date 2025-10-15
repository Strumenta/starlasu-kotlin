package com.strumenta.starlasu.lionweb

data class ProxyNode(
    val nodeId: String,
) : KNode()

object ProxyBasedNodeResolver : NodeResolver {
    override fun resolve(nodeID: String): KNode? = ProxyNode(nodeID)
}
