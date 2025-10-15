package com.strumenta.starlasu.semantics.scope.description

import com.strumenta.starlasu.ids.HasID
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.semantics.symbol.description.SymbolDescription

/**
 * Utility function for defining scope descriptions.
 **/
fun scope(
    ignoreCase: Boolean = false,
    init: ScopeDescriptionApi.() -> Unit,
): ScopeDescription = ScopeDescription(ignoreCase).apply(init)

/**
 * A scope description containing all relevant information
 * concerning the visible elements at a given point in space or time.
 * - `ignoreCase`: whether the names associated to the symbols
 * in this scope should be handled as case-sensitive or not;
 * - `nodes`: the local symbols contained in this scope and
 * their associated name;
 * - `identifiers`: the external symbols contained in this scope
 * and their associated name;
 * - `parent`: the scope description *preceding* the current one - used
 * as delegate if no symbol can be found for a given name in the current scope;
 *
 * Each scope description can be configured through the methods defined in
 * the `ScopeDescriptionApi`. References can be resolved invoking `resolve(reference)`.
 *
 * Given a reference, the scope provider will follow the following plan:
 * - try to retrieve a local symbol associated with the given reference name;
 * - if not found, try to retrieve a global symbol associated with the given reference name;
 * - if not found, delegate the resolution to its parent (if any)
 *
 **/
class ScopeDescription(
    private val ignoreCase: Boolean = false,
) : ScopeDescriptionApi {
    private var parent: ScopeDescription? = null
    private val namesToExternalSymbolIdentifiers: MutableMap<String, String> = mutableMapOf()
    private val namesToLocalSymbolNodes: MutableMap<String, PossiblyNamed> = mutableMapOf()

    /**
     * Resolves the given reference in the current scope (or its parents).
     **/
    fun resolve(reference: ReferenceByName<out PossiblyNamed>) {
        val name = reference.name.asKey()
        val node by lazy { this.namesToLocalSymbolNodes[name] }
        val identifier: String? by lazy {
            val id = this.namesToExternalSymbolIdentifiers[name] ?: (node as? HasID)?.id
            require(node == null || id != null) { "No ID available for node $node" }
            id
        }
        when {
            node != null -> {
                @Suppress("UNCHECKED_CAST")
                (reference as ReferenceByName<PossiblyNamed>).referred = node
            }
            identifier != null -> {
                reference.identifier = identifier
            }
            else -> {
                this.parent?.resolve(reference)
            }
        }
    }

    override fun define(
        name: String,
        symbol: Node,
    ) {
        when (symbol) {
            is SymbolDescription -> this.namesToExternalSymbolIdentifiers[name.asKey()] = symbol.identifier
            is PossiblyNamed -> this.namesToLocalSymbolNodes[name.asKey()] = symbol
        }
    }

    override fun define(symbol: PossiblyNamed) {
        if (symbol is Node && symbol.name != null) {
            this.define(symbol.name!!, symbol)
        } else {
            throw RuntimeException("Symbols must be SymbolDescription or Node instances with a non-null name property.")
        }
    }

    override fun parent(parent: ScopeDescription) {
        this.parent = parent
    }

    override fun parent(
        ignoreCase: Boolean,
        init: ScopeDescriptionApi.() -> Unit,
    ) {
        this.parent = scope(ignoreCase, init)
    }

    /**
     * Handle case sensitivity if enabled.
     **/
    private fun String.asKey(): String = if (ignoreCase) this.lowercase() else this
}

/**
 * Interface for defining scope descriptions. It provides
 * the following methods:
 * - `define(name, symbol)` - to associate the given local or global symbol with the given name;
 * - `parent(parent)` - to update the parent scope from another description;
 * - `parent(ignoreCase, init)` - to update the parent scope from a literal description;
 **/
interface ScopeDescriptionApi {
    /**
     * Associates the given symbol with the given name.
     **/
    fun define(
        name: String,
        symbol: Node,
    )

    /**
     * Associates the given symbol with its name.
     *
     **/
    fun define(symbol: PossiblyNamed)

    /**
     * Updates the parent of this scope from another description.
     **/
    fun parent(parent: ScopeDescription)

    /**
     * Updates the parent of this scope from a literal description.
     **/
    fun parent(
        ignoreCase: Boolean = false,
        init: ScopeDescriptionApi.() -> Unit,
    )
}
