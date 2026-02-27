package com.strumenta.starlasu.lionwebclient

import com.strumenta.starlasu.lionweb.SNode
import com.strumenta.starlasu.lionweb.LWNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.base.v2.ASTLanguage
import io.lionweb.kotlin.children
import io.lionweb.language.Concept
import io.lionweb.model.HasSettableParent
import io.lionweb.model.impl.ProxyNode

fun Node.withSource(source: Source): Node {
    this.setSourceForTree(source)
    require(this.source === source)
    return this
}

fun HasSettableParent.setParentID(parentID: String?) {
    val parent =
        if (parentID == null) {
            null
        } else {
            ProxyNode(parentID)
        }
    this.setParent(parent)
}

fun StarlasuClient.getASTRoots(aLWNode: LWNode): Sequence<SNode> {
    val res = mutableListOf<SNode>()

    fun exploreForASTs(aLWNode: LWNode) {
        val isKNode: Boolean = isStarlasuConcept(aLWNode.classifier)
        if (isKNode) {
            res.add(toKolasuNode(aLWNode))
        } else {
            aLWNode.children.forEach { exploreForASTs(it) }
        }
    }

    exploreForASTs(aLWNode)
    return res.asSequence()
}

fun isStarlasuConcept(concept: Concept): Boolean {
    return concept.allAncestors().contains(ASTLanguage.getInstance().astNode)
}
