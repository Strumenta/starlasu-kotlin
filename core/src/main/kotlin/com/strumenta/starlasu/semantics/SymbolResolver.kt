package com.strumenta.starlasu.semantics

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.KReferenceByName
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.getReferredType
import com.strumenta.starlasu.model.kReferenceByNameProperties
import com.strumenta.starlasu.traversing.walkChildren
import kotlin.reflect.KClass

// instance
@Deprecated("The corresponding component in the semantics module should be used instead.")
class SymbolResolver(
    private val scopeProvider: ScopeProvider = ScopeProvider(),
) {
    fun loadFrom(
        configuration: SymbolResolverConfiguration,
        semantics: Semantics,
    ) {
        this.scopeProvider.loadFrom(configuration.scopeProvider, semantics)
    }

    @Suppress("unchecked_cast")
    fun resolve(node: ASTNode) {
        node.kReferenceByNameProperties().forEach { this.resolve(it as KReferenceByName<out ASTNode>, node) }
        node.walkChildren().forEach(this::resolve)
    }

    @Suppress("unchecked_cast")
    fun resolve(
        property: KReferenceByName<out ASTNode>,
        node: ASTNode,
    ) {
        (node.properties.find { it.name == property.name }?.value as ReferenceByName<PossiblyNamed>?)?.apply {
            this.referred = scopeProvider.scopeFor(property, node).resolve(this.name, property.getReferredType())
        }
    }

    fun scopeFor(
        property: KReferenceByName<out Node>,
        node: Node? = null,
    ): Scope = this.scopeProvider.scopeFor(property, node)

    fun scopeFrom(node: Node? = null): Scope = this.scopeProvider.scopeFrom(node)
}

// configuration
@Deprecated("The corresponding component in the semantics module should be used instead.")
class SymbolResolverConfiguration(
    val scopeProvider: ScopeProviderConfiguration = ScopeProviderConfiguration(),
) {
    inline fun <reified N : Node> scopeFor(
        referenceByName: KReferenceByName<N>,
        crossinline scopeResolutionRule: Semantics.(N) -> Scope,
    ) = this.scopeProvider.scopeFor(referenceByName, scopeResolutionRule)

    inline fun <reified N : Node> scopeFrom(
        nodeType: KClass<N>,
        crossinline scopeConstructionRule: Semantics.(N) -> Scope,
    ) = this.scopeProvider.scopeFrom(nodeType, scopeConstructionRule)
}

// builder

fun symbolResolver(init: SymbolResolverConfiguration.() -> Unit) = SymbolResolverConfiguration().apply(init)
