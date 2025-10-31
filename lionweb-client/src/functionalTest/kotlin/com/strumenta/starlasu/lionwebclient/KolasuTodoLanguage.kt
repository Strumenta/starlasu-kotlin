package com.strumenta.starlasu.lionwebclient

import com.strumenta.starlasu.ids.NodeIdProvider
import com.strumenta.starlasu.language.KolasuLanguage
import com.strumenta.starlasu.lionweb.LIONWEB_VERSION_USED_BY_STARLASU
import com.strumenta.starlasu.model.ASTRoot
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.semantics.scope.provider.declarative.DeclarativeScopeProvider
import com.strumenta.starlasu.semantics.scope.provider.declarative.scopeFor
import com.strumenta.starlasu.semantics.symbol.provider.declarative.DeclarativeSymbolProvider
import com.strumenta.starlasu.semantics.symbol.provider.declarative.symbolFor
import com.strumenta.starlasu.semantics.symbol.repository.SymbolRepository
import io.lionweb.kotlin.Multiplicity
import io.lionweb.kotlin.createConcept
import io.lionweb.kotlin.createContainment
import io.lionweb.kotlin.lwLanguage
import io.lionweb.language.LionCoreBuiltins
import io.lionweb.model.impl.DynamicNode

val todoAccountLanguage =
    lwLanguage("todoAccountLanguage", lionWebVersion = LIONWEB_VERSION_USED_BY_STARLASU).apply {
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
