package com.strumenta.starlasu.semantics.symbol.repository

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.semantics.symbol.description.SymbolDescription
import kotlin.reflect.KClass

/**
 * Stores [SymbolDescription]s, e.g. in memory or in an external data source.
 */
interface SymbolRepository {
    /**
     * The symbol with the given [identifier], if any.
     */
    fun load(identifier: String): SymbolDescription?

    fun store(symbol: SymbolDescription)

    /**
     * The symbols whose node type is [withType] or a subtype of it.
     */
    fun find(withType: KClass<out Node>): Sequence<SymbolDescription>
}
