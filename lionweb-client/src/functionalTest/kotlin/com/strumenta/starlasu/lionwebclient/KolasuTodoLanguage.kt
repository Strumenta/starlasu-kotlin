package com.strumenta.starlasu.lionwebclient

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.language.KolasuLanguage
import com.strumenta.kolasu.lionweb.LIONWEB_VERSION_USED_BY_KOLASU
import com.strumenta.kolasu.model.ASTRoot
import com.strumenta.kolasu.model.Named
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.semantics.scope.provider.declarative.DeclarativeScopeProvider
import com.strumenta.kolasu.semantics.scope.provider.declarative.scopeFor
import com.strumenta.kolasu.semantics.symbol.provider.declarative.DeclarativeSymbolProvider
import com.strumenta.kolasu.semantics.symbol.provider.declarative.symbolFor
import com.strumenta.kolasu.semantics.symbol.repository.SymbolRepository
import io.lionweb.kotlin.Multiplicity
import io.lionweb.kotlin.createConcept
import io.lionweb.kotlin.createContainment
import io.lionweb.kotlin.lwLanguage
import io.lionweb.language.LionCoreBuiltins
import io.lionweb.model.impl.DynamicNode

val todoAccountLanguage =
    lwLanguage("todoAccountLanguage", lionWebVersion = LIONWEB_VERSION_USED_BY_KOLASU).apply {
        createConcept("TodoAccount").apply {
            createContainment("projects", LionCoreBuiltins.getNode(), Multiplicity.ZERO_TO_MANY)
        }
    }

val todoAccountConcept by lazy {
    todoAccountLanguage.getConceptByName("TodoAccount")
}

class TodoAccount(id: String) : DynamicNode(id, todoAccountConcept!!)

@ASTRoot
data class TodoProject(override var name: String, val todos: MutableList<Todo> = mutableListOf()) : Node(), Named

data class Todo(
    override var name: String,
    var description: String,
    val prerequisite: ReferenceByName<Todo>? = null
) : Node(), Named {
    constructor(name: String) : this(name, name)
}

val todoLanguage =
    KolasuLanguage("TodoLanguage").apply {
        addClass(TodoProject::class)
    }

class TodoSymbolProvider(nodeIdProvider: NodeIdProvider) : DeclarativeSymbolProvider(
    nodeIdProvider,
    symbolFor<Todo> {
        this.name(it.node.name)
    }
)

class TodoScopeProvider(val sri: SymbolRepository) : DeclarativeScopeProvider(
    scopeFor(Todo::prerequisite) {
        // We first consider local todos, as they may shadow todos from other projects
        (it.node.parent as TodoProject).todos.forEach(this::define)
        // We then consider all symbols from the sri. Note that nodes of the current project
        // appear both as nodes and as symbols
        sri.find(Todo::class).forEach { todo ->
            define(todo.name, todo)
        }
    }
)
