package com.strumenta.starlasu.model

import com.strumenta.simplelang.SimpleLangLexer
import com.strumenta.simplelang.SimpleLangParser
import com.strumenta.starlasu.parsing.ParseTreeOrigin
import com.strumenta.starlasu.parsing.toPosition
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test as test

data class MySetStatement(
    val specifiedPosition: Position? = null,
) : Node(specifiedPosition)

class PositionTest {
    @test
    fun offset() {
        val code =
            """this is some code
                     |second line
                     |third line""".trimMargin("|")
        assertEquals(0, START_POINT.offset(code))
        assertEquals(5, Point(1, 5).offset(code))
        assertEquals(17, Point(1, 17).offset(code))
        assertEquals(18, Point(2, 0).offset(code))
        assertFails { Point(1, 18).offset(code) }
        assertFails { Point(4, 0).offset(code) }
    }

    @test
    fun pointCompare() {
        val p0 = START_POINT
        val p1 = Point(1, 1)
        val p2 = Point(1, 100)
        val p3 = Point(2, 90)

        assertEquals(false, p0 < p0)
        assertEquals(true, p0 <= p0)
        assertEquals(true, p0 >= p0)
        assertEquals(false, p0 > p0)

        assertEquals(true, p0 < p1)
        assertEquals(true, p0 <= p1)
        assertEquals(false, p0 >= p1)
        assertEquals(false, p0 > p1)

        assertEquals(true, p0 < p2)
        assertEquals(true, p0 <= p2)
        assertEquals(false, p0 >= p2)
        assertEquals(false, p0 > p2)

        assertEquals(true, p0 < p3)
        assertEquals(true, p0 <= p3)
        assertEquals(false, p0 >= p3)
        assertEquals(false, p0 > p3)

        assertEquals(true, p1 < p2)
        assertEquals(true, p1 <= p2)
        assertEquals(false, p1 >= p2)
        assertEquals(false, p1 > p2)

        assertEquals(true, p1 < p3)
        assertEquals(true, p1 <= p3)
        assertEquals(false, p1 >= p3)
        assertEquals(false, p1 > p3)
    }

    @test
    fun isBefore() {
        val p0 = START_POINT
        val p1 = Point(1, 1)
        val p2 = Point(1, 100)
        val p3 = Point(2, 90)

        assertEquals(false, p0.isBefore(p0))
        assertEquals(true, p0.isBefore(p1))
        assertEquals(true, p0.isBefore(p2))
        assertEquals(true, p0.isBefore(p3))

        assertEquals(false, p1.isBefore(p0))
        assertEquals(false, p1.isBefore(p1))
        assertEquals(true, p1.isBefore(p2))
        assertEquals(true, p1.isBefore(p3))

        assertEquals(false, p2.isBefore(p0))
        assertEquals(false, p2.isBefore(p1))
        assertEquals(false, p2.isBefore(p2))
        assertEquals(true, p2.isBefore(p3))

        assertEquals(false, p3.isBefore(p0))
        assertEquals(false, p3.isBefore(p1))
        assertEquals(false, p3.isBefore(p2))
        assertEquals(false, p3.isBefore(p3))
    }

    @test
    fun text() {
        val code =
            """this is some code
                     |second line
                     |third line""".trimMargin("|")
        assertEquals("", Position(START_POINT, START_POINT).text(code))
        assertEquals("t", Position(START_POINT, Point(1, 1)).text(code))
        assertEquals("this is some cod", Position(START_POINT, Point(1, 16)).text(code))
        assertEquals("this is some code", Position(START_POINT, Point(1, 17)).text(code))
        assertEquals("this is some code\n", Position(START_POINT, Point(2, 0)).text(code))
        assertEquals("this is some code\ns", Position(START_POINT, Point(2, 1)).text(code))
    }

    @test
    fun containsPoint() {
        val before = Point(1, 0)
        val start = Point(1, 1)
        val middle = Point(1, 2)
        val end = Point(1, 3)
        val after = Point(1, 4)
        val position = Position(start, end)

        assertFalse("contains should return false with point before") { position.contains(before) }
        assertTrue("contains should return true with point at the beginning") { position.contains(start) }
        assertTrue("contains should return true with point in the middle") { position.contains(middle) }
        assertTrue("contains should return true with point at the end") { position.contains(end) }
        assertFalse("contains should return false with point after") { position.contains(after) }
    }

    @test
    fun containsPosition() {
        val before = Position(Point(1, 0), Point(1, 10))
        val inside = Position(Point(2, 3), Point(2, 8))
        val after = Position(Point(3, 0), Point(3, 10))
        val position = Position(Point(2, 0), Point(2, 10))

        assertFalse("contains should return false with position before") { position.contains(before) }
        assertTrue("contains should return true with same position") { position.contains(position) }
        assertTrue("contains should return true with position inside") { position.contains(inside) }
        assertFalse("contains should return false with position after") { position.contains(after) }
    }

    @test
    fun containsNode() {
        val before = Node(Position(Point(1, 0), Point(1, 10)))
        val inside = Node(Position(Point(2, 3), Point(2, 8)))
        val after = Node(Position(Point(3, 0), Point(3, 10)))
        val position = Position(Point(2, 0), Point(2, 10))

        assertFalse("contains should return false with node before") { position.contains(before) }
        assertTrue("contains should return true with node inside") { position.contains(inside) }
        assertFalse("contains should return false with node after") { position.contains(after) }
    }

    @test
    fun parserRuleContextPosition() {
        val code = "set foo = 123"
        val lexer = SimpleLangLexer(CharStreams.fromString(code))
        val parser = SimpleLangParser(CommonTokenStream(lexer))
        val cu = parser.compilationUnit()
        val setStmt = cu.statement(0) as SimpleLangParser.SetStmtContext
        val pos = setStmt.toPosition()
        assertEquals(Position(Point(1, 0), Point(1, 13)), pos)
    }

    @test
    fun positionDerivedFromParseTreeNode() {
        val code = "set foo = 123"
        val lexer = SimpleLangLexer(CharStreams.fromString(code))
        val parser = SimpleLangParser(CommonTokenStream(lexer))
        val cu = parser.compilationUnit()
        val setStmt = cu.statement(0) as SimpleLangParser.SetStmtContext
        val mySetStatement = MySetStatement()
        mySetStatement.origin = ParseTreeOrigin(setStmt)
        assertEquals(Position(Point(1, 0), Point(1, 13)), mySetStatement.position)
    }

    @test
    fun illegalPositionAccepted() {
        Position(Point(10, 1), Point(5, 2), validate = false)
    }

    @test(expected = Exception::class)
    fun illegalPositionNotAccepted() {
        Position(Point(10, 1), Point(5, 2), validate = true)
    }

    @test
    fun parserTreePosition() {
        val code = "set foo = 123"
        val lexer = SimpleLangLexer(CharStreams.fromString(code))
        val parser = SimpleLangParser(CommonTokenStream(lexer))
        val cu: ParseTree = parser.compilationUnit()
        val pos = cu.toPosition()
        assertEquals(Position(Point(1, 0), Point(1, 13)), pos)
    }

//    @test(expected = Exception::class) fun illegalPositionNotAcceptedByDefault() {
//        val p = Position(Point(10, 1), Point(5, 2))
//    }
}
