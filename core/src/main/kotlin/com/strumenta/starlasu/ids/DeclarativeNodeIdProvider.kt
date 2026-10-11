package com.strumenta.starlasu.ids

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf

/**
 * A [SemanticNodeIDProvider] defined by a list of [idFor] rules.
 *
 * The first rule whose node type is a supertype of the node's type is applied; rules for more specific node types take
 * precedence. Nodes matched by no rule have no semantic identity.
 *
 * ```kotlin
 * object MyNodeIdProvider : DeclarativeNodeIdProvider(
 *     idFor<PackageDeclaration> { it.name },
 *     idFor<ClassDeclaration> { node ->
 *         val pkg = node.findAncestorOfType(PackageDeclaration::class.java)
 *         "${pkg?.let { semanticID(it) } ?: "__none__"}::${node.name}"
 *     },
 * )
 * ```
 **/
open class DeclarativeNodeIdProvider(
    vararg rules: DeclarativeNodeIdProviderRule<out ASTNode>,
) : SemanticNodeIDProvider {
    private val rules: List<DeclarativeNodeIdProviderRule<out ASTNode>> = rules.sorted()

    override fun hasSemanticIdentity(kNode: ASTNode): Boolean = tryToGetSemanticID(kNode) != null

    override fun semanticID(kNode: ASTNode): String =
        tryToGetSemanticID(kNode)
            ?: throw RuntimeException("Cannot find rule for node type: ${kNode::class.qualifiedName}")

    private fun tryToGetSemanticID(kNode: ASTNode): String? =
        this.rules
            .firstOrNull {
                it.canBeInvokedWith(kNode::class)
            }?.invoke(this, kNode)
}

/**
 * Utility function to define declarative node id provider rules.
 * Can be used whenever listing the rules in the DeclarativeNodeIdProvider constructor.
 **/
inline fun <reified NodeTy : Node> idFor(
    noinline specification: SemanticNodeIDProvider.(NodeTy) -> String,
): DeclarativeNodeIdProviderRule<NodeTy> = DeclarativeNodeIdProviderRule(NodeTy::class, specification)

/**
 * Class representing a single rule of a DeclarativeNodeIdProvider.
 **/
class DeclarativeNodeIdProviderRule<NodeTy : ASTNode>(
    private val nodeType: KClass<NodeTy>,
    private val specification: SemanticNodeIDProvider.(NodeTy) -> String,
) : Comparable<DeclarativeNodeIdProviderRule<*>>,
    (SemanticNodeIDProvider, ASTNode) -> String {
    override fun invoke(
        nodeIdProvider: SemanticNodeIDProvider,
        node: ASTNode,
    ): String {
        @Suppress("UNCHECKED_CAST")
        return nodeIdProvider.specification(node as NodeTy)
    }

    fun canBeInvokedWith(type: KClass<*>): Boolean = this.nodeType.isSuperclassOf(type)

    override fun compareTo(other: DeclarativeNodeIdProviderRule<*>): Int =
        when {
            this.nodeType.isSuperclassOf(other.nodeType) -> 1
            this.nodeType.isSubclassOf(other.nodeType) -> -1
            else -> (this.nodeType.qualifiedName ?: "") compareTo (other.nodeType.qualifiedName ?: "")
        }
}
