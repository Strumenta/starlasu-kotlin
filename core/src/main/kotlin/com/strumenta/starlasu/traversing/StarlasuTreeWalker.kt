package com.strumenta.starlasu.traversing

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Multiplicity
import com.strumenta.starlasu.model.PropertyDescription.Companion.multiplicity
import com.strumenta.starlasu.model.nodeOriginalProperties
import com.strumenta.starlasu.model.providesNodes
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface StarlasuTreeWalker {
    fun <N : ASTNode> walkChildren(node: N): Sequence<ASTNode>

    fun walk(node: ASTNode): Sequence<ASTNode>

    fun <N : ASTNode> assignParents(node: N)
}

class CommonStarlasuTreeWalker : StarlasuTreeWalker {
    // Cache stores a factory that returns the children list directly (no Sequence/coroutine overhead).
    // We use java Class as key because they have faster hashCode/equals than KClass.
    private val calculatorCaches = ConcurrentHashMap<Class<out ASTNode>, Function1<ASTNode, List<ASTNode>>>()

    // Tracks which classes override getOriginalProperties() (e.g. Java nodes that use
    // JavaBeans reflection instead of Kotlin memberProperties).
    private val overridesOriginalPropertiesCache = ConcurrentHashMap<Class<out ASTNode>, Boolean>()

    private fun overridesOriginalProperties(javaClass: Class<out ASTNode>): Boolean =
        overridesOriginalPropertiesCache.computeIfAbsent(javaClass) { clz ->
            try {
                clz.getMethod("getOriginalProperties").declaringClass != ASTNode::class.java
            } catch (_: NoSuchMethodException) {
                false
            }
        }

    /**
     * Returns the direct children of [node] as a List.
     * Returns [emptyList] (singleton) for leaf nodes — no allocation.
     */
    internal fun walkChildrenToList(node: ASTNode): List<ASTNode> {
        // Given that determining how to calculate children for a given node requires examining the class,
        // we cache the examination part, and we get a lambda that, given a node will give us the children
        // We then invoke such lambda on a given node
        val calculator =
            calculatorCaches.computeIfAbsent(node.javaClass) { javaClass ->
                if (overridesOriginalProperties(javaClass)) {
                    // Fall back to the PropertyDescription path for classes that override
                    // getOriginalProperties() (e.g. Java nodes using JavaBeans reflection).
                    return@computeIfAbsent { n ->
                        val result = ArrayList<ASTNode>()
                        n.originalProperties.forEach { property ->
                            when (val value = property.value) {
                                is ASTNode -> result.add(value)
                                is Collection<*> -> value.forEach { if (it is ASTNode) result.add(it) }
                            }
                        }
                        result
                    }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val kClass = javaClass.kotlin as KClass<ASTNode>
                    val propsArray =
                        kClass.nodeOriginalProperties
                            .filter { providesNodes(it) }
                            .toTypedArray() as Array<KProperty1<ASTNode, *>>
                    val manyArray =
                        BooleanArray(propsArray.size) { i ->
                            multiplicity(propsArray[i]) == Multiplicity.MANY
                        }

                    return@computeIfAbsent { n ->
                        // Defer ArrayList creation until we actually find a child.
                        // Leaf nodes pay zero allocation cost (returns emptyList singleton).
                        var result: ArrayList<ASTNode>? = null
                        for (i in propsArray.indices) {
                            val value = propsArray[i].get(n)
                            if (value != null) {
                                if (manyArray[i]) {
                                    @Suppress("UNCHECKED_CAST")
                                    val collectionValue = value as Collection<ASTNode>
                                    if (collectionValue.isNotEmpty()) {
                                        if (result == null) result = ArrayList(collectionValue.size)
                                        result.addAll(collectionValue)
                                    }
                                } else {
                                    if (result == null) result = ArrayList(2)
                                    result.add(value as ASTNode)
                                }
                            }
                        }
                        result ?: emptyList()
                    }
                }
            }
        return calculator.invoke(node)
    }

    override fun <N : ASTNode> walkChildren(node: N): Sequence<ASTNode> = walkChildrenToList(node).asSequence()

    override fun walk(node: ASTNode): Sequence<ASTNode> =
        sequence {
            val stack = ArrayDeque<ASTNode>()
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
    internal fun collectLeavesFirst(
        node: ASTNode,
        result: ArrayList<ASTNode>,
    ) {
        val children = walkChildrenToList(node)
        for (child in children) collectLeavesFirst(child, result)
        result.add(node)
    }

    override fun <N : ASTNode> assignParents(node: N) {
        walkChildrenToList(node).forEach {
            if (it == node) throw IllegalStateException("A node cannot be parent of itself: $node")
            it.parent = node
            assignParents(it)
        }
    }
}
