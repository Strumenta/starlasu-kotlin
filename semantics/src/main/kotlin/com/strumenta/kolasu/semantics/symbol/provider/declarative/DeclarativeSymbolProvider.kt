package com.strumenta.kolasu.semantics.symbol.provider.declarative

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.semantics.symbol.description.BooleanValueDescription
import com.strumenta.kolasu.semantics.symbol.description.ContainmentValueDescription
import com.strumenta.kolasu.semantics.symbol.description.IntegerValueDescription
import com.strumenta.kolasu.semantics.symbol.description.ListValueDescription
import com.strumenta.kolasu.semantics.symbol.description.NullValueDescription
import com.strumenta.kolasu.semantics.symbol.description.ReferenceValueDescription
import com.strumenta.kolasu.semantics.symbol.description.StringValueDescription
import com.strumenta.kolasu.semantics.symbol.description.SymbolDescription
import com.strumenta.kolasu.semantics.symbol.description.ValueDescription
import com.strumenta.kolasu.semantics.symbol.provider.SymbolProvider
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.safeCast

inline fun <reified NodeTy : Node> symbolFor(
    noinline specification: DeclarativeSymbolProvideRuleApi<NodeTy>.(
        DeclarativeSymbolProviderRuleContext<NodeTy>
    ) -> Unit
): DeclarativeSymbolProviderRule<NodeTy> {
    return DeclarativeSymbolProviderRule(NodeTy::class, specification)
}

open class DeclarativeSymbolProvider(
    private val nodeIdProvider: NodeIdProvider,
    vararg rules: DeclarativeSymbolProviderRule<out Node>
) : SymbolProvider {
    private val rules: List<DeclarativeSymbolProviderRule<out Node>> = rules.sorted()

    override fun symbolFor(node: Node): SymbolDescription? {
        return this.rules
            .firstOrNull { it.isCompatibleWith(node::class) }
            ?.invoke(nodeIdProvider, this, node)
    }
}

class DeclarativeSymbolProviderRule<NodeTy : Node>(
    private val nodeType: KClass<NodeTy>,
    private val specification: DeclarativeSymbolProvideRuleApi<NodeTy>.(
        DeclarativeSymbolProviderRuleContext<NodeTy>
    ) -> Unit
) : (NodeIdProvider, SymbolProvider, Node) -> SymbolDescription,
    Comparable<DeclarativeSymbolProviderRule<out Node>> {
    /**
     * Collects the name/properties produced by running the specification. A fresh
     * instance is created per [invoke]: the specification re-executes on each
     * invocation, so keeping this state on the rule itself would both race across
     * threads and require a lock held across user callbacks (specification code may
     * in turn resolve symbols through other rules).
     */
    private class Api<NodeTy : Node> : DeclarativeSymbolProvideRuleApi<NodeTy> {
        var name: (Node) -> String? = { null }
        val properties: MutableMap<String, (NodeIdProvider, Node) -> ValueDescription> = mutableMapOf()

        override fun name(name: String) {
            this.name = { name }
        }

        override fun include(property: KProperty1<in NodeTy, Any?>) {
            if (property.name == "name") {
                this.name = { node ->
                    @Suppress("UNCHECKED_CAST")
                    String::class.safeCast(property.get(node as NodeTy))
                }
            }
            this.properties[property.name] = { nodeIdProvider, node ->
                @Suppress("UNCHECKED_CAST")
                this.toValueDescription(nodeIdProvider, property.get(node as NodeTy))
            }
        }

        fun getName(node: NodeTy): String {
            return this.name(node)
                ?: throw RuntimeException("Symbol description name property not set for node: ${node::class.qualifiedName}")
        }

        fun getTypes(node: NodeTy): List<String> {
            return listOfNotNull(
                node::class.qualifiedName
            ) + node::class.allSuperclasses.mapNotNull(KClass<*>::qualifiedName)
        }

        fun getProperties(
            nodeIdProvider: NodeIdProvider,
            node: NodeTy
        ): Map<String, ValueDescription> {
            return this.properties.mapValues { (_, valueDescriptionProvider) ->
                valueDescriptionProvider(nodeIdProvider, node)
            }
        }

        private fun toValueDescription(
            nodeIdProvider: NodeIdProvider,
            source: Any?
        ): ValueDescription {
            return when (source) {
                is Boolean -> BooleanValueDescription(source)
                is Int -> IntegerValueDescription(source)
                is String -> StringValueDescription(source)
                is Node -> toContainmentValueDescription(nodeIdProvider, source)
                is ReferenceByName<*> -> toReferenceValueDescription(nodeIdProvider, source)
                is List<*> -> toListValueDescription(nodeIdProvider, source)
                null -> NullValueDescription
                else -> throw RuntimeException("Unsupported value description for ${source::class.qualifiedName}")
            }
        }

        private fun toReferenceValueDescription(
            nodeIdProvider: NodeIdProvider,
            source: ReferenceByName<*>
        ): ReferenceValueDescription {
            return ReferenceValueDescription(
                source.referred?.let { it as? Node }?.let { nodeIdProvider.id(it) }
            )
        }

        private fun toContainmentValueDescription(
            nodeIdProvider: NodeIdProvider,
            source: Node
        ): ContainmentValueDescription {
            return ContainmentValueDescription(nodeIdProvider.id(source))
        }

        private fun toListValueDescription(
            nodeIdProvider: NodeIdProvider,
            source: List<*>
        ): ListValueDescription {
            return ListValueDescription(source.map { this.toValueDescription(nodeIdProvider, it) }.toList())
        }
    }

    override fun invoke(
        nodeIdProvider: NodeIdProvider,
        symbolProvider: SymbolProvider,
        node: Node
    ): SymbolDescription {
        @Suppress("UNCHECKED_CAST")
        return (node as NodeTy).let {
            val api = Api<NodeTy>()
            this.specification(api, DeclarativeSymbolProviderRuleContext(node, symbolProvider))
            SymbolDescription(
                api.getName(node),
                nodeIdProvider.id(node),
                api.getTypes(node),
                api.getProperties(nodeIdProvider, node)
            )
        }
    }

    fun isCompatibleWith(nodeType: KClass<*>): Boolean {
        return this.nodeType.isSuperclassOf(nodeType)
    }

    override fun compareTo(other: DeclarativeSymbolProviderRule<out Node>): Int {
        return when {
            this.nodeType.isSuperclassOf(other.nodeType) -> 1
            other.nodeType.isSuperclassOf(this.nodeType) -> -1
            else -> (this.nodeType.qualifiedName ?: "") compareTo (other.nodeType.qualifiedName ?: "")
        }
    }
}

interface DeclarativeSymbolProvideRuleApi<NodeTy : Node> {
    fun name(name: String)

    fun include(property: KProperty1<in NodeTy, Any?>)
}

data class DeclarativeSymbolProviderRuleContext<NodeTy : Node>(val node: NodeTy, val symbolProvider: SymbolProvider)
