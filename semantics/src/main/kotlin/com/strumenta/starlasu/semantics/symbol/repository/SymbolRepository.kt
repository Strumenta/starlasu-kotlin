package com.strumenta.starlasu.semantics.symbol.repository

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.semantics.symbol.description.SymbolDescription
import kotlin.reflect.KClass

interface SymbolRepository {
    fun load(identifier: String): SymbolDescription?

    fun store(symbol: SymbolDescription)

    fun find(withType: KClass<out Node>): Sequence<SymbolDescription>
}
