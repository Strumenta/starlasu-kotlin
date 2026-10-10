@file:JvmName("ProcessingByPosition")

package com.strumenta.starlasu.traversing

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.positions

/**
 * @param position the position where to search for nodes
 * @param selfContained whether the starting node position contains the positions of all its children.
 * If **true** no further search will be performed in subtrees where the root node falls outside the given position.
 * If **false (default)** the research will cover all nodes from the starting node to the leaves.
 * @return the node most closely containing the given [position]. Null if none is found.
 * @see searchByPosition
 */
@JvmOverloads
fun ASTNode.findByPosition(
    position: Position,
    selfContained: Boolean = false,
): ASTNode? = this.searchByPosition(position, selfContained).lastOrNull()

/**
 * @param position the position where to search for nodes
 * @param selfContained whether the starting node position contains the positions of all its children.
 * If **true**: no further search will be performed in subtrees where the root node falls outside the given position.
 * If **false (default)**: the search will cover all nodes from the starting node to the leaves.
 * In any case, the search stops at the first subtree found to be containing the position.
 * @return all nodes containing the given [position] using depth-first search. Empty list if none are found.
 */
@JvmOverloads
fun ASTNode.searchByPosition(
    position: Position,
    selfContained: Boolean = false,
): Sequence<ASTNode> = searchContaining(selfContained) { it.contains(position) }

/**
 * Like [findByPosition], but matches nodes by the positions of their [ASTNode.destination] (see [positions])
 * rather than by their own position. For example, after code generation, this finds the node
 * that produced the code at the given position in the generated text.
 *
 * Destination positions are not assumed to nest hierarchically, so the whole tree is searched.
 *
 * @param position the position where to search for nodes, in the destination
 * @return the node whose destination most closely contains the given [position]. Null if none is found.
 * @see searchByDestinationPosition
 */
fun ASTNode.findByDestinationPosition(position: Position): ASTNode? =
    this.searchByDestinationPosition(position).lastOrNull()

/**
 * Like [searchByPosition], but matches nodes by the positions of their [ASTNode.destination] (see [positions])
 * rather than by their own position. A node matches if any of its destination positions contains [position].
 *
 * Destination positions are not assumed to nest hierarchically, so the whole tree is searched,
 * stopping at the first subtree found to be containing the position.
 *
 * @param position the position where to search for nodes, in the destination
 * @return the path from this node to the deepest node whose destination contains the given [position],
 * using depth-first search. Empty if none are found.
 */
fun ASTNode.searchByDestinationPosition(position: Position): Sequence<ASTNode> =
    searchContaining(selfContained = false) { node ->
        node.destination?.positions?.any { it.contains(position) } ?: false
    }

private fun ASTNode.searchContaining(
    selfContained: Boolean,
    containsPosition: (ASTNode) -> Boolean,
): Sequence<ASTNode> {
    val contains = containsPosition(this)
    if (!selfContained || contains) {
        if (children.isEmpty()) {
            return if (contains) sequenceOf(this) else emptySequence()
        } else {
            for (c in children) {
                val seq = c.searchContaining(selfContained, containsPosition).iterator()
                if (seq.hasNext()) {
                    return sequence {
                        yield(this@searchContaining)
                        yieldAll(seq)
                    }
                }
            }
            if (contains) {
                return sequenceOf(this)
            }
        }
    }
    return emptySequence()
}

/**
 * @param position the position within which the walk should remain
 * @return walks the AST within the given [position] starting from this node, depth-first.
 */
fun ASTNode.walkWithin(position: Position): Sequence<ASTNode> =
    if (position.contains(this)) {
        sequenceOf(this) + this.children.walkWithin(position)
    } else if (this.overlaps(position)) {
        this.children.walkWithin(position)
    } else {
        emptySequence()
    }

/**
 * @param position the position within which the walk should remain
 * @return walks the AST within the given [position] starting from each node
 * and concatenates all results in a single sequence
 */
fun List<ASTNode>.walkWithin(position: Position): Sequence<ASTNode> =
    this
        .map { it.walkWithin(position) }
        .reduceOrNull { previous, current -> previous + current } ?: emptySequence()
