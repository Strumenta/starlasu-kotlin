package com.strumenta.starlasu.model

import com.strumenta.starlasu.semantics.Scope
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test as test

class MyNode(
    override val name: String,
) : Node(),
    Named

data class ASymbol(
    override val name: String,
    val index: Int = 0,
) : Named

data class BSymbol(
    override val name: String,
    val index: Int = 0,
) : Named

data class USymbol(
    override val name: String? = null,
) : PossiblyNamed

data class NodeOverridingName(
    override var name: String,
) : Node(),
    Named

open class BaseNode(
    open var attr1: Int,
) : Node()

data class ExtNode(
    override var attr1: Int,
) : BaseNode(attr1)

class ModelTest {
    @test
    fun referenceByNameUnsolvedToString() {
        val refUnsolved = ReferenceByName<MyNode>("foo")
        assertEquals("Ref(foo)[Unsolved]", refUnsolved.toString())
    }

    @test
    fun referenceByNameSolvedToString() {
        val refSolved = ReferenceByName<MyNode>("foo", MyNode("foo"))
        assertEquals("Ref(foo)[Solved]", refSolved.toString())
    }

    @test
    fun tryToResolvePositiveCaseSameCase() {
        val ref = ReferenceByName<MyNode>("foo")
        assertEquals(true, ref.tryToResolve(listOf(MyNode("foo"))))
        assertEquals(true, ref.resolved)
    }

    @test
    fun tryToResolveNegativeCaseSameCase() {
        val ref = ReferenceByName<MyNode>("foo")
        assertEquals(false, ref.tryToResolve(listOf(MyNode("foo2"))))
        assertEquals(false, ref.resolved)
    }

    @test
    fun tryToResolvePositiveCaseDifferentCase() {
        val ref = ReferenceByName<MyNode>("foo")
        assertEquals(true, ref.tryToResolve(listOf(MyNode("fOo")), caseInsensitive = true))
        assertEquals(true, ref.resolved)
    }

    @test
    fun tryToResolveNegativeCaseDifferentCase() {
        val ref = ReferenceByName<MyNode>("foo")
        assertEquals(false, ref.tryToResolve(listOf(MyNode("foO"))))
        assertEquals(false, ref.resolved)
    }

    @test
    fun scopeAddSymbols() {
        val scope = Scope()
        assertTrue { scope.symbolTable.isEmpty() }

        scope.define(ASymbol(name = "a", index = 0))
        assertEquals(1, scope.symbolTable.size)
        assertEquals(1, scope.symbolTable.getOrElse("a") { emptyList() }.size)
        assertContains(scope.symbolTable.getOrElse("a") { emptyList() }, ASymbol(name = "a", index = 0))

        scope.define(BSymbol(name = "b", index = 0))
        assertEquals(2, scope.symbolTable.size)
        assertEquals(1, scope.symbolTable.getOrElse("b") { emptyList() }.size)
        assertContains(scope.symbolTable.getOrElse("b") { emptyList() }, BSymbol(name = "b", index = 0))

        scope.define(ASymbol(name = "b", index = 1))
        assertEquals(2, scope.symbolTable.size)
        assertEquals(2, scope.symbolTable.getOrElse("b") { emptyList() }.size)
        assertContains(scope.symbolTable.getOrElse("b") { emptyList() }, BSymbol(name = "b", index = 0))
        assertContains(scope.symbolTable.getOrElse("b") { emptyList() }, ASymbol(name = "b", index = 1))
    }

    @test(expected = IllegalArgumentException::class)
    fun scopeAddSymbolWithoutNameError() {
        Scope().define(USymbol(name = null))
    }

    @test
    fun lookupSymbolByNameInLocal() {
        val scope =
            Scope(
                parent = Scope().apply { define(ASymbol(name = "b")) },
            ).apply { this.define(ASymbol(name = "a")) }
        val expected = ASymbol(name = "a")
        val actual = scope.resolve(name = "a")
        assertEquals(expected, actual)
    }

    @test
    fun lookupSymbolByNameInParent() {
        val scope =
            Scope(
                parent = Scope().apply { define(ASymbol(name = "b")) },
            ).apply { define(ASymbol(name = "a")) }
        val expected = ASymbol(name = "b")
        val actual = scope.resolve(name = "b")
        assertEquals(expected, actual)
    }

    @test
    fun lookupSymbolByNameNotFound() {
        val scope = Scope().apply { define(ASymbol(name = "b")) }
        assertNull(scope.resolve(name = "a"))
    }

    @test
    fun lookupSymbolByNameAndTypeInLocal() {
        val scope =
            Scope(
                parent = Scope().apply { define(BSymbol(name = "a")) },
            ).apply { define(ASymbol(name = "a")) }
        val expected = ASymbol(name = "a")
        val actual = scope.resolve(name = "a", type = ASymbol::class)
        assertEquals(expected, actual)
    }

    @test
    fun lookupSymbolByNameAndTypeInParent() {
        val scope =
            Scope(
                parent = Scope().apply { define(BSymbol(name = "a")) },
            ).apply { define(ASymbol(name = "a")) }
        val expected = BSymbol(name = "a")
        val actual = scope.resolve(name = "a", type = BSymbol::class)
        assertEquals(expected, actual)
    }

    @test
    fun lookupSymbolByNameAndTypeNotFoundDifferentName() {
        val scope = Scope().apply { define(ASymbol(name = "a")) }
        assertNull(scope.resolve(name = "b", type = ASymbol::class))
    }

    @test
    fun lookupSymbolByNameAndTypeNotFoundDifferentType() {
        val scope = Scope().apply { define(ASymbol(name = "a")) }
        assertNull(scope.resolve(name = "a", type = BSymbol::class))
    }

    @test
    fun duplicatePropertiesInheritedByInterface() {
        val properties = NodeOverridingName::class.nodeProperties
        assertEquals(1, properties.size)
    }

    @test
    fun duplicatePropertiesInheritedByClass() {
        val properties = ExtNode::class.nodeProperties
        assertEquals(1, properties.size)
    }

    @test
    fun nameIsProperty() {
        assertTrue { MyNode("").properties.map { it.name }.contains("name") }
    }
}
