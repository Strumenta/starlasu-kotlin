package com.strumenta.starlasu.ids

import com.strumenta.starlasu.model.ASTNode

/**
 * This defines a policy to associate IDs to Starlasu Nodes.
 * This is necessary as Starlasu Nodes have no IDs, but several systems need IDs.
 * It is important that the logic is implemented so that given the same Node, the same ID is returned.
 */
interface NodeIdProvider {
    /**
     * The ID of [kNode].
     */
    fun id(kNode: ASTNode): String

    /**
     * The provider wrapping this one, if any (e.g. a [CachingNodeIDProvider]). Implementations that need the IDs
     * of other nodes, such as parents, ask [topLevelProvider], so that the wrapper is used.
     */
    var parentProvider: NodeIdProvider?
    val topLevelProvider: NodeIdProvider
        get() = if (parentProvider == null) this else parentProvider!!.topLevelProvider
}

/**
 * Base [NodeIdProvider] that stores the [parentProvider]. Setting it to the provider itself sets it to null.
 */
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

    private fun positionalID(kNode: ASTNode): String =
        StructuralNodeIdProvider().apply { parentProvider = this }.id(kNode)
}

/**
 * Provides IDs based on what a node represents (e.g. its qualified name) rather than on its position in the tree.
 * Not every node has a semantic identity.
 */
interface SemanticNodeIDProvider {
    fun hasSemanticIdentity(kNode: ASTNode): Boolean

    fun semanticID(kNode: ASTNode): String
}
