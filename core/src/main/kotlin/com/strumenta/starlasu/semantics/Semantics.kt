package com.strumenta.starlasu.semantics

import com.strumenta.starlasu.validation.Issue

// instance

@Deprecated("The corresponding component in the semantics module should be used instead.")
class Semantics(
    issues: MutableList<Issue> = mutableListOf(),
    configuration: SemanticsConfiguration,
) {
    val typeComputer =
        TypeComputer().apply {
            configuration.typeComputer?.let {
                this.loadFrom(it, this@Semantics)
            }
        }
    val symbolResolver =
        SymbolResolver().apply {
            configuration.symbolResolver?.let {
                this.loadFrom(it, this@Semantics)
            }
        }
}

// configuration

@Deprecated("The corresponding component in the semantics module should be used instead.")
class SemanticsConfiguration(
    var typeComputer: TypeComputerConfiguration? = null,
    var symbolResolver: SymbolResolverConfiguration? = null,
) {
    fun typeComputer(init: TypeComputerConfiguration.() -> Unit) {
        this.typeComputer = TypeComputerConfiguration().apply(init)
    }

    fun symbolResolver(init: SymbolResolverConfiguration.() -> Unit) {
        this.symbolResolver = SymbolResolverConfiguration().apply(init)
    }
}

// builder

fun semantics(
    issues: MutableList<Issue> = mutableListOf(),
    init: SemanticsConfiguration.() -> Unit,
) = Semantics(issues, SemanticsConfiguration().apply(init))
