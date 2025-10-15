package com.strumenta.starlasu.model

import com.strumenta.starlasu.base.IDProvider
import com.strumenta.starlasu.ids.NodeIdProvider

class NodeIdProviderAdapter(
    val idProvider: IDProvider,
) : NodeIdProvider {
    private var available: List<String>? = null
    private var index: Int = -1

    override fun id(kNode: ASTNode): String {
        if (available == null || index >= available!!.count()) {
            available = idProvider.provideIDs(4096)
            index = 0
        }
        val id = available!![index++]
        return id
    }

    override var parentProvider: NodeIdProvider? = null
}

fun NodeIdProvider.assignIDsToTree(
    root: ASTNode,
    overriding: Boolean = false,
) {
    if (overriding || root.id == null) {
        root.id = this.id(root)
    }
    root.children.forEach { child -> this.assignIDsToTree(child, overriding) }
}
