package com.strumenta.starlasu.semantics.scope.provider.declarative

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.semantics.scope.description.ScopeDescription
import com.strumenta.starlasu.semantics.scope.description.ScopeDescriptionApi
import com.strumenta.starlasu.semantics.scope.provider.ScopeProvider
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSuperclassOf

/**
 * Utility function for defining scoping rules.
 **/
inline fun <
    reified NodeTy : ASTNode,
    PropertyTy : KProperty1<in NodeTy, ReferenceByName<out PossiblyNamed>?>,
> scopeFor(
    property: PropertyTy,
    ignoreCase: Boolean = false,
    noinline specification: ScopeDescriptionApi.(DeclarativeScopeProviderRuleContext<NodeTy>) -> Unit,
) = DeclarativeScopeProviderRule(NodeTy::class, property.name, ignoreCase, specification)

/**
 * A [ScopeProvider] defined by a list of [scopeFor] rules.
 *
 * For a given node and reference property, the first rule whose node type is a supertype of the node's type and whose
 * property name matches is applied; rules for more specific node types take precedence. If no rule matches, a
 * [RuntimeException] is thrown.
 *
 * ```kotlin
 * object MyScopeProvider : DeclarativeScopeProvider(
 *     scopeFor(Todo::prerequisite) {
 *         // local symbols: named nodes of the same AST
 *         (it.node.parent as TodoProject).todos.forEach(this::define)
 *         // external symbols, e.g. from a SymbolRepository
 *         symbolRepository.find(Todo::class).forEach { symbol -> define(symbol.name, symbol) }
 *     },
 * )
 * ```
 **/
open class DeclarativeScopeProvider(
    vararg rules: DeclarativeScopeProviderRule<out ASTNode>,
) : ScopeProvider {
    private val rules: MutableList<DeclarativeScopeProviderRule<out ASTNode>> = rules.sorted().toMutableList()

    fun addRule(rule: DeclarativeScopeProviderRule<out ASTNode>) {
        rules.add(rule)
        rules.sort()
    }

    override fun <NodeType : ASTNode> scopeFor(
        node: NodeType,
        reference: KProperty1<in NodeType, ReferenceByName<out PossiblyNamed>?>,
    ): ScopeDescription =
        this.rules
            .firstOrNull { it.canBeInvokedWith(node::class, reference) }
            ?.invoke(this, node)
            ?: throw RuntimeException(
                "Cannot find scoping rule for reference ${node::class.qualifiedName}::${reference.name}",
            )
}

/**
 * Represents a scoping rule, i.e. the body of `scopeFor(...)` definitions.
 **/
class DeclarativeScopeProviderRule<NodeTy : ASTNode>(
    private val nodeType: KClass<NodeTy>,
    private val propertyName: String,
    private val ignoreCase: Boolean,
    private val specification: ScopeDescription.(DeclarativeScopeProviderRuleContext<NodeTy>) -> Unit,
) : (ScopeProvider, ASTNode) -> ScopeDescription,
    Comparable<DeclarativeScopeProviderRule<out ASTNode>> {
    override fun invoke(
        scopeProvider: ScopeProvider,
        node: ASTNode,
    ): ScopeDescription =
        ScopeDescription(this.ignoreCase).apply {
            @Suppress("UNCHECKED_CAST")
            val context = DeclarativeScopeProviderRuleContext(node as NodeTy, scopeProvider)
            this.specification(context)
        }

    fun canBeInvokedWith(
        nodeType: KClass<*>,
        property: KProperty1<*, ReferenceByName<out PossiblyNamed>?>,
    ): Boolean = this.nodeType.isSuperclassOf(nodeType) && this.propertyName == property.name

    override fun compareTo(other: DeclarativeScopeProviderRule<out ASTNode>): Int =
        when {
            this.nodeType.isSuperclassOf(other.nodeType) -> 1
            other.nodeType.isSuperclassOf(this.nodeType) -> -1
            else -> this.describeForComparison() compareTo other.describeForComparison()
        }

    private fun describeForComparison(): String = "${this.nodeType.qualifiedName ?: ""}::${this.propertyName}"
}

/**
 * Represents the available context when defining scoping rules,
 * i.e. the `it` variable in `scopeFor(...)` bodies.
 **/
data class DeclarativeScopeProviderRuleContext<NodeTy : ASTNode>(
    val node: NodeTy,
    val scopeProvider: ScopeProvider,
)
