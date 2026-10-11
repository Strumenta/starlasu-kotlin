package com.strumenta.starlasu.semantics.scope.provider

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.semantics.scope.description.ScopeDescription
import kotlin.reflect.KProperty1

/**
 * Provides the [ScopeDescription] in which the given [ReferenceByName] property of a node is resolved.
 */
interface ScopeProvider {
    fun <NodeType : ASTNode> scopeFor(
        node: NodeType,
        reference: KProperty1<in NodeType, ReferenceByName<out PossiblyNamed>?>,
    ): ScopeDescription
}
