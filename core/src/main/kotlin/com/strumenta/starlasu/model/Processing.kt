@file:JvmName("Processing")

package com.strumenta.starlasu.model

import com.strumenta.starlasu.traversing.children
import com.strumenta.starlasu.traversing.searchByType
import com.strumenta.starlasu.traversing.walk
import com.strumenta.starlasu.traversing.walkChildren
import java.util.IdentityHashMap
import kotlin.reflect.KFunction1
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Sets or corrects the parent of all AST nodes.
 * Kolasu does not see set/add/delete operations on the AST nodes,
 * so this function should be called manually after modifying the AST.
 */
fun ASTNode.assignParents() {
    this.walkChildren().forEach {
        if (it == this) {
            throw java.lang.IllegalStateException("A node cannot be parent of itself: $this")
        }
        it.parent = this
        it.assignParents()
    }
}

/**
 * Recursively execute [operation] on [this] node, and all nodes below this node.
 * @param walker the function that generates the nodes to operate on in the desired sequence.
 */
fun ASTNode.processNodes(
    operation: (ASTNode) -> Unit,
    walker: KFunction1<ASTNode, Sequence<ASTNode>> = ASTNode::walk,
) {
    walker.invoke(this).forEach(operation)
}

fun ASTNode.invalidPositions(): Sequence<ASTNode> =
    this.walk().filter {
        it.position == null ||
            run {
                val parentPos = it.parent?.position
                // If the parent position is null, we can't say anything about this node's position
                (
                    parentPos != null &&
                        !(
                            parentPos.contains(it.position!!.start) &&
                                parentPos.contains(it.position!!.end)
                        )
                )
            }
    }

fun ASTNode.findInvalidPosition(): ASTNode? = this.invalidPositions().firstOrNull()

fun ASTNode.hasValidParents(parent: ASTNode? = this.parent): Boolean =
    this.parent == parent &&
        this.children.all {
            it.hasValidParents(this)
        }

fun <T : ASTNode> T.withParent(parent: ASTNode?): T {
    this.parent = parent
    return this
}

/**
 * Executes an operation on the properties of a node.
 * @param propertiesToIgnore which properties to ignore
 * @param propertyOperation the operation to perform on each property.
 */
@JvmOverloads
fun Node.processProperties(
    propertiesToIgnore: Set<String> = emptySet(),
    propertyOperation: (PropertyDescription) -> Unit,
) {
    this.properties.filter { it.name !in propertiesToIgnore }.forEach {
        try {
            propertyOperation(it)
        } catch (t: Throwable) {
            throw java.lang.RuntimeException("Issue processing property $it in $this", t)
        }
    }
}

/**
 * Executes an operation on the properties of a node.
 * @param propertiesToIgnore which properties to ignore
 * @param propertyOperation the operation to perform on each property.
 */
@JvmOverloads
fun ASTNode.processOriginalProperties(
    propertiesToIgnore: Set<String> = emptySet(),
    propertyOperation: (PropertyDescription) -> Unit,
) {
    this.originalProperties.filter { it.name !in propertiesToIgnore }.forEach {
        try {
            propertyOperation(it)
        } catch (t: Throwable) {
            throw java.lang.RuntimeException("Issue processing property $it in $this", t)
        }
    }
}

/**
 * Executes an operation on the properties definitions of a node class.
 * @param propertiesToIgnore which properties to ignore
 * @param propertyTypeOperation the operation to perform on each property.
 */
fun <T : Any> Class<T>.processProperties(
    propertiesToIgnore: Set<String> = emptySet(),
    propertyTypeOperation: (PropertyTypeDescription) -> Unit,
) = kotlin.processProperties(propertiesToIgnore, propertyTypeOperation)

/**
 * @param walker the function that generates the nodes to operate on in the desired sequence.
 * @return the first node in the AST for which the [predicate] is true. Null if none are found.
 */
fun ASTNode.find(
    predicate: (ASTNode) -> Boolean,
    walker: KFunction1<ASTNode, Sequence<ASTNode>> = ASTNode::walk,
): ASTNode? = walker.invoke(this).find(predicate)

/**
 * Recursively execute [operation] on this node, and all nodes below this node that extend [klass].
 *
 * T is not forced to be a subtype of Node to support using interfaces.
 *
 * @param walker the function that generates the nodes to operate on in the desired sequence.
 */
fun <T> ASTNode.processNodesOfType(
    klass: Class<T>,
    operation: (T) -> Unit,
    walker: KFunction1<ASTNode, Sequence<ASTNode>> = ASTNode::walk,
) {
    searchByType(klass, walker).forEach(operation)
}

/**
 * Recursively execute [operation] on this node, and all nodes below this node.
 * Every node is informed about its [parent] node. (But not about the parent's parent!)
 */
fun ASTNode.processConsideringDirectParent(
    operation: (ASTNode, ASTNode?) -> Unit,
    parent: ASTNode? = null,
) {
    operation(this, parent)
    this.properties.forEach { p ->
        when (val v = p.value) {
            is ASTNode -> v.processConsideringDirectParent(operation, this)
            is Collection<*> -> v.forEach { (it as? ASTNode)?.processConsideringDirectParent(operation, this) }
        }
    }
}

/**
 * @return all direct children of this node.
 */
val ASTNode.children: List<ASTNode>
    get() {
        val children = mutableListOf<ASTNode>()
        this.originalProperties.forEach { p ->
            val v = p.value
            when (v) {
                is Node -> children.add(v)
                is Collection<*> -> v.forEach { if (it is Node) children.add(it) }
            }
        }
        return children
    }

/**
 * @return the next sibling node. Notice that children of a sibling collection are considered siblings
 * and not the collection itself.
 */
val ASTNode.nextSibling: ASTNode?
    get() {
        if (this.parent != null) {
            val siblings = this.parent!!.children
            val index = siblings.indexOf(this)
            return if (index == siblings.size - 1) null else siblings[index + 1]
        }
        return null
    }

/**
 * @return the previous sibling. Notice that children of a sibling collection are considered siblings
 * and not the collection itself.
 */
val ASTNode.previousSibling: ASTNode?
    get() {
        if (this.parent != null) {
            val siblings = this.parent!!.children
            val index = siblings.indexOf(this)
            return if (index == 0) null else siblings[index - 1]
        }
        return null
    }

/**
 * @return the next sibling node in the same property.
 */
val Node.nextSamePropertySibling: Node?
    get() {
        if (this.parent != null) {
            val siblings =
                this.parent!!
                    .properties
                    .find { p ->
                        val v = p.value
                        when (v) {
                            is Collection<*> -> v.contains(this)
                            else -> false
                        }
                    }?.value as? Collection<*> ?: emptyList<Node>()

            val index = siblings.indexOf(this)
            return if (index == siblings.size - 1 || index == -1) null else siblings.elementAt(index + 1) as Node
        }
        return null
    }

/**
 * @return the previous sibling in the same property.
 */
val Node.previousSamePropertySibling: Node?
    get() {
        if (this.parent != null) {
            val siblings =
                this.parent!!
                    .properties
                    .find { p ->
                        val v = p.value
                        when (v) {
                            is Collection<*> -> v.contains(this)
                            else -> false
                        }
                    }?.value as? Collection<*> ?: emptyList<Node>()

            val index = siblings.indexOf(this)
            return if (index == 0 || index == -1) null else siblings.elementAt(index - 1) as Node
        }
        return null
    }

/**
 * Return the property containing this Node, if any. Null should be returned for root nodes.
 */
fun ASTNode.containingProperty(): PropertyDescription? {
    if (this.parent == null) {
        return null
    }
    return this.parent!!.properties.find { p ->
        val v = p.value
        when {
            v is Collection<*> -> v.any { it === this }
            v === this -> true
            else -> false
        }
    } ?: throw IllegalStateException("No containing property for $this with parent ${this.parent}")
}

/**
 * Return the index of this Node within the containing property. The return value is null for root nodes.
 * The index is always 0 for Nodes in singular containment properties.
 */
fun ASTNode.indexInContainingProperty(): Int? {
    val p = this.containingProperty()
    return if (p == null) {
        null
    } else {
        val value = p.value
        if (value is Collection<*>) {
            value.indexOfFirst { this === it }
        } else {
            0
        }
    }
}

/**
 * @return the next sibling of the specified type. Notice that children of a sibling collection are considered siblings
 * and not the collection itself.
 */
inline fun <reified T : ASTNode> ASTNode.nextSibling(): ASTNode? {
    if (this.parent != null) {
        val siblings = this.parent!!.children
        return siblings.takeLast(siblings.size - 1 - siblings.indexOf(this)).firstOrNull { it is T }
    }
    return null
}

/**
 * @return the previous sibling of the specified type. Notice that children of a sibling collection are considered siblings
 * and not the collection itself.
 */
inline fun <reified T : ASTNode> ASTNode.previousSibling(): ASTNode? {
    if (this.parent != null) {
        val siblings = this.parent!!.children
        return siblings.take(siblings.indexOf(this)).lastOrNull { it is T }
    }
    return null
}

// TODO reimplement using transformChildren
fun Node.transformTree(
    operation: (Node) -> Node,
    inPlace: Boolean = false,
    mutationsCache: IdentityHashMap<Node, Node> = IdentityHashMap<Node, Node>(),
): Node {
    if (inPlace) TODO()
    mutationsCache.computeIfAbsent(this) { operation(this) }
    val changes = mutableMapOf<String, Any>()
    relevantMemberProperties().forEach { p ->
        when (val v = p.get(this)) {
            is Node -> {
                val newValue = v.transformTree(operation, inPlace, mutationsCache)
                if (newValue != v) changes[p.name] = newValue
            }

            is Collection<*> -> {
                val newValue = v.map { if (it is Node) it.transformTree(operation, inPlace, mutationsCache) else it }
                if (newValue != v) changes[p.name] = newValue
            }
        }
    }
    var instanceToTransform = this
    if (changes.isNotEmpty()) {
        val constructor = this.javaClass.kotlin.primaryConstructor!!
        val params = mutableMapOf<KParameter, Any?>()
        constructor.parameters.forEach { param ->
            if (changes.containsKey(param.name)) {
                params[param] = changes[param.name]
            } else {
                params[param] =
                    this.javaClass.kotlin.memberProperties
                        .find { param.name == it.name }!!
                        .get(this)
            }
        }
        instanceToTransform = constructor.callBy(params)
    }
    return mutationsCache.computeIfAbsent(instanceToTransform) { operation(instanceToTransform) }
}

class ImmutablePropertyException(
    property: KProperty<*>,
    node: ASTNode,
) : RuntimeException("Cannot mutate property '${property.name}' of node $node (class: ${node.javaClass.canonicalName})")

// assumption: every MutableList in the AST contains Nodes.
@Suppress("UNCHECKED_CAST")
fun ASTNode.transformChildren(operation: (ASTNode) -> ASTNode) {
    nodeProperties.forEach { property ->
        when (val value = property.get(this)) {
            is Node -> {
                val newValue = operation(value)
                if (newValue != value) {
                    if (property is KMutableProperty<*>) {
                        property.setter.call(this, newValue)
                        newValue.parent = this
                    } else {
                        throw ImmutablePropertyException(property, this)
                    }
                }
            }

            is Collection<*> -> {
                if (value is List<*>) {
                    for (i in 0 until value.size) {
                        val element = value[i]
                        if (element is Node) {
                            val newValue = operation(element)
                            if (newValue != element) {
                                if (value is MutableList<*>) {
                                    (value as MutableList<ASTNode>)[i] = newValue
                                    newValue.parent = this
                                } else {
                                    throw ImmutablePropertyException(property, element)
                                }
                            }
                        }
                    }
                } else {
                    throw UnsupportedOperationException(
                        "Only modifications in a List and MutableList are supported, not ${value::class}",
                    )
                }
            }
        }
    }
}

fun ASTNode.mapChildren(operation: (ASTNode) -> ASTNode): ASTNode {
    val changes = mutableMapOf<String, Any>()
    relevantMemberProperties().forEach { property ->
        when (val value = property.get(this)) {
            is Node -> {
                val newValue = operation(value)
                if (newValue != value) {
                    changes[property.name] = newValue
                }
            }

            is Collection<*> -> {
                val newValue = value.map { if (it is Node) operation(it) else it }
                if (newValue != value) {
                    changes[property.name] = newValue
                }
            }
        }
    }
    var instanceToTransform = this
    if (changes.isNotEmpty()) {
        val constructor = this.javaClass.kotlin.primaryConstructor!!
        val params = mutableMapOf<KParameter, Any?>()
        constructor.parameters.forEach { param ->
            if (changes.containsKey(param.name)) {
                params[param] = changes[param.name]
            } else {
                params[param] =
                    this.javaClass.kotlin.memberProperties
                        .find { param.name == it.name }!!
                        .get(this)
            }
        }
        instanceToTransform = constructor.callBy(params)
    }
    return instanceToTransform
}

/**
 * Replace [this] node with [other] (by modifying the children of the parent node.)
 * For this to work, [Node.assignParents] must have been called.
 *
 * Note that we recognize the exact same Node, looking at its identity, not using
 * equality.
 */
fun ASTNode.replaceWith(other: ASTNode) {
    if (this.parent == null) {
        throw IllegalStateException("Parent not set")
    }
    this.parent!!.transformChildren { if (it === this) other else it }
}

/**
 * Looks for [oldNode] in the lists of nodes in this node.
 * When found, it is removed, and in its place the [newNodes] are inserted.
 * When not found, an [IllegalStateException] is thrown.
 */
fun ASTNode.replaceWithSeveral(
    oldNode: ASTNode,
    newNodes: List<ASTNode>,
) {
    findMutableListContainingChild(oldNode) { nodeList, index ->
        nodeList.replaceWithSeveral(index, newNodes)
        oldNode.parent = null
        newNodes.forEach { node -> node.parent = this }
    }
}

/**
 * Looks for [targetNode] in the lists of nodes in this node.
 * When found, it is removed.
 * When not found, an [IllegalStateException] is thrown.
 */
fun ASTNode.removeFromList(targetNode: ASTNode) {
    findMutableListContainingChild(targetNode) { nodeList, index ->
        nodeList.removeAt(index)
        targetNode.parent = null
    }
}

/**
 * Looks for [targetNode] in the lists of nodes in this node.
 * When found, [newNodes] are inserted before it.
 * When not found, an [IllegalStateException] is thrown.
 */
fun ASTNode.addSeveralBefore(
    targetNode: ASTNode,
    newNodes: List<ASTNode>,
) {
    findMutableListContainingChild(targetNode) { nodeList, index ->
        nodeList.addSeveralBefore(index, newNodes)
        newNodes.forEach { node -> node.parent = this }
    }
}

/**
 * Looks for [targetNode] in the lists of nodes in this node.
 * When found, [newNodes] are inserted after it.
 * When not found, an [IllegalStateException] is thrown.
 */
fun ASTNode.addSeveralAfter(
    targetNode: ASTNode,
    newNodes: List<ASTNode>,
) {
    findMutableListContainingChild(targetNode) { nodeList, index ->
        nodeList.addSeveralAfter(index, newNodes)
        newNodes.forEach { node -> node.parent = this }
    }
}

/**
 * Supports functions that manipulate a list of child nodes by finding [targetNode] in the [MutableList]s of nodes contained in [this] node.
 */
@Suppress("UNCHECKED_CAST") // assumption: a MutableList with a Node in it is a MutableList<Node>
private fun ASTNode.findMutableListContainingChild(
    targetNode: ASTNode,
    whenFoundDo: (nodeList: MutableList<ASTNode>, index: Int) -> Unit,
) {
    relevantMemberProperties().forEach { property ->
        when (val value = property.get(this)) {
            is MutableList<*> -> {
                for (i in 0 until value.size) {
                    // We want to find a particular child, not just one which is equal to it
                    if (value[i] === targetNode) {
                        whenFoundDo(value as MutableList<ASTNode>, i)
                        return
                    }
                }
            }
        }
    }
    throw IllegalStateException("Did not find $targetNode in any MutableList in $this.")
}

/**
 * Replaces [this] node with any amount of other nodes if it is in a [MutableList].
 * <p/>Looks for [this] in the lists of nodes in the parent node.
 * When found, [this] is removed, and in its place [newNodes] are inserted.
 * For this to work, [Node.assignParents] must have been called.
 */
fun ASTNode.replaceWithSeveral(newNodes: List<ASTNode>) {
    parent?.replaceWithSeveral(this, newNodes) ?: throw IllegalStateException("Parent not set")
}

/**
 * Inserts the [newNodes] before [this] node if it is in a [MutableList].
 * For this to work, [Node.assignParents] must have been called.
 */
fun ASTNode.addSeveralBefore(newNodes: List<ASTNode>) {
    parent?.addSeveralBefore(this, newNodes) ?: throw IllegalStateException("Parent not set")
}

/**
 * Inserts the [newNodes] after [this] node if it is in a [MutableList].
 * For this to work, [Node.assignParents] must have been called.
 */
fun ASTNode.addSeveralAfter(newNodes: List<ASTNode>) {
    parent?.addSeveralAfter(this, newNodes) ?: throw IllegalStateException("Parent not set")
}

/**
 * Removes [this] node from the parent if it is in a [MutableList].
 * For this to work, [Node.assignParents] must have been called.
 */
fun ASTNode.removeFromList() {
    parent?.removeFromList(this) ?: throw IllegalStateException("Parent not set")
}

/**
 * Replaces the element at [index] with [replacements].
 */
fun <T> MutableList<T>.replaceWithSeveral(
    index: Int,
    replacements: List<T>,
) {
    removeAt(index)
    addAll(index, replacements)
}

/**
 * Replaces the element at [index] with [additions].
 */
fun <T> MutableList<T>.addSeveralBefore(
    index: Int,
    additions: List<T>,
) {
    addAll(index, additions)
}

/**
 * Replaces the element at [index] with [additions].
 */
fun <T> MutableList<T>.addSeveralAfter(
    index: Int,
    additions: List<T>,
) {
    addAll(index + 1, additions)
}
