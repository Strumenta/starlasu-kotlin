@file:JvmName("ProcessingByPosition")

package com.strumenta.kolasu.traversing

import com.strumenta.kolasu.model.ASTNode
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Position

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
): Sequence<ASTNode> {
    val contains = this.contains(position)
    if (!selfContained || contains) {
        if (children.isEmpty()) {
            return if (contains) sequenceOf(this) else emptySequence()
        } else {
            for (c in children) {
                val seq = c.searchByPosition(position, selfContained).iterator()
                if (seq.hasNext()) {
                    return sequence {
                        yield(this@searchByPosition)
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
