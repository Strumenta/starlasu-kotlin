package com.strumenta.kolasu.semantics

import com.strumenta.kolasu.ids.NodeIdProvider
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.semantics.symbol.provider.declarative.DeclarativeSymbolProvider
import com.strumenta.kolasu.semantics.symbol.provider.declarative.symbolFor
import com.strumenta.kolasu.testing.runConcurrently
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for the thread-safety fix in [DeclarativeSymbolProvider]: each rule
 * re-evaluates its specification on every symbolFor() call, mutating the rule's own
 * name/properties state. The evaluation is now synchronized and the state reset.
 */
class ThreadSafetyTest {

    @Test
    fun declarativeSymbolProviderIsThreadSafe() {
        val nodeIdProvider = object : NodeIdProvider {
            override fun id(kNode: Node): String = "id-${kNode.hashCode()}"
            override var parentProvider: NodeIdProvider? = null
        }
        val provider = DeclarativeSymbolProvider(
            nodeIdProvider,
            symbolFor<Todo> {
                this.name(it.node.name)
            }
        )
        val todos = (1..20).map { Todo("todo$it", "description $it") }

        runConcurrently(threads = 8, iterations = 300) { w, i ->
            val todo = todos[(w + i) % todos.size]
            val symbol = provider.symbolFor(todo)
            // Without the fix, the rule's mutable name/properties state could be observed
            // mid-update by another invocation running on another thread
            assertEquals(todo.name, symbol?.name)
            assertEquals(nodeIdProvider.id(todo), symbol?.identifier)
        }
    }
}
