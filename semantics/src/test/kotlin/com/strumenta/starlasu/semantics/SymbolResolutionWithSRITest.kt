package com.strumenta.starlasu.semantics

import com.strumenta.starlasu.ids.StructuralNodeIdProvider
import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.ASTRoot
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.SimpleOrigin
import com.strumenta.starlasu.model.SyntheticSource
import com.strumenta.starlasu.model.assignParents
import com.strumenta.starlasu.semantics.scope.provider.declarative.DeclarativeScopeProvider
import com.strumenta.starlasu.semantics.scope.provider.declarative.scopeFor
import com.strumenta.starlasu.semantics.symbol.description.StringValueDescription
import com.strumenta.starlasu.semantics.symbol.description.SymbolDescription
import com.strumenta.starlasu.semantics.symbol.provider.SymbolProvider
import com.strumenta.starlasu.semantics.symbol.repository.SymbolRepository
import com.strumenta.starlasu.traversing.walk
import com.strumenta.starlasu.traversing.walkDescendants
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import com.strumenta.starlasu.semantics.symbol.resolver.SymbolResolver as SR

@ASTRoot
class TodoProject(
    override var name: String,
    val todos: MutableList<Todo>,
) : Node(),
    Named

class Todo(
    override var name: String,
    var description: String,
    val prerequisite: ReferenceByName<Todo>? = null,
) : Node(),
    Named

class SymbolResolutionWithSRITest {
    @Test
    fun symbolResolutionPointingToNodes() {
        val todo1 = Todo("todo1", "stuff to do 1")
        val todo2 = Todo("todo2", "stuff to do 2", prerequisite = ReferenceByName("todo1"))
        val todo3 = Todo("todo3", "stuff to do 3")
        val todoProject = TodoProject("Personal", mutableListOf(todo1, todo2, todo3))
        todoProject.assignParents()

        val scopeProvider =
            DeclarativeScopeProvider(
                scopeFor(Todo::prerequisite) {
                    (it.node.parent as TodoProject).todos.forEach(this::define)
                },
            )
        val symbolResolver = SR(scopeProvider)

        assertEquals(false, todo2.prerequisite!!.resolved)
        symbolResolver.resolve(todoProject, entireTree = true)
        assertEquals(true, todo2.prerequisite.resolved)
        assertEquals(todo1, todo2.prerequisite.referred)
    }

    @Test
    fun symbolResolutionPointingToNodesWithCustomIdProvider() {
        val todo1 = Todo("todo1", "stuff to do 1")
        val todo2 = Todo("todo2", "stuff to do 2", prerequisite = ReferenceByName("todo1"))
        val todo3 = Todo("todo3", "stuff to do 3")
        val todoProject = TodoProject("Personal", mutableListOf(todo1, todo2, todo3))
        todoProject.assignParents()

        val nodeIdProvider = StructuralNodeIdProvider("foo")

        val scopeProvider =
            DeclarativeScopeProvider(
                scopeFor(Todo::prerequisite) {
                    (it.node.parent as TodoProject).todos.forEach {
                        define(it)
                    }
                },
            )
        val symbolResolver = SR(scopeProvider)

        assertEquals(false, todo2.prerequisite!!.resolved)
        symbolResolver.resolve(todoProject, entireTree = true)
        assertEquals(true, todo2.prerequisite!!.resolved)
        assertEquals(todo1, todo2.prerequisite!!.referred)
    }

    @Test
    fun symbolResolutionPointingToSymbols() {
        val todo1 = Todo("todo1", "stuff to do 1")
        val todo2 = Todo("todo2", "stuff to do 2")
        val todo3 = Todo("todo3", "stuff to do 3")
        val todoProjectPersonal = TodoProject("Personal", mutableListOf(todo1, todo2, todo3))
        val dummyPoint = Point(1, 0)
        val source1 = SyntheticSource("Personal-Source")
        todoProjectPersonal.assignParents()
        todoProjectPersonal.walk().forEach {
            it.origin = SimpleOrigin(Position(dummyPoint, dummyPoint, source1))
            assertEquals(source1, it.source)
        }

        val todo4 = Todo("todo4", "Some stuff to do", ReferenceByName("todo2"))
        val todoProjectErrands = TodoProject("Errands", mutableListOf(todo4))
        val source2 = SyntheticSource("Errands-Source")
        todoProjectErrands.assignParents()
        todoProjectErrands.walk().forEach {
            it.origin = SimpleOrigin(Position(dummyPoint, dummyPoint, source2))
            assertEquals(source2, it.source)
        }

        val nodeIdProvider = StructuralNodeIdProvider()
        val symbolProvider =
            object : SymbolProvider {
                override fun symbolFor(node: ASTNode): SymbolDescription? {
                    if (node is Todo) {
                        val id = nodeIdProvider.id(node)
                        return SymbolDescription(
                            node.name,
                            id,
                            emptyList(),
                            mapOf("name" to StringValueDescription(node.name)),
                        )
                    } else {
                        return null
                    }
                }
            }

        val sri: SymbolRepository =
            ASTsSymbolRepository(
                symbolProvider,
                todoProjectPersonal,
                todoProjectErrands,
            )
        val scopeProvider =
            DeclarativeScopeProvider(
                scopeFor(Todo::prerequisite) {
                    // We first consider local todos, as they may shadow todos from other projects
                    (it.node.parent as TodoProject).todos.forEach {
                        define(it)
                    }
                    // We then consider all symbols from the sri. Note that nodes of the current project
                    // appear both as nodes and as symbols
                    sri.find(Todo::class).forEach {
                        define(it.name!!, it)
                    }
                },
            )

        // We can now resolve _only_ the nodes in the current AST, so we do not specify other ASTs
        val symbolResolver = SR(scopeProvider)

        assertEquals(false, todo4.prerequisite!!.resolved)
        symbolResolver.resolve(todoProjectErrands, entireTree = true)
        assertEquals(true, todo4.prerequisite.resolved)
        assertEquals("synthetic_Personal-Source_todos_1", todo4.prerequisite.identifier)
    }
}

class ASTsSymbolRepository(
    val symbolProvider: SymbolProvider,
    vararg val roots: Node,
) : SymbolRepository {
    override fun load(identifier: String): SymbolDescription? {
        roots.forEach { root ->
            root.walk().forEach { node ->
                val symbol = symbolProvider.symbolFor(node)
                if (symbol != null && symbol.identifier == identifier) {
                    return symbol
                }
            }
        }
        return null
    }

    override fun store(symbol: SymbolDescription) {
        TODO("Not yet implemented")
    }

    override fun find(withType: KClass<out Node>): Sequence<SymbolDescription> =
        sequence {
            roots.forEach { root ->
                root.walkDescendants(withType).forEach { node ->
                    yield(symbolProvider.symbolFor(node)!!)
                }
            }
        }
}
