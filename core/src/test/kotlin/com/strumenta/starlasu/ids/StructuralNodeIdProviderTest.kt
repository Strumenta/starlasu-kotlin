package com.strumenta.starlasu.ids

import com.strumenta.starlasu.model.ASTRoot
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.SyntheticSource
import com.strumenta.starlasu.model.assignParents
import junit.framework.TestCase.assertEquals
import kotlin.test.Test
import kotlin.test.assertNotEquals

@ASTRoot
data class MyRoot(
    val foos: MutableList<MyNonRoot> = mutableListOf(),
    var other: MyOtherNode? = null,
) : Node()

data class MyNonRoot(
    val v: Int,
) : Node()

data class MyOtherNode(
    override val name: String,
) : Node(),
    Named

@ASTRoot
data class MyOtherRoot(
    val s: String,
) : Node()

class StructuralNodeIdProviderTest {
    @Test(expected = SourceShouldBeSetException::class)
    fun rootNodeMustHaveSourceSet() {
        val idProvider = StructuralNodeIdProvider()
        val ast = MyRoot(foos = mutableListOf(MyNonRoot(2), MyNonRoot(4), MyNonRoot(6)))
        idProvider.id(ast)
    }

    @Test
    fun rootNodeHasIDDependingOnSource() {
        val idProvider = StructuralNodeIdProvider()
        val ast = MyRoot(foos = mutableListOf(MyNonRoot(2), MyNonRoot(4), MyNonRoot(6)))
        ast.setSourceForTree(SyntheticSource("S1"))
        val id1 = idProvider.id(ast)
        ast.setSourceForTree(SyntheticSource("S2"))
        val id2 = idProvider.id(ast)
        ast.setSourceForTree(SyntheticSource("S1"))
        val id3 = idProvider.id(ast)
        assertNotEquals(id1, id2)
        assertEquals(id1, id3)
    }

    @Test(expected = NodeShouldNotBeRootException::class)
    fun nodeOfNonRootTypeMustBePlacedInAST() {
        val idProvider = StructuralNodeIdProvider()
        val danglingNode = MyNonRoot(2)
        idProvider.id(danglingNode)
    }

    @Test
    fun nodeOfNonRootTypeCanHaveIDWhenPlacedInAST() {
        val idProvider = StructuralNodeIdProvider()
        val n1 = MyNonRoot(2)
        val ast = MyRoot(foos = mutableListOf(n1, MyNonRoot(4), MyNonRoot(6)))
        ast.assignParents()
        ast.setSourceForTree(SyntheticSource("S1"))
        idProvider.id(n1)
    }

    @Test
    fun nodeIdOfNonRootDependsOnPosition() {
        val idProvider = StructuralNodeIdProvider()
        val n1 = MyNonRoot(2)
        val n2 = MyNonRoot(4)
        val n3 = MyNonRoot(6)
        val ast = MyRoot(foos = mutableListOf(n1, n2, n3))
        ast.assignParents()
        ast.setSourceForTree(SyntheticSource("S1"))
        val id1 = idProvider.id(n1)
        val id2 = idProvider.id(n2)
        val id3 = idProvider.id(n3)
        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)
        ast.foos.clear()
        ast.foos.addAll(listOf(n3, n1, n2))
        val nid1 = idProvider.id(n1)
        val nid2 = idProvider.id(n2)
        val nid3 = idProvider.id(n3)
        assertEquals(id1, nid3)
        assertEquals(id2, nid1)
        assertEquals(id3, nid2)
    }

    @Test
    fun semanticIDProviderForRootNode() {
        val idProvider =
            CommonNodeIdProvider(
                semanticIDProvider =
                    DeclarativeNodeIdProvider(
                        idFor<MyOtherRoot> {
                            "MyOtherRoot-${it.s}"
                        },
                    ),
            )
        val ast = MyOtherRoot("foo")
        // Note that the source is not set
        assertEquals("MyOtherRoot-foo", idProvider.id(ast))
    }

    @Test
    fun semanticIDProviderForNonRootNode() {
        val idProvider =
            CommonNodeIdProvider(
                semanticIDProvider =
                    DeclarativeNodeIdProvider(
                        idFor<MyOtherNode> {
                            val n = (it.parent as MyRoot).foos.sumOf { it.v }
                            "MyName-${it.name}-$n"
                        },
                    ),
            )
        val n1 = MyNonRoot(2)
        val n2 = MyNonRoot(4)
        val n3 = MyNonRoot(6)
        val other = MyOtherNode("XYZ")
        val ast = MyRoot(foos = mutableListOf(n1, n2, n3), other = other)
        ast.setSourceForTree(SyntheticSource("S1"))
        ast.assignParents()
        assertEquals("MyName-XYZ-12", idProvider.id(other))
    }
}
