package com.strumenta.starlasu.language

import com.strumenta.starlasu.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

enum class E1 {
    A,
    B,
    C,
}

sealed class C1 : Node()

data class C2(
    val s: String,
) : C1()

data class C3(
    val e: E1,
) : C1()

class KolasuLanguageTest {
    @Test
    fun allElementsAreFound() {
        val kolasuLanguage = KolasuLanguage("MyLanguage")
        kolasuLanguage.addClass(C1::class)
        assertEquals(3, kolasuLanguage.astClasses.size)
        assertNotNull(kolasuLanguage.findASTClass("C1"))
        assertNotNull(kolasuLanguage.findASTClass("C2"))
        assertNotNull(kolasuLanguage.findASTClass("C3"))
        assertEquals(1, kolasuLanguage.enumClasses.size)
        assertNotNull(kolasuLanguage.findEnumClass("E1"))
    }
}
