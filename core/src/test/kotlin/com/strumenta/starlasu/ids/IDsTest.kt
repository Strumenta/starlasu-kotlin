package com.strumenta.starlasu.ids

import com.strumenta.starlasu.IDProvider
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.traversing.walk
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun nodeIdProviderAdapterCanBeSharedByThreads() {
        val idProvider =
            object : IDProvider {
                private val next = AtomicInteger(1)

                override fun provideIDs(size: Int): List<String> = (0 until size).map { "id_${next.getAndIncrement()}" }
            }
        val nodeIdProvider = NodeIdProviderAdapter(idProvider)
        val node = IdentifiableNode("A", mutableListOf())
        val threads = 8
        val idsPerThread = 10_000
        val executor = Executors.newFixedThreadPool(threads)
        val ids =
            (1..threads)
                .map { executor.submit<List<String>> { (1..idsPerThread).map { nodeIdProvider.id(node) } } }
                .flatMap { it.get() }
        executor.shutdown()
        assertEquals(threads * idsPerThread, ids.size)
        assertEquals(ids.size, ids.toSet().size, "the same ID was returned more than once")
    }
}
