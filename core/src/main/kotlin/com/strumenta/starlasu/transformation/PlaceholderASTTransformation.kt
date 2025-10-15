package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Origin
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.traversing.walkAncestors
import kotlin.reflect.KClass

/**
 * This indicates if the Node itself is marked as a placeholder. Note that the Node could not be directly marked
 * as such and still be the descendants of such type of Node. In other words it could be in a placeholder tree.
 * This operation is not expensive to perform.
 */
val ASTNode.isDirectlyPlaceholderASTTransformation: Boolean
    get() = this.origin is PlaceholderASTTransformation

/**
 * This indicates if the Node itself is marked as a placeholder or if any of its ancestors are. Note that the Node
 * could not be directly marked as such and still be the descendants of such type of Node. In other words it could be
 * in a placeholder tree.
 * This operation is expensive to perform.
 */
val ASTNode.isDirectlyOrIndirectlyAPlaceholderASTTransformation: Boolean
    get() =
        this.isDirectlyPlaceholderASTTransformation ||
            this.walkAncestors().any {
                it.isDirectlyPlaceholderASTTransformation
            }

/**
 * This is used to indicate that a Node represents some form of placeholders to be used in transformation.
 */
sealed class PlaceholderASTTransformation(
    val origin: Origin?,
    val message: String,
) : Origin {
    override val position: Position?
        get() = origin?.position
    override val sourceText: String?
        get() = origin?.sourceText
}

/**
 * This is used to indicate that we do not know how to transform a certain node.
 */
class MissingASTTransformation(
    origin: Origin?,
    val transformationSource: Any?,
    val expectedType: KClass<out Node>? = null,
    message: String =
        "Translation of a node is not yet implemented: " +
            "${if (transformationSource is Node) transformationSource.simpleNodeType else transformationSource}" +
            if (expectedType != null) " into $expectedType" else "",
) : PlaceholderASTTransformation(origin, message) {
    constructor(transformationSource: Node, expectedType: KClass<out Node>? = null) : this(
        transformationSource,
        transformationSource,
        expectedType,
    )
}

/**
 * This is used to indicate that, while we had a transformation for a given node, that failed.
 * This is typically the case because the transformation covers only certain case and we encountered
 * one that was not covered.
 */
class FailingASTTransformation(
    origin: Origin?,
    message: String,
) : PlaceholderASTTransformation(origin, message)
