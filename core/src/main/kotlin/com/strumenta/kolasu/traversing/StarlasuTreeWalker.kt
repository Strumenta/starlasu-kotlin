package com.strumenta.kolasu.traversing

import com.strumenta.kolasu.model.Multiplicity
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.PropertyDescription.Companion.multiplicity
import com.strumenta.kolasu.model.nodeOriginalProperties
import com.strumenta.kolasu.model.providesNodes
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface StarlasuTreeWalker {
    fun <N : Node> walkChildren(node: N): Sequence<Node>
    fun walk(node: Node): Sequence<Node>
    fun <N : Node> assignParents(node: N)
}

class CommonStarlasuTreeWalker : StarlasuTreeWalker {
    // Cache stores a factory that returns the children list directly (no Sequence/coroutine overhead).
    // We use java Class as key because they have faster hashCode/equals than KClass.
    private val calculatorCaches = ConcurrentHashMap<Class<out Node>, Function1<Node, List<Node>>>()

    // Tracks which classes override getOriginalProperties() (e.g. Java nodes that use
    // JavaBeans reflection instead of Kotlin memberProperties).
    private val overridesOriginalPropertiesCache = ConcurrentHashMap<Class<out Node>, Boolean>()

    private fun overridesOriginalProperties(javaClass: Class<out Node>): Boolean =
        overridesOriginalPropertiesCache.computeIfAbsent(javaClass) { clz ->
            try {
                clz.getMethod("getOriginalProperties").declaringClass != Node::class.java
            } catch (_: NoSuchMethodException) {
                false
            }
        }

    /**
     * Returns the direct children of [node] as a List.
     * Returns [emptyList] (singleton) for leaf nodes — no allocation.
     */
    internal fun walkChildrenToList(node: Node): List<Node> =
        calculatorCaches.computeIfAbsent(node.javaClass) { javaClass ->
            if (overridesOriginalProperties(javaClass)) {
                // Fall back to the PropertyDescription path for classes that override
                // getOriginalProperties() (e.g. Java nodes using JavaBeans reflection).
                return@computeIfAbsent { n ->
                    val result = ArrayList<Node>()
                    n.originalProperties.forEach { property ->
                        when (val value = property.value) {
                            is Node -> result.add(value)
                            is Collection<*> -> value.forEach { if (it is Node) result.add(it) }
                        }
                    }
                    result
                }
            }

            @Suppress("UNCHECKED_CAST")
            val kClass = javaClass.kotlin as KClass<Node>
            val relevantProps = kClass.nodeOriginalProperties.filter { providesNodes(it) }
            val many = BooleanArray(relevantProps.size) { i -> multiplicity(relevantProps[i]) == Multiplicity.MANY }

            @Suppress("UNCHECKED_CAST")
            val propsArray = relevantProps.toTypedArray() as Array<KProperty1<Node, *>>

            return@computeIfAbsent { n ->
                // Defer ArrayList creation until we actually find a child.
                // Leaf nodes pay zero allocation cost (returns emptyList singleton).
                var result: ArrayList<Node>? = null
                for (i in propsArray.indices) {
                    val value = propsArray[i].get(n)
                    if (value != null) {
                        if (many[i]) {
                            @Suppress("UNCHECKED_CAST")
                            val col = value as Collection<Node>
                            if (col.isNotEmpty()) {
                                if (result == null) result = ArrayList(col.size)
                                result.addAll(col)
                            }
                        } else {
                            if (result == null) result = ArrayList(2)
                            result.add(value as Node)
                        }
                    }
                }
                result ?: emptyList()
            }
        }.invoke(node)

    override fun <N : Node> walkChildren(node: N): Sequence<Node> = walkChildrenToList(node).asSequence()

    override fun walk(node: Node): Sequence<Node> = sequence {
        val stack = ArrayDeque<Node>()
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            yield(current)
            val children = walkChildrenToList(current)
            for (i in children.size - 1 downTo 0) {
                stack.addLast(children[i])
            }
        }
    }

    /** Post-order accumulation into [result]. Recursive but AST depth is bounded in practice. */
    internal fun collectLeavesFirst(node: Node, result: ArrayList<Node>) {
        val children = walkChildrenToList(node)
        for (child in children) collectLeavesFirst(child, result)
        result.add(node)
    }

    override fun <N : Node> assignParents(node: N) {
        walkChildrenToList(node).forEach {
            if (it == node) throw IllegalStateException("A node cannot be parent of itself: $node")
            it.parent = node
            assignParents(it)
        }
    }
}
