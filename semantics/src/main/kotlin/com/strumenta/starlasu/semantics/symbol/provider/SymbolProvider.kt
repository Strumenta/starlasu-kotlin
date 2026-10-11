package com.strumenta.starlasu.semantics.symbol.provider

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.semantics.symbol.description.SymbolDescription

/**
 * Produces the [SymbolDescription] of a node, or null if the node does not define a symbol.
 */
interface SymbolProvider {
    fun symbolFor(node: ASTNode): SymbolDescription?
}
