package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.language.KolasuLanguage
import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.ReferenceByName
import kotlin.test.Test
import kotlin.test.assertEquals

data class Root(
    val _id: Int,
    val childrez: MutableList<NodeA>,
) : Node()

data class NodeA(
    override val name: String,
    val ref: ReferenceByName<NodeA>,
    val child: NodeB?,
) : Node(),
    Named

data class NodeB(
    val value: String,
) : Node(),
    FooMyRelevantInterface,
    FooMyIrrelevantInterface

interface FooMyIrrelevantInterface

interface FooMyRelevantInterface : ASTNode

class KolasuLanguageTest {
    @Test
    fun allASTClassesAreFound() {
        val kolasuLanguage = KolasuLanguage("com.strumenta.TestLanguage1")
        assertEquals(emptySet(), kolasuLanguage.astClasses.toSet())
        kolasuLanguage.addClass(Root::class)
        assertEquals(
            setOf(Root::class, NodeA::class, NodeB::class, FooMyRelevantInterface::class),
            kolasuLanguage.astClasses.toSet(),
        )
    }
}
