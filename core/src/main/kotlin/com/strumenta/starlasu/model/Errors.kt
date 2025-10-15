package com.strumenta.starlasu.model

import com.strumenta.starlasu.traversing.walkDescendants

/**
 * An AST node that marks the presence of an error, for example a syntactic or semantic error in the original tree.
 */
interface ErrorNode : ASTNode {
    val message: String
    override var position: Position?
}

/**
 * Generic implementation of [ErrorNode].
 */
class GenericErrorNode(
    error: Exception? = null,
    message: String? = null,
) : Node(),
    ErrorNode {
    override val message: String =
        message
            ?: error?.message() ?: "Unspecified error node"

    private fun Throwable.message(): String {
        val cause = this.cause?.message()?.let { " -> $it" } ?: ""
        val explanation = if (this.message != null) ": " + this.message else ""
        return "${this.javaClass.simpleName}$explanation$cause"
    }
}

fun Node.errors(): Sequence<ErrorNode> = this.walkDescendants(ErrorNode::class)

fun Node.findError(): ErrorNode? = this.errors().firstOrNull()
