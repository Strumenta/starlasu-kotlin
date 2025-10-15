package com.strumenta.starlasu.serialization

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.traversing.walk
import java.util.IdentityHashMap

fun interface IdProvider {
    fun getId(node: ASTNode): String?
}

class SequentialIdProvider(
    private var counter: Int = 0,
) : IdProvider {
    override fun getId(node: ASTNode): String? = "${this.counter++}"
}

class OnlyReferencedIdProvider(
    private val root: ASTNode,
    private var idProvider: IdProvider = SequentialIdProvider(),
) : IdProvider {
    private val referencedElements: Set<PossiblyNamed> by lazy {
        this.root
            .walk()
            .flatMap { node ->
                node.properties
                    .filter { property -> property.value is ReferenceByName<*> }
                    .mapNotNull { property -> (property.value as ReferenceByName<*>).referred }
            }.toSet()
    }

    override fun getId(node: ASTNode): String? =
        this.referencedElements
            .find { referencedElement -> referencedElement == node }
            ?.let { idProvider.getId(node) }
}

/**
 * @param walker a function that generates a sequence of nodes. By default this is the depth-first "walk" method.
 * @param idProvider a provider that takes a node and returns a String identifier representing it
 * For post-order traversal, take "walkLeavesFirst".
 * @return walks the whole AST starting from the given node and associates each visited node with a generated id
 **/
fun ASTNode.computeIds(
    walker: (ASTNode) -> Sequence<ASTNode> = ASTNode::walk,
    idProvider: IdProvider = SequentialIdProvider(),
): IdentityHashMap<ASTNode, String> {
    val idsMap = IdentityHashMap<ASTNode, String>()
    walker.invoke(this).forEach {
        val id = idProvider.getId(it)
        if (id != null) idsMap[it] = id
    }
    return idsMap
}

fun ASTNode.computeIdsForReferencedNodes(
    walker: (ASTNode) -> Sequence<ASTNode> = ASTNode::walk,
    idProvider: IdProvider = OnlyReferencedIdProvider(this),
): IdentityHashMap<ASTNode, String> = computeIds(walker, idProvider)
