package com.strumenta.starlasu.semantics.symbol.provider

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.semantics.symbol.description.SymbolDescription

interface SymbolProvider {
    fun symbolFor(node: ASTNode): SymbolDescription?
}
