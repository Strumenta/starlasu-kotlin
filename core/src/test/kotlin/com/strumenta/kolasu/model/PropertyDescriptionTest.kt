package com.strumenta.kolasu.model

import org.junit.Test
import java.util.LinkedList
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.test.assertEquals

data class Foo1(
    override val name: String,
) : Node(),
    Named

data class Foo2(
    val names: List<String>,
) : Node()

data class Foo3(
    val foo: Foo1,
) : Node()

data class Foo4(
    val foos: List<Foo1>?,
) : Node()

data class Foo5(
    val fooRef: ReferenceByName<Foo1>,
) : Node()

class PropertyDescriptionTest {
    @Test
    fun buildForNotNodeSingleProperty() {
        val list = LinkedList<PropertyDescription>()
        val instance = Foo1("gino")
        instance.processProperties {
            list.add(it)
        }
        assertEquals(1, list.size)
        assertEquals(
            PropertyDescription(
                "name",
                Multiplicity.SINGULAR,
                { "gino" },
                PropertyType.ATTRIBUTE,
                derived = false,
                type = String::class.createType(),
            ),
            list[0],
        )
    }

    @Test
    fun buildForNotNodeMultipleProperty() {
        val list = LinkedList<PropertyDescription>()
        val instance = Foo2(listOf("gino", "pino"))
        instance.processProperties {
            list.add(it)
        }
        assertEquals(1, list.size)
        assertEquals(
            PropertyDescription(
                "names",
                Multiplicity.MANY,
                { listOf("gino", "pino") },
                PropertyType.ATTRIBUTE,
                derived = false,
                type =
                    List::class.createType(
                        listOf(KTypeProjection.invariant(String()::class.createType())),
                    ),
            ),
            list[0],
        )
    }

    @Test
    fun buildForNodeSingleProperty() {
        val list = LinkedList<PropertyDescription>()
        val instance = Foo3(Foo1("gino"))
        instance.processProperties {
            list.add(it)
        }
        assertEquals(1, list.size)
        assertEquals(
            PropertyDescription(
                "foo",
                Multiplicity.SINGULAR,
                { Foo1("gino") },
                PropertyType.CONTAINMENT,
                derived = false,
                type = Foo1::class.createType(),
            ),
            list[0],
        )
    }

    @Test
    fun buildForNodeMultipleProperty() {
        val list = LinkedList<PropertyDescription>()
        val instance = Foo4(listOf(Foo1("gino")))
        instance.processProperties {
            list.add(it)
        }
        assertEquals(1, list.size)
        assertEquals(
            PropertyDescription(
                "foos",
                Multiplicity.MANY,
                { listOf(Foo1("gino")) },
                PropertyType.CONTAINMENT,
                derived = false,
                type =
                    List::class.createType(
                        listOf(KTypeProjection.invariant(Foo1::class.createType())),
                        nullable = true,
                    ),
            ),
            list[0],
        )
    }

    @Test
    fun buildForNodeMultiplePropertyEmpty() {
        val list = LinkedList<PropertyDescription>()
        val instance = Foo4(listOf())
        instance.processProperties {
            list.add(it)
        }
        assertEquals(1, list.size)
        assertEquals(
            PropertyDescription(
                "foos",
                Multiplicity.MANY,
                { emptyList<Foo1>() },
                PropertyType.CONTAINMENT,
                derived = false,
                type =
                    List::class.createType(
                        listOf(KTypeProjection.invariant(Foo1::class.createType())),
                        nullable = true,
                    ),
            ),
            list[0],
        )
    }

    @Test
    fun buildForNodeMultiplePropertyNull() {
        val list = LinkedList<PropertyDescription>()
        val instance = Foo4(null)
        instance.processProperties {
            list.add(it)
        }
        assertEquals(1, list.size)
        assertEquals(
            PropertyDescription(
                "foos",
                Multiplicity.MANY,
                { null },
                PropertyType.CONTAINMENT,
                derived = false,
                type =
                    List::class.createType(
                        listOf(KTypeProjection.invariant(Foo1::class.createType())),
                        nullable = true,
                    ),
            ),
            list[0],
        )
    }

    @Test
    fun buildForNodeWithReference() {
        val instance = Foo5(ReferenceByName(""))
        val list = instance.properties
        assertEquals(1, list.size)
        assertEquals(
            PropertyDescription(
                "fooRef",
                Multiplicity.SINGULAR,
                { ReferenceByName<Foo1>("") },
                PropertyType.REFERENCE,
                derived = false,
                type = Foo1::class.createType(),
            ),
            list[0],
        )
    }
}
