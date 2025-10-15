package com.strumenta.starlasu.model

import com.strumenta.starlasu.serialization.IdProvider
import com.strumenta.starlasu.serialization.NodeWithReference
import com.strumenta.starlasu.serialization.computeIds
import com.strumenta.starlasu.serialization.computeIdsForReferencedNodes
import com.strumenta.starlasu.traversing.walkLeavesFirst
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class IndexingTest {
    @Test
    fun computeIdsWithDefaultWalkerAndIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val idsMap = b1.computeIds()
        assertEquals(4, idsMap.size)
        assertContains(idsMap, a1)
        assertContains(idsMap, a2)
        assertContains(idsMap, a3)
        assertContains(idsMap, b1)
    }

    @Test
    fun computeIdsWithCustomWalkerAndDefaultIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids = b1.computeIds(walker = ASTNode::walkLeavesFirst)
        assertEquals(4, ids.size)
        assertContains(ids, a1)
        assertContains(ids, a2)
        assertContains(ids, a3)
        assertContains(ids, b1)
    }

    @Test
    fun computeIdsWithDefaultWalkerAndCustomIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids =
            b1.computeIds(
                idProvider =
                    object : IdProvider {
                        private var counter: Int = 0

                        override fun getId(node: ASTNode): String? = "custom_${this.counter++}"
                    },
            )
        assertEquals(4, ids.size)
        assertEquals(ids[b1], "custom_0")
        assertEquals(ids[a1], "custom_1")
        assertEquals(ids[a2], "custom_2")
        assertEquals(ids[a3], "custom_3")
    }

    @Test
    fun computeIdsWithCustomWalkerAndIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids =
            b1.computeIds(
                walker = ASTNode::walkLeavesFirst,
                idProvider =
                    object : IdProvider {
                        private var counter: Int = 0

                        override fun getId(node: ASTNode): String? = "custom_${this.counter++}"
                    },
            )
        assertEquals(4, ids.size)
        assertEquals(ids[a1], "custom_0")
        assertEquals(ids[a2], "custom_1")
        assertEquals(ids[a3], "custom_2")
        assertEquals(ids[b1], "custom_3")
    }

    @Test
    fun computeIdsForReferencedNodesWithDefaultWalker() {
        val parent = NodeWithReference(name = "root", reference = ReferenceByName(name = "self"))
        parent.reference!!.referred = parent
        val firstChild = NodeWithReference(name = "child", reference = ReferenceByName(name = "parent"))
        firstChild.reference!!.referred = parent
        parent.children.add(firstChild)
        val secondChild = NodeWithReference(name = "child", reference = ReferenceByName(name = "previous"))
        secondChild.reference!!.referred = firstChild
        parent.children.add(secondChild)
        val ids = parent.computeIdsForReferencedNodes()
        assertEquals(2, ids.size)
        assertEquals(ids[parent], "0")
        assertEquals(ids[firstChild], "1")
    }

    @Test
    fun computeIdsForReferencedNodesWithCustomWalker() {
        val parent = NodeWithReference(name = "root", reference = ReferenceByName(name = "self"))
        parent.reference!!.referred = parent
        val firstChild = NodeWithReference(name = "child", reference = ReferenceByName(name = "parent"))
        firstChild.reference!!.referred = parent
        parent.children.add(firstChild)
        val secondChild = NodeWithReference(name = "child", reference = ReferenceByName(name = "previous"))
        secondChild.reference!!.referred = firstChild
        parent.children.add(secondChild)
        val ids = parent.computeIdsForReferencedNodes(walker = ASTNode::walkLeavesFirst)
        assertEquals(2, ids.size)
        assertEquals(ids[parent], "1")
        assertEquals(ids[firstChild], "0")
    }

    @Test
    fun computeIdsForReferencedNodesWithDefaultWalkerAndNoReferences() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids = b1.computeIdsForReferencedNodes()
        assertEquals(0, ids.size)
    }

    @Test
    fun computeIdsForReferencedNodesWithCustomWalkerAndNoReferences() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids = b1.computeIdsForReferencedNodes(walker = ASTNode::walkLeavesFirst)
        assertEquals(0, ids.size)
    }
}
