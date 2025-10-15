package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.model.ASTRoot
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.SyntheticSource
import com.strumenta.starlasu.model.assignParents
import com.strumenta.starlasu.traversing.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ASTRoot
data class A(
    val bs: MutableList<B> = mutableListOf(),
) : Node()

@ASTRoot(canBeNotRoot = true)
data class B(
    var name: String,
    var cs: MutableList<C> = mutableListOf(),
) : Node()

@ASTRoot(canBeNotRoot = true)
data class C(
    var value: Int,
) : Node()

class StructuralLionWebNodeIdProviderTest {
    @Test
    fun equalsNodesInTreeGetDifferentIDs() {
        val root =
            A(
                mutableListOf(
                    B(
                        "foo",
                        mutableListOf(
                            C(1),
                            C(2),
                        ),
                    ),
                    B(
                        "foo",
                        mutableListOf(
                            C(1),
                            C(2),
                        ),
                    ),
                ),
            )
        root.assignParents()
        root.source = SyntheticSource("ss1")
        val ip = StructuralLionWebNodeIdProvider()
        val encounteredIDs = mutableSetOf<String>()
        root.walk().forEach { node ->
            val id = ip.id(node)
            assertNotNull(id)
            assert(id !in encounteredIDs) { "Node ID $id was already used" }
            encounteredIDs.add(id)
        }
        assertEquals(7, encounteredIDs.size)
    }

    @Test
    fun equalsNodesNotInTreeGetSameIDs() {
        val root =
            A(
                mutableListOf(
                    B(
                        "foo",
                        mutableListOf(
                            C(1),
                            C(2),
                        ),
                    ),
                    B(
                        "foo",
                        mutableListOf(
                            C(1),
                            C(2),
                        ),
                    ),
                ),
            )
        root.setSourceForTree(SyntheticSource("foo-bar"))

        // we do NOT call assignParents
        val ip = StructuralLionWebNodeIdProvider()
        root.walk().forEach { node ->
            val id = ip.id(node)
            assertNotNull(id)
        }
        assertEquals(ip.id(root.bs[0]), ip.id(root.bs[1]))
        assertEquals(ip.id(root.bs[0].cs[0]), ip.id(root.bs[1].cs[0]))
        assertEquals(ip.id(root.bs[0].cs[1]), ip.id(root.bs[1].cs[1]))
    }
}
