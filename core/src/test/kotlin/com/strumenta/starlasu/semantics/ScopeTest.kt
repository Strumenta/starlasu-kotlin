package com.strumenta.starlasu.semantics

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScopeTest {
    @Test
    fun testScopeWithIgnoreCase() {
        val node = ClassDecl("TestNode")
        val scope = scope(ignoreCase = true) { define(node) }
        assertEquals(node, scope.resolve(name = "TestNode"))
        assertEquals(node, scope.resolve(name = "testnode"))
        assertEquals(node, scope.resolve(name = "testNode"))
        assertEquals(node, scope.resolve(name = "Testnode"))
    }

    @Test
    fun testScopeWithoutIgnoreCase() {
        val node = ClassDecl(name = "TestNode")
        val scope = scope(ignoreCase = false) { define(node) }
        assertEquals(node, scope.resolve(name = "TestNode"))
        assertNull(scope.resolve(name = "testnode"))
        assertNull(scope.resolve(name = "testNode"))
        assertNull(scope.resolve(name = "Testnode"))
    }
}
