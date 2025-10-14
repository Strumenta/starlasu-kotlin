package com.strumenta.kolasu.semantics.scope.provider

import com.strumenta.kolasu.model.ASTNode
import com.strumenta.kolasu.model.PossiblyNamed
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.semantics.scope.description.ScopeDescription
import kotlin.reflect.KProperty1

interface ScopeProvider {
    fun <NodeType : ASTNode> scopeFor(
        node: NodeType,
        reference: KProperty1<in NodeType, ReferenceByName<out PossiblyNamed>?>,
    ): ScopeDescription
}
