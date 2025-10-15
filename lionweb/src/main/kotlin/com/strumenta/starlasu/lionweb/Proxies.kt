package com.strumenta.starlasu.lionweb

data class ProxyNode(
    val nodeId: String,
) : SNode()

object ProxyBasedNodeResolver : NodeResolver {
    override fun resolve(nodeID: String): SNode? = ProxyNode(nodeID)
}
