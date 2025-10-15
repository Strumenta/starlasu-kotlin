package com.strumenta.starlasu.testing

import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import org.junit.ComparisonFailure
import org.junit.Test

data class MyBigNode(
    override val name: String,
    val foo: MySmallNode? = null,
    val bars: List<MyOtherNode> = emptyList(),
) : Node(),
    Named

data class MySmallNode(
    val value: Long,
) : Node()

data class MyOtherNode(
    val flag: Boolean,
    val s: String,
) : Node()

class AssertionsTest {
    @Test
    fun comparingTwoSimpleNodesWhichAreEqual() {
        assertASTsAreEqual(MyBigNode("a"), MyBigNode("a"))
    }

    @Test(expected = ComparisonFailure::class)
    fun comparingTwoSimpleNodesWhichAreNotEqualBecauseOfSimpleProperty() {
        assertASTsAreEqual(MyBigNode("a"), MyBigNode("b"))
    }

    @Test(expected = AssertionError::class)
    fun comparingTwoSimpleNodesWhichAreNotEqualBecauseOfSingleContainmentA() {
        assertASTsAreEqual(MyBigNode("a"), MyBigNode("a", MySmallNode(1L)))
    }

    @Test(expected = AssertionError::class)
    fun comparingTwoSimpleNodesWhichAreNotEqualBecauseOfSingleContainmentB() {
        assertASTsAreEqual(MyBigNode("a", MySmallNode(1L)), MyBigNode("a"))
    }

    @Test(expected = AssertionError::class)
    fun comparingTwoSimpleNodesWhichAreNotEqualBecauseOfSingleContainmentC() {
        assertASTsAreEqual(MyBigNode("a", MySmallNode(1L)), MyBigNode("a", MySmallNode(2L)))
    }

    @Test
    fun comparingTwoSimpleNodesWhichAreEqualWithSingleContainmentC() {
        assertASTsAreEqual(MyBigNode("a", MySmallNode(4L)), MyBigNode("a", MySmallNode(4L)))
    }

    @Test
    fun comparingTwoSimpleNodesWhichAreEqualWithMultipleContainmentC() {
        assertASTsAreEqual(
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(true, "z2"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(true, "z2"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
        )
    }

    @Test(expected = AssertionError::class)
    fun comparingTwoSimpleNodesWhichAreNotEqualBecauseOfMultipleContainmentA() {
        assertASTsAreEqual(
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(true, "z2"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(false, "z2"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
        )
    }

    @Test(expected = AssertionError::class)
    fun comparingTwoSimpleNodesWhichAreNotEqualBecauseOfMultipleContainmentB() {
        assertASTsAreEqual(
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(true, "z2"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
        )
    }

    @Test(expected = AssertionError::class)
    fun comparingTwoSimpleNodesWhichAreNotEqualBecauseOfMultipleContainmentC() {
        assertASTsAreEqual(
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(true, "z2"),
                    ),
            ),
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(true, "z2"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
        )
    }

    @Test
    fun comparingTwoSimpleNodesWhichAreNotEqualButUsingIgnoreChildren() {
        assertASTsAreEqual(
            MyBigNode("a", bars = IgnoreChildren()),
            MyBigNode(
                "a",
                bars =
                    listOf(
                        MyOtherNode(false, "z1"),
                        MyOtherNode(true, "z2"),
                        MyOtherNode(false, "z3"),
                    ),
            ),
        )
    }
}
