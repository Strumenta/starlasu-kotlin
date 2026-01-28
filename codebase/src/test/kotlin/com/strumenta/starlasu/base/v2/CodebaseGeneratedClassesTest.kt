package com.strumenta.starlasu.base.v2

import io.lionweb.kotlin.createConcept
import io.lionweb.kotlin.getPropertyValueByName
import io.lionweb.kotlin.lwLanguage
import io.lionweb.language.Concept
import io.lionweb.model.Node
import io.lionweb.model.ReferenceValue
import io.lionweb.model.impl.DynamicNode
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

object Instantiator {
    private var nextId = 0

    fun <T : Node> instantiate(clazz: KClass<T>): T = clazz.constructors.first().call("id-${++nextId}")

    fun instantiate(concept: Concept): DynamicNode = DynamicNode("id-${++nextId}", concept)
}

fun <T : Node> KClass<T>.instantiate(): T = Instantiator.instantiate(this)

fun Concept.instantiate(): DynamicNode = Instantiator.instantiate(this)

class CodebaseGeneratedClassesTest {
    val simpleLanguage =
        lwLanguage("SimpleLanguage").apply {
            createConcept("SimpleConcept")
        }

    val simpleConcept = simpleLanguage.requireConceptByName("SimpleConcept")

    @Test
    fun builtinsCollection() {
        val bc = BuiltinsCollection::class.instantiate()
        bc.languageName = "MyLanguage"
        assertEquals("MyLanguage", bc.languageName)
        assertEquals("MyLanguage", bc.getPropertyValueByName("languageName"))

        val sc1 = simpleConcept.instantiate()
        val sc2 = simpleConcept.instantiate()
        val sc3 = simpleConcept.instantiate()

        bc.addToBuiltins(sc1)
        bc.addToBuiltins(sc3)
        bc.addToBuiltins(sc2, 1)
        assertEquals(listOf(sc1, sc2, sc3), bc.builtins)
        assertSame(bc, sc1.parent)
        assertSame(bc, sc2.parent)
        assertSame(bc, sc3.parent)
        bc.clearBuiltins()
        assertEquals(emptyList(), bc.builtins)
        assertNull(sc1.parent)
        assertNull(sc2.parent)
        assertNull(sc3.parent)

        val sc4 = simpleConcept.instantiate()
        val sc5 = simpleConcept.instantiate()

        bc.setBuiltins(listOf(sc4, sc5))
        assertEquals(listOf(sc4, sc5), bc.builtins)
        assertSame(bc, sc4.parent)
        assertSame(bc, sc5.parent)

        bc.removeFromBuiltins(sc4)
        assertNull(sc4.parent)
        assertEquals(listOf(sc5), bc.builtins)

        bc.removeFromBuiltins(sc5)
        assertNull(sc5.parent)
        assertEquals(emptyList(), bc.builtins)
    }

    @Test
    fun codebase() {
        val codebase = Codebase::class.instantiate()
        val f1 = CodebaseFile::class.instantiate()
        val f2 = CodebaseFile::class.instantiate()
        val f3 = CodebaseFile::class.instantiate()

        codebase.addToFiles(f1)
        assertEquals(listOf(ReferenceValue(f1, null)), codebase.files)
        codebase.addToFiles(f3)
        assertEquals(
            listOf(
                ReferenceValue(f1, null),
                ReferenceValue(f3, null),
            ),
            codebase.files,
        )
        codebase.addToFiles(f2, 1)
        assertEquals(
            listOf(
                ReferenceValue(f1, null),
                ReferenceValue(f2, null),
                ReferenceValue(f3, null),
            ),
            codebase.files,
        )

        codebase.clearFiles()
        assertEquals(emptyList(), codebase.files)
    }
}
