package com.strumenta.kolasu.lionweb

import io.lionweb.language.Concept
import io.lionweb.language.Containment
import io.lionweb.model.impl.ProxyNode

class LWWalkSpeeder {

    private val containmentsCache = mutableMapOf<Concept, List<Containment>>()

    fun thisAndAllDescendants(node: LWNode): Sequence<LWNode> {
        return sequence {
            yieldThisAndAllDescendants(node)
        }
    }

    private suspend fun SequenceScope<LWNode>.yieldThisAndAllDescendants(node: LWNode) {
        val containments = containmentsCache.computeIfAbsent(node.classifier) { concept ->
            concept.allContainments()
        }
        yield(node)
        if (node is ProxyNode) {
            return
        }
        containments.forEach { containment ->
            node.getChildren(containment).forEach { yieldThisAndAllDescendants(it) }
        }
    }
}
