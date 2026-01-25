package com.strumenta.starlasu.base.v2

import io.lionweb.kotlin.createConcept
import io.lionweb.kotlin.getPropertyValueByName
import io.lionweb.kotlin.lwLanguage
import io.lionweb.language.Concept
import io.lionweb.model.Node
import io.lionweb.model.impl.DynamicNode
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

object Instantiator {
    private var nextId = 0
    fun <T: Node> instantiate(clazz: KClass<T>) : T = clazz.constructors.first().call("id-${++nextId}")
    fun instantiate(concept: Concept) : DynamicNode = DynamicNode("id-${++nextId}", concept)
}

fun <T:Node>KClass<T>.instantiate() : T = Instantiator.instantiate(this)

fun Concept.instantiate() : DynamicNode = Instantiator.instantiate(this)

class CodebaseGeneratedClassesTest {

    val simpleLanguage = lwLanguage("SimpleLanguage").apply {
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
        //bc.addToBuiltins(sc1, 0)
        //bc.addToBuiltins(sc2)
        //bc.setBuiltins(listOf(sc1, sc2, sc3))
    }
}