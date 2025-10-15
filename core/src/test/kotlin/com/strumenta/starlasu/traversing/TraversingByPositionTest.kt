package com.strumenta.starlasu.traversing

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

internal class TraversingByPositionTest {
    class Box(
        val name: String,
        val contents: List<ASTNode>,
        specifiedPosition: Position? = null,
    ) : Node(specifiedPosition)

    class Item(
        val name: String,
        specifiedPosition: Position? = null,
    ) : Node(specifiedPosition)

    private fun printSequence(sequence: Sequence<ASTNode>): String =
        sequence
            .map {
                when (it) {
                    is Box -> it.name
                    is Item -> it.name
                    else -> fail("")
                }
            }.joinToString()

    private val testCase =
        Box(
            "root",
            listOf(
                Box(
                    "first",
                    listOf(
                        Item("1", specifiedPosition = pos(3, 6, 3, 12)),
                    ),
                    specifiedPosition = pos(2, 3, 4, 3),
                ),
                Item("2", specifiedPosition = pos(5, 3, 5, 9)),
                Box(
                    "big",
                    listOf(
                        Box(
                            "small",
                            listOf(
                                Item("3", specifiedPosition = pos(8, 7, 8, 13)),
                                Item("4", specifiedPosition = pos(9, 7, 9, 13)),
                                Item("5", specifiedPosition = pos(10, 7, 10, 13)),
                            ),
                            specifiedPosition = pos(7, 5, 11, 5),
                        ),
                    ),
                    specifiedPosition = pos(6, 3, 12, 3),
                ),
                Item("6", specifiedPosition = pos(13, 3, 13, 9)),
            ),
            specifiedPosition = pos(1, 1, 14, 1),
        )

    @Test
    fun walkWithinWithOutsidePosition() {
        val position: Position = pos(15, 1, 15, 1)
        val result: String = printSequence(testCase.walkWithin(position))
        assertEquals("", result)
    }

    @Test
    fun walkWithinWithRootPosition() {
        val position: Position = pos(1, 1, 14, 1)
        val result: String = printSequence(testCase.walkWithin(position))
        assertEquals("root, first, 1, 2, big, small, 3, 4, 5, 6", result)
    }

    @Test
    fun walkWithinWithLeafPosition() {
        val position: Position = pos(13, 3, 13, 9)
        val result: String = printSequence(testCase.walkWithin(position))
        assertEquals("6", result)
    }

    @Test
    fun walkWithinPointInLeafPosition() {
        val position: Position = pos(13, 4, 13, 5)
        assertEquals("", printSequence(testCase.walkWithin(position)))
    }

    @Test
    fun walkWithinWithSubTreePosition() {
        val position: Position = pos(7, 5, 11, 5)
        val result: String = printSequence(testCase.walkWithin(position))
        assertEquals("small, 3, 4, 5", result)
    }

    @Test
    fun findByPosition() {
        val leaf1: Position = pos(13, 4, 13, 5)
        assertEquals("root, 6", printSequence(testCase.searchByPosition(leaf1, true)))
        assertEquals("6", printSequence(sequenceOf(testCase.findByPosition(leaf1, true)!!)))
        assertEquals("root, 6", printSequence(testCase.searchByPosition(leaf1, false)))
        assertEquals("6", printSequence(sequenceOf(testCase.findByPosition(leaf1, false)!!)))

        val leaf2: Position = pos(10, 8, 10, 12)
        assertEquals("root, big, small, 5", printSequence(testCase.searchByPosition(leaf2, true)))
        assertEquals("5", printSequence(sequenceOf(testCase.findByPosition(leaf2, true)!!)))
        assertEquals("root, big, small, 5", printSequence(testCase.searchByPosition(leaf2, false)))
        assertEquals("5", printSequence(sequenceOf(testCase.findByPosition(leaf2, false)!!)))

        val internal: Position = pos(8, 8, 10, 12)
        assertEquals("root, big, small", printSequence(testCase.searchByPosition(internal, true)))
        assertEquals("small", printSequence(sequenceOf(testCase.findByPosition(internal, true)!!)))
        assertEquals("root, big, small", printSequence(testCase.searchByPosition(internal, false)))
        assertEquals("small", printSequence(sequenceOf(testCase.findByPosition(internal, false)!!)))

        val outside: Position = pos(100, 100, 101, 101)
        assertEquals("", printSequence(testCase.searchByPosition(outside, true)))
        assertNull(testCase.findByPosition(outside, true))
    }
}
