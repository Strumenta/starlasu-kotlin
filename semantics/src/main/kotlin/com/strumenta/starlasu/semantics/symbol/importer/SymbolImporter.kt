package com.strumenta.starlasu.semantics.symbol.importer

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.children
import com.strumenta.starlasu.semantics.symbol.provider.SymbolProvider
import com.strumenta.starlasu.semantics.symbol.repository.SymbolRepository

/**
 * Symbol importer instances allow to import symbol descriptions
 * into symbol repositories. The descriptions can be specified using
 * a SymbolProvider, while the repository can be defined implementing
 * the SymbolRepository interface - could be an in-memory map or using
 * an external datasource.
 *
 * Given an AST, the symbol importer uses the symbol provider to extract
 * symbol descriptions from nodes. The resulting description is then
 * stored into the repository for later usage.
 **/
open class SymbolImporter(
    private val symbolProvider: SymbolProvider,
    private val symbolRepository: SymbolRepository,
) {
    /**
     * Extracts a symbol description from the given node
     * and stores it in the symbol repository. Repeats the
     * same process over its children, if `entireTree` is true.
     **/
    fun import(
        node: ASTNode,
        entireTree: Boolean = false,
    ) {
        this.symbolProvider.symbolFor(node)?.let { this.symbolRepository.store(it) }
        node.children.forEach { this.import(it, entireTree) }
    }
}
