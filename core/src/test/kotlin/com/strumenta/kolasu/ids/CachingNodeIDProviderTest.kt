package com.strumenta.kolasu.ids

import com.strumenta.kolasu.model.ASTNode
import com.strumenta.kolasu.model.SyntheticSource
import com.strumenta.kolasu.model.assignParents
import kotlin.test.Test
import kotlin.test.assertEquals

class CachingNodeIDProviderTest {
    class CountingNodeIdProvider : StructuralNodeIdProvider() {
        var count: Int = 0

        override fun id(kNode: ASTNode): String {
            count++
            return super.id(kNode)
        }
    }

    @Test
    fun invocationHappensTheRightAmountOfTime() {
        val n1 = MyNonRoot(2)
        val n2 = MyNonRoot(4)
        val n3 = MyNonRoot(6)
        val ast = MyRoot(foos = mutableListOf(n1, n2, n3))
        ast.assignParents()
        ast.setSourceForTree(SyntheticSource("S1"))

        // First without caching
        val providerNoCache = CountingNodeIdProvider()
        val id1 = providerNoCache.id(n1)
        val id2 = providerNoCache.id(n2)
        val id3 = providerNoCache.id(n3)
        val idRoot = providerNoCache.id(ast)
        assertEquals(7, providerNoCache.count)

        // Then with caching
        val providerWithCache = CountingNodeIdProvider().caching()
        val id1b = providerWithCache.id(n1)
        val id2b = providerWithCache.id(n2)
        val id3b = providerWithCache.id(n3)
        val idRootB = providerWithCache.id(ast)
        assertEquals(4, (providerWithCache.wrapped as CountingNodeIdProvider).count)

        // Same IDS
        assertEquals(id1, id1b)
        assertEquals(id2, id2b)
        assertEquals(id3, id3b)
        assertEquals(idRoot, idRootB)
    }
}
