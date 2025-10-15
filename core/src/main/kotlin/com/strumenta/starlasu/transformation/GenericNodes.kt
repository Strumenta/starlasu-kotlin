package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.traversing.children

/**
 * A generic AST node. We use it to represent parts of a source tree that we don't know how to translate yet.
 */
@Deprecated("To be removed in Kolasu 2.0")
class GenericNode(
    parent: ASTNode? = null,
) : Node() {
    init {
        this.parent = parent
    }
}

@Deprecated("To be removed in Kolasu 2.0")
fun ASTNode.findGenericNode(): GenericNode? =
    if (this is GenericNode) {
        this
    } else {
        this.children.firstNotNullOfOrNull {
            it.findGenericNode()
        }
    }
