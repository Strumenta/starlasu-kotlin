package com.strumenta.starlasu.semantics.symbol.resolver

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.children
import com.strumenta.starlasu.model.kReferenceByNameType
import com.strumenta.starlasu.model.nodeProperties
import com.strumenta.starlasu.semantics.scope.provider.ScopeProvider
import com.strumenta.starlasu.transformation.isDirectlyPlaceholderASTTransformation
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubtypeOf

/**
 * Symbol resolver instances can be used to resolve references within AST nodes
 * (i.e. `ReferenceByName` instances). Internally, the symbol resolver uses a scope provider
 * defining the language-specific scoping rules. The resolution process can be invoked for
 * specific node properties or for all properties of a given node (possibly its children as well).
 *
 * Symbol resolution can be executed invoking one of the following methods:
 * - `resolve(node, reference)`: to resolve a specific reference of the given node;
 * - `resolve(node, entireTree)`: to resolve all references of the given node
 * and its children if `entireTree` is set to `true`;
 *
 * In both cases, the symbol resolver will resolve references by performing an in-place update.
 * For each reference, the `referred` or `identifier` properties will be updated
 * if an entry is found in the corresponding scope.
 **/
open class SymbolResolver(
    private val scopeProvider: ScopeProvider,
) {
    /**
     * Attempts to resolve the given reference property of the given node.
     **/
    fun resolve(
        node: ASTNode,
        reference: KProperty1<ASTNode, ReferenceByName<PossiblyNamed>?>,
    ) {
        node.properties
            .find { it.name == reference.name }
            ?.let {
                @Suppress("UNCHECKED_CAST")
                it.value as ReferenceByName<PossiblyNamed>?
            }?.let { this.scopeProvider.scopeFor(node, reference).resolve(it) }
    }

    /**
     * Attempts to resolve all reference properties of the
     * given node and its children (if `entireTree` is `true`).
     **/
    fun resolve(
        node: ASTNode,
        entireTree: Boolean = false,
    ) {
        if (node.isDirectlyPlaceholderASTTransformation) {
            return
        }
        node.references().forEach { reference -> this.resolve(node, reference) }
        if (entireTree) {
            node.children.filter { !it.isDirectlyPlaceholderASTTransformation }.forEach {
                this.resolve(it, entireTree)
            }
        }
    }

    /**
     * Retrieve all reference properties of a given node.
     **/
    private fun ASTNode.references(): List<KProperty1<ASTNode, ReferenceByName<PossiblyNamed>?>> =
        this.nodeProperties
            .filter { it.returnType.isSubtypeOf(kReferenceByNameType()) }
            .mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it as? KProperty1<ASTNode, ReferenceByName<PossiblyNamed>?>
            }
}
