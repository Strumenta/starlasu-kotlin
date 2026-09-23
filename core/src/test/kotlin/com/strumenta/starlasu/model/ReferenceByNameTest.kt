package com.strumenta.starlasu.model

import com.strumenta.starlasu.language.Attribute
import com.strumenta.starlasu.language.Containment
import io.lionweb.model.AnnotationInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReferenceByNameTest {
    class BaseNamed(
        override val name: String,
    ) : Node(),
        Named

    /**
     * An [ASTNode] which does not extend [BaseASTNode], like the proxies used when loading models lazily.
     */
    class LightweightNamed(
        override val name: String,
    ) : ASTNode,
        Named {
        override var id: String? = null
        override val annotations: MutableList<AnnotationInstance> = mutableListOf()
        override var parent: ASTNode? = null
        override var source: Source? = null
        override var position: Position? = null
        override val sourceText: String? = null
        override val properties: List<PropertyDescription> = emptyList()
        override val originalProperties: List<PropertyDescription> = emptyList()
        override var origin: Origin? = null
        override var destination: Destination? = null
        override val nodeType: String = "LightweightNamed"
        override val simpleNodeType: String = "LightweightNamed"

        override fun getAttributeValue(attribute: Attribute): Any? = null

        override fun getAttributeValue(name: String): Any? = null

        override fun getChildren(
            containment: Containment,
            includeDerived: Boolean,
        ): List<ASTNode> = emptyList()

        override fun getChildren(
            propertyName: String,
            includeDerived: Boolean,
        ): List<ASTNode> = emptyList()

        override fun getReference(name: String): ReferenceByName<*>? = null

        override fun addAnnotation(instance: AnnotationInstance): Boolean = annotations.add(instance)
    }

    @Test
    fun `the 1_5 constructor forms are still available`() {
        val unresolved = ReferenceByName<Named>("a")
        assertEquals("a", unresolved.name)
        assertTrue(!unresolved.resolved)

        val target = BaseNamed("b")
        val resolvedAtConstruction = ReferenceByName("b", target)
        assertSame(target, resolvedAtConstruction.referred)
        assertTrue(resolvedAtConstruction.resolved)

        val byIdentifier = ReferenceByName<Named>("c", identifier = "id-c")
        assertTrue(byIdentifier.resolved)
        assertTrue(!byIdentifier.retrieved)
    }

    @Test
    fun `any ASTNode can be referred, not only BaseASTNode`() {
        val target = LightweightNamed("x")
        val ref = ReferenceByName("x", target)
        assertSame(target, ref.referred)
        assertTrue(ref.tryToResolve(listOf(LightweightNamed("y"), target)))
        assertSame(target, ref.referred)
    }
}
