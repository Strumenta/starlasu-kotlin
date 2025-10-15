package com.strumenta.starlasu.ids

import com.strumenta.starlasu.base.IDProvider
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.traversing.walk
import kotlin.test.Test
import kotlin.test.assertEquals

data class IdentifiableNode(
    var bar: String,
    val zum: MutableList<IdentifiableNode>,
) : Node()

class IDsTest {
    @Test
    fun testNodeIdProviderAdapter() {
        val idProvider =
            object : IDProvider {
                private var next = 1

                override fun provideIDs(size: Int): List<String> {
                    val res = mutableListOf<String>()
                    (0 until size).forEach { i ->
                        res.add("id_${next++}")
                    }
                    return res
                }
            }
        val nodeIdProvider = NodeIdProviderAdapter(idProvider)
        val ast =
            IdentifiableNode(
                "A",
                mutableListOf(
                    IdentifiableNode(
                        "B",
                        mutableListOf(
                            IdentifiableNode(
                                "C",
                                mutableListOf(),
                            ),
                        ),
                    ),
                    IdentifiableNode("D", mutableListOf()),
                ),
            )
        assertEquals(true, ast.walk().all { it.id == null })
        nodeIdProvider.assignIDsToTree(ast)
        assertEquals(true, ast.walk().none { it.id == null })
        assertEquals(setOf("id_1", "id_2", "id_3", "id_4"), ast.walk().map { it.id }.toSet())
    }
}
