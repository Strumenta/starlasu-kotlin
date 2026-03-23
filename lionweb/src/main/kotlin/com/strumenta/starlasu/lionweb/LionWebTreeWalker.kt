package com.strumenta.starlasu.lionweb

import io.lionweb.language.Containment
import io.lionweb.model.impl.ProxyNode
import java.util.concurrent.ConcurrentHashMap

class LionWebTreeWalker {
    private val containmentsCache = ConcurrentHashMap<String, List<Containment>>()

    /**
     * Returns all nodes in this subtree in pre-order (root first).
     *
     * Previously backed by a Kotlin `sequence { yield() }` coroutine, which allocated
     * a coroutine state-machine object per element.  Now uses an iterative stack so the
     * only allocation is the result ArrayList and the internal ArrayDeque stack.
     */
    fun thisAndAllDescendants(node: LWNode): List<LWNode> {
        val result = ArrayList<LWNode>(64)
        val stack = ArrayDeque<LWNode>(32)
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            result.add(current)
            if (current !is ProxyNode) {
                val containments =
                    containmentsCache.computeIfAbsent(current.classifier.id!!) {
                        current.classifier.allContainments()
                    }
                // Push children in reverse order so the first child is processed first.
                for (i in containments.indices.reversed()) {
                    val children = current.getChildren(containments[i])
                    for (j in children.indices.reversed()) {
                        stack.addLast(children[j])
                    }
                }
            }
        }
        return result
    }

    /**
     * Returns all nodes in this subtree leaves-first (post-order).
     */
    fun thisAndAllDescendantsLeavesFirst(node: LWNode): List<LWNode> {
        val result = ArrayList<LWNode>(64)
        collectLeavesFirst(node, result)
        return result
    }

    private fun collectLeavesFirst(
        node: LWNode,
        result: ArrayList<LWNode>,
    ) {
        if (node is ProxyNode) {
            result.add(node)
            return
        }

        val containments =
            containmentsCache.computeIfAbsent(node.classifier.id!!) {
                node.classifier.allContainments()
            }

        for (i in containments.indices) {
            val children = node.getChildren(containments[i])
            for (j in children.indices) {
                collectLeavesFirst(children[j], result)
            }
        }
        result.add(node)
    }
}
