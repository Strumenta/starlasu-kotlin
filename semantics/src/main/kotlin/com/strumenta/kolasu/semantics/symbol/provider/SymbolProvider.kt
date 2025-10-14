package com.strumenta.kolasu.semantics.symbol.provider

import com.strumenta.kolasu.model.ASTNode
import com.strumenta.kolasu.semantics.symbol.description.SymbolDescription

interface SymbolProvider {
    fun symbolFor(node: ASTNode): SymbolDescription?
}
