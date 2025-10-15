package com.strumenta.starlasu.model

import kotlin.test.assertEquals
import org.junit.Test as test

abstract class Expr : Node()

class Number(
    val value: Int,
) : Expr()

class Add(
    val left: Expr,
    val right: Expr,
) : Expr()

class Sub(
    private val left: Expr,
    private val right: Expr,
) : Expr()

class Empty(
    val empty: List<Expr>? = emptyList(),
) : Expr()

open class Expressions(
    val public: List<Expr> = emptyList(),
    private val private: List<Expr> = emptyList(),
    protected val protected: List<Expr> = emptyList(),
    internal val internal: List<Expr> = emptyList(),
) : Expr()

class PrintingTest {
    @test
    fun basicTest() {
        val ast = Add(Add(Number(3), Number(9)), Number(1))
        assertEquals(
            """
            Add {
              left = [
                Add {
                  left = [
                    Number {
                      value = 3
                    } // Number
                  ]
                  right = [
                    Number {
                      value = 9
                    } // Number
                  ]
                } // Add
              ]
              right = [
                Number {
                  value = 1
                } // Number
              ]
            } // Add
            
            """.trimIndent(),
            ast.debugPrint(),
        )
    }

    @test
    fun privateFieldsDoNotGetPrintedToo() {
        val ast = Add(Sub(Number(3), Number(9)), Number(1))
        assertEquals(
            """
            Add {
              left = [
                Sub
              ]
              right = [
                Number {
                  value = 1
                } // Number
              ]
            } // Add
            
            """.trimIndent(),
            ast.debugPrint(),
        )
    }

    @test
    fun shortFormatForEmptyArrays() {
        val ast = Empty()
        assertEquals(
            """
            Empty {
              empty = []
            } // Empty
            
            """.trimIndent(),
            ast.debugPrint(),
        )
    }
}
