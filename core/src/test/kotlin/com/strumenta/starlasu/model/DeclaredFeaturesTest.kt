package com.strumenta.starlasu.model

import org.junit.Test
import kotlin.test.assertEquals

data class NodeWithDerivedA(
    val name: String,
) : Node() {
    @Derived
    val upperName: String
        get() = name.uppercase()
}

data class NodeWithDerivedB(
    val name: String,
) : Node() {
    @Derived
    val upperName: String
        get() = name.uppercase()
}

class DeclaredFeaturesTest {
    @Test
    fun includeDerivedIsRespectedWhenExcludingDerivedIsComputedFirst() {
        assertEquals(listOf("name"), NodeWithDerivedA::class.declaredFeatures().map { it.name })
        assertEquals(
            setOf("name", "upperName"),
            NodeWithDerivedA::class.declaredFeatures(includeDerived = true).map { it.name }.toSet(),
        )
    }

    @Test
    fun includeDerivedIsRespectedWhenIncludingDerivedIsComputedFirst() {
        assertEquals(
            setOf("name", "upperName"),
            NodeWithDerivedB::class.declaredFeatures(includeDerived = true).map { it.name }.toSet(),
        )
        assertEquals(listOf("name"), NodeWithDerivedB::class.declaredFeatures().map { it.name })
    }
}
