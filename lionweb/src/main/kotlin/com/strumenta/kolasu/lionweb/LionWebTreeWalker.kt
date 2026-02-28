package com.strumenta.kolasu.lionweb

import io.lionweb.language.Containment
import io.lionweb.model.impl.ProxyNode
import java.util.concurrent.ConcurrentHashMap

class LionWebTreeWalker {

    private val containmentsCache = ConcurrentHashMap<String, List<Containment>>()

    fun thisAndAllDescendants(node: LWNode): Sequence<LWNode> {
        return sequence {
            yieldThisAndAllDescendants(node)
        }
    }

    private suspend fun SequenceScope<LWNode>.yieldThisAndAllDescendants(node: LWNode) {
        val containments = containmentsCache.computeIfAbsent(node.classifier.id!!) { _ ->
            node.classifier.allContainments()
        }
        yield(node)
        if (node is ProxyNode) {
            return
        }

        var i = 0
        val size = containments.size
        while (i < size) {
            val containment = containments[i]
            val children = node.getChildren(containment)
            var j = 0
            val childrenSize = children.size
            while (j < childrenSize) {
                yieldThisAndAllDescendants(children[j])
                j++
            }
            i++
        }
    }

    fun thisAndAllDescendantsLeavesFirst(node: LWNode): Sequence<LWNode> = sequence {
        yieldThisAndAllDescendantsLeavesFirst(node)
    }

    private suspend fun SequenceScope<LWNode>.yieldThisAndAllDescendantsLeavesFirst(node: LWNode) {
        if (node is ProxyNode) {
            yield(node)
            return
        }

        val containments = containmentsCache.computeIfAbsent(node.classifier.id!!) { _ ->
            node.classifier.allContainments()
        }

        var i = 0
        val size = containments.size
        while (i < size) {
            val containment = containments[i]
            val children = node.getChildren(containment)
            var j = 0
            val childrenSize = children.size
            while (j < childrenSize) {
                yieldThisAndAllDescendantsLeavesFirst(node = children[j])
                j++
            }
            i++
        }

        yield(node)
    }
}
