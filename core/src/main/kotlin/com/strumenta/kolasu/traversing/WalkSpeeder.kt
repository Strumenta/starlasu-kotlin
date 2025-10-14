package com.strumenta.kolasu.traversing

import com.strumenta.kolasu.model.Multiplicity
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.PropertyDescription.Companion.multiplicity
import com.strumenta.kolasu.model.assignParents
import com.strumenta.kolasu.model.nodeOriginalProperties
import com.strumenta.kolasu.model.providesNodes
import kotlin.reflect.KClass
import java.util.concurrent.ConcurrentHashMap;

class WalkSpeeder {
    // Use primitive Class as key - faster hashCode/equals than KClass
    private val calculatorCaches = ConcurrentHashMap<Class<out Node>, Function1<Node, Sequence<Node>>>()

    fun <N : Node> walkChildren(node: N): Sequence<Node> {
        return calculatorCaches.computeIfAbsent(node.javaClass) { javaClass ->
            val kClass = javaClass.kotlin as KClass<N>
            val relevantProps = kClass.nodeOriginalProperties.filter { providesNodes(it) }
            // Use primitive boolean array - avoids boxing
            val multiplicity = BooleanArray(relevantProps.size) { i ->
                multiplicity(relevantProps[i]) == Multiplicity.MANY
            }

            // Cache the properties as an array for faster iteration
            val propsArray = relevantProps.toTypedArray()

            return@computeIfAbsent { node ->
                sequence {
                    // Use indices instead of forEachIndexed - slightly faster
                    for (i in propsArray.indices) {
                        val value = propsArray[i].get(node as N)
                        if (value != null) {
                            if (multiplicity[i]) {
                                yieldAll(value as Collection<Node>)
                            } else {
                                yield(value as Node)
                            }
                        }
                    }
                }
            }
        }.invoke(node)
    }

    fun walk(node: Node): Sequence<Node> = sequence {
        val stack = ArrayDeque<Node>()
        stack.addLast(node)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            yield(current)

            // Optimize: avoid creating intermediate list by using walkChildren directly
            // and adding to stack in reverse
            val childrenSeq = walkChildren(current)
            val children = if (childrenSeq is List<*>) {
                childrenSeq as List<Node>
            } else {
                childrenSeq.toList()
            }

            // Add in reverse order for correct traversal
            for (i in children.size - 1 downTo 0) {
                stack.addLast(children[i] as Node)
            }
        }
    }

    fun <N : Node>assignParents(node: N) {
        walkChildren(node).forEach {
            if (it == node) {
                throw java.lang.IllegalStateException("A node cannot be parent of itself: $node")
            }
            it.parent = node
            assignParents(it)
        }
    }
}
