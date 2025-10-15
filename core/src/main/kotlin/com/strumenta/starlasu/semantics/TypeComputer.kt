package com.strumenta.starlasu.semantics

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.utils.memoize
import com.strumenta.starlasu.utils.sortBySubclassesFirst
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

// instance
@Deprecated("The corresponding component in the semantics module should be used instead.")
class TypeComputer(
    private val typingRules: MutableMap<KClass<out Node>, (Node) -> Node?> = mutableMapOf(),
) {
    fun loadFrom(
        configuration: TypeComputerConfiguration,
        semantics: Semantics,
    ) {
        configuration.typingRules.mapValuesTo(this.typingRules) { (_, typingRule) ->
            { node: Node -> semantics.typingRule(node) }
        }
    }

    fun typeFor(node: Node? = null): Node? =
        node?.let {
            this.typingRules.keys
                .filter { it.isSuperclassOf(node::class) }
                .sortBySubclassesFirst()
                .firstOrNull()
                ?.let { this.typingRules[it] }
                ?.invoke(node)
        }
}

// configuration
@Deprecated("The corresponding component in the semantics module should be used instead.")
class TypeComputerConfiguration(
    val typingRules: MutableMap<KClass<out Node>, Semantics.(Node) -> Node?> =
        mutableMapOf(
            Node::class to { it },
        ),
) {
    inline fun <reified N : Node> typeFor(
        nodeType: KClass<N>,
        crossinline typingRule: Semantics.(N) -> Node?,
    ) {
        this.typingRules.putIfAbsent(
            nodeType,
            { semantics: Semantics, node: Node ->
                if (node is N) semantics.typingRule(node) else null
            }.memoize(),
        )
    }
}

// builder

fun typeComputer(init: TypeComputerConfiguration.() -> Unit) = TypeComputerConfiguration().apply(init)
