package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import junit.framework.TestCase.assertEquals
import kotlin.test.Test

enum class SomeAlternatives {
    BAR,
    FOO,
    ZUM,
}

data class MyNodeWithProps(
    val p1: Int,
    val p2: Boolean,
    val p4: String,
) : Node()

data class MyNodeWithLongProp(
    val p3: Long,
) : Node()

data class MyNodeWithEnum(
    val p5: SomeAlternatives,
) : Node()

data class MyNodeWithContainments(
    val c1: MyNodeWithProps,
    val c2: MyNodeWithProps?,
    val c3: MutableList<MyNodeWithProps>,
) : Node()

data class MyNodeWithRefs(
    val r1: ReferenceByName<PossiblyNamed>?,
    val r2: ReferenceByName<PossiblyNamed>,
) : Node()

class DummyNodesTest {
    @Test
    fun canCreateDummyNodeWithProperties() {
        val dummyNode = MyNodeWithProps::class.dummyInstance()
        assert(dummyNode is MyNodeWithProps)
        assertEquals(0, dummyNode.p1)
        assertEquals(false, dummyNode.p2)
        assertEquals("DUMMY", dummyNode.p4)
    }

    @Test
    fun canCreateDummyNodeWithLongProp() {
        val dummyNode = MyNodeWithLongProp::class.dummyInstance()
        assert(dummyNode is MyNodeWithLongProp)
        assertEquals(0L, dummyNode.p3)
    }

    @Test
    fun canCreateDummyNodeWithEnum() {
        val dummyNode = MyNodeWithEnum::class.dummyInstance()
        assert(dummyNode is MyNodeWithEnum)
        assertEquals(SomeAlternatives.BAR, dummyNode.p5)
    }

    @Test
    fun canCreateDummyNodeWithContainments() {
        val dummyNode = MyNodeWithContainments::class.dummyInstance()
        assert(dummyNode is MyNodeWithContainments)
        assert(dummyNode.c1 is MyNodeWithProps)
        assertEquals(null, dummyNode.c2)
        assertEquals(emptyList<MyNodeWithProps>(), dummyNode.c3)
    }

    @Test
    fun canCreateDummyNodeWithRefs() {
        val dummyNode = MyNodeWithRefs::class.dummyInstance()
        assert(dummyNode is MyNodeWithRefs)
        assertEquals(null, dummyNode.r1)
        assertEquals(ReferenceByName<PossiblyNamed>("UNKNOWN"), dummyNode.r2)
    }
}
