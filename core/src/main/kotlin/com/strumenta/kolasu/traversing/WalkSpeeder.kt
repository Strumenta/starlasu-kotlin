package com.strumenta.kolasu.traversing

import com.strumenta.kolasu.model.Multiplicity
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.PropertyDescription.Companion.multiplicity
import com.strumenta.kolasu.model.nodeOriginalProperties
import com.strumenta.kolasu.model.providesNodes
import kotlin.reflect.KClass

class WalkSpeeder {
    private val calculatorCaches = mutableMapOf<KClass<out Node>, Function1<Node, Sequence<Node>>>()

    fun <N : Node>walkChildren(node: N): Sequence<Node> {
        return calculatorCaches.computeIfAbsent(node.javaClass.kotlin as KClass<N>) { kClass ->
            val relevantProps = (kClass as KClass<N>).nodeOriginalProperties.filter { providesNodes(it) }
            val multiplicity = relevantProps.map { multiplicity(it) == Multiplicity.MANY }.toTypedArray()
            return@computeIfAbsent { node ->
                sequence {
                    relevantProps.forEachIndexed { index, prop ->
                        if (multiplicity[index]) {
                            yieldAll(prop.get(node as N) as Collection<Node>)
                        } else {
                            (prop.get(node as N) as? Node)?.let { yield(it) }
                        }
                    }
                }
            }
        }.invoke(node)
    }

    fun walk(node: Node): Sequence<com.strumenta.kolasu.model.Node> {
        return sequence {
            val stack = ArrayDeque<Node>()
            stack.addLast(node)

            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                yield(current)

                // Add children in reverse order to maintain original traversal order
                val children = walkChildren(current).toList()
                for (i in children.size - 1 downTo 0) {
                    stack.addLast(children[i])
                }
            }
        }
    }
}
