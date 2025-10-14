package com.strumenta.kolasu.ids

import com.strumenta.kolasu.model.ASTNode
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Node as KNode

/**
 * This defines a policy to associate IDs to Kolasu Nodes.
 * This is necessary as Kolasu Nodes have no IDs, but several systems need IDs.
 * It is important that the logic is implemented so that given the same Node, the same ID is returned.
 */
interface NodeIdProvider {
    fun id(kNode: ASTNode): String

    /**
     * This should be replaced in the future by setting kNode.id directly, instead of relying
     * on this external mapping
     */
    @Deprecated("No nodes have an ID")
    fun registerMapping(
        kNode: ASTNode,
        nodeId: String,
    ) {
        // do nothing
    }

    var parentProvider: NodeIdProvider?
    val topLevelProvider: NodeIdProvider
        get() = if (parentProvider == null) this else parentProvider!!.topLevelProvider
}

abstract class BaseNodeIdProvider : NodeIdProvider {
    override var parentProvider: NodeIdProvider? = null
        set(value) {
            field =
                if (value == this) {
                    null
                } else {
                    value
                }
        }
}

/**
 * The common approach for calculating Node IDs is to use Semantic Node IDs where present,
 * and Positional Node IDs in the other cases.
 */
class CommonNodeIdProvider(
    val semanticIDProvider: SemanticNodeIDProvider = DeclarativeNodeIdProvider(),
) : BaseNodeIdProvider() {
    override fun id(kNode: ASTNode): String =
        if (semanticIDProvider.hasSemanticIdentity(kNode)) {
            semanticIDProvider.semanticID(kNode)
        } else {
            positionalID(kNode)
        }

    private fun positionalID(kNode: ASTNode): String = StructuralNodeIdProvider().apply { parentProvider = this }.id(kNode)
}

interface SemanticNodeIDProvider {
    fun hasSemanticIdentity(kNode: ASTNode): Boolean

    fun semanticID(kNode: ASTNode): String
}
