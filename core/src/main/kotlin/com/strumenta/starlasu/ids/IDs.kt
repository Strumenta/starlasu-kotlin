package com.strumenta.starlasu.ids

import com.strumenta.starlasu.IDProvider
import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.children

/**
 * Adapts an [IDProvider] to [NodeIdProvider]. Each call to [id] returns a new ID, even for the same node.
 *
 * It can be shared by threads processing different files in parallel (e.g., the components of the Starlasu pipeline).
 */
class NodeIdProviderAdapter(
    val idProvider: IDProvider,
) : NodeIdProvider {
    private var available: List<String>? = null
    private var index: Int = -1

    @Synchronized
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
