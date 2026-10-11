package com.strumenta.starlasu.semantics.symbol.description

import com.strumenta.starlasu.language.KolasuLanguage
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node

/**
 * A serializable description of a symbol, usable to resolve references to it without loading its AST.
 *
 * @property identifier a unique identifier, such as the ID of the node defining the symbol.
 * @property types qualified names of the node's type and its supertypes.
 * @property fields selected property values of the node.
 */
data class SymbolDescription(
    override val name: String,
    val identifier: String,
    val types: List<String>,
    val fields: Map<String, ValueDescription>,
) : Node(),
    Named

/**
 * A property value in a [SymbolDescription]. Nodes and references are described by node ID.
 */
sealed class ValueDescription(
    open val value: Any? = null,
) : Node()

data class BooleanValueDescription(
    override val value: Boolean? = null,
) : ValueDescription(value)

data class IntegerValueDescription(
    override val value: Int? = null,
) : ValueDescription(value)

data class StringValueDescription(
    override val value: String? = null,
) : ValueDescription(value)

object NullValueDescription : ValueDescription(null) {
    private fun readResolve(): Any = NullValueDescription
}

data class ReferenceValueDescription(
    override val value: String? = null,
) : ValueDescription(value)

data class ContainmentValueDescription(
    override val value: String? = null,
) : ValueDescription(value)

data class ListValueDescription(
    override val value: List<ValueDescription> = emptyList(),
) : ValueDescription(value)

const val KOLASU_SYMBOL_DESCRIPTION_LANGUAGE_NAME = "com.strumenta.starlasu.semantics.symbol.SymbolDescriptionLanguage"

val KOLASU_SYMBOL_DESCRIPTION_LANGUAGE: KolasuLanguage by lazy {
    KolasuLanguage(KOLASU_SYMBOL_DESCRIPTION_LANGUAGE_NAME).apply {
        addClass(SymbolDescription::class)
    }
}
