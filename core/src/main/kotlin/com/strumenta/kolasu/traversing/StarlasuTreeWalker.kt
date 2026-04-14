package com.strumenta.kolasu.traversing

import com.strumenta.kolasu.model.Multiplicity
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.PropertyDescription.Companion.multiplicity
import com.strumenta.kolasu.model.nodeOriginalProperties
import com.strumenta.kolasu.model.providesNodes
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

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
        overridesOriginalPropertiesCache[javaClass]
            ?: overridesOriginalPropertiesCache.computeIfAbsent(javaClass) { clz ->
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
    internal fun walkChildrenToList(node: Node): List<Node> {
        // Given that determining how to calculate children for a given node requires examining the class,
        // we cache the examination part, and we get a lambda that, given a node will give us the children
        // We then invoke such lambda on a given node
        val calculator = calculatorCaches[node.javaClass]
            ?: calculatorCaches.computeIfAbsent(node.javaClass) { javaClass ->
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
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val kClass = javaClass.kotlin as KClass<Node>
                    val props = kClass.nodeOriginalProperties.filter { providesNodes(it) }
                    val manyArray = BooleanArray(props.size) { i ->
                        multiplicity(props.elementAt(i)) == Multiplicity.MANY
                    }

                    // Precompute a raw accessor for each property.
                    // KProperty1.javaField returns the backing field for simple `val` properties.
                    // Fall back to KProperty1.get() only for computed / delegated properties.
                    @Suppress("UNCHECKED_CAST")
                    val accessors: Array<(Node) -> Any?> = props.map { prop ->
                        val field = (prop as KProperty1<Node, *>).javaField
                        if (field != null) {
                            field.isAccessible = true
                            // Field.get(instance) — no vararg Object[] allocation
                            { n: Node -> field.get(n) }
                        } else {
                            // Computed or delegated property: fall back to KProperty1.get()
                            { n: Node -> prop.get(n) }
                        }
                    }.toTypedArray()

                    return@computeIfAbsent { n ->
                        // Defer ArrayList creation until we actually find a child.
                        // Leaf nodes pay zero allocation cost (returns emptyList singleton).
                        var result: ArrayList<Node>? = null
                        for (i in accessors.indices) {
                            val value = accessors[i](n)
                            if (value != null) {
                                if (manyArray[i]) {
                                    @Suppress("UNCHECKED_CAST")
                                    val collectionValue = value as Collection<Node>
                                    if (collectionValue.isNotEmpty()) {
                                        if (result == null) result = ArrayList(collectionValue.size)
                                        result.addAll(collectionValue)
                                    }
                                } else {
                                    if (result == null) result = ArrayList(2)
                                    result.add(value as Node)
                                }
                            }
                        }
                        result ?: emptyList()
                    }
                }
            }
        return calculator.invoke(node)
    }

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
