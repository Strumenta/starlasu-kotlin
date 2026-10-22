package com.strumenta.starlasu.model

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class InheritanceBase : Node() {
    abstract val inheritedAttr: String
}

data class InheritanceChild(
    override val inheritedAttr: String,
    val ownAttr: String,
) : InheritanceBase()

class IsInheritedTest {
    @Test
    fun featureDeclaredInSupertypeIsInherited() {
        val feature = InheritanceChild::class.allFeatures().first { it.name == "inheritedAttr" }
        assertTrue(InheritanceChild::class.isInherited(feature))
    }

    @Test
    fun featureDeclaredInClassIsNotInherited() {
        val feature = InheritanceChild::class.allFeatures().first { it.name == "ownAttr" }
        assertFalse(InheritanceChild::class.isInherited(feature))
    }

    @Test
    fun featureOfRootClassIsNotInherited() {
        val feature = InheritanceBase::class.allFeatures().first { it.name == "inheritedAttr" }
        assertFalse(InheritanceBase::class.isInherited(feature))
    }
}
