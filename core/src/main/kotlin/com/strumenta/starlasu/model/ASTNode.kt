package com.strumenta.starlasu.model

import com.strumenta.starlasu.ids.HasID
import com.strumenta.starlasu.language.Attribute
import com.strumenta.starlasu.language.Containment
import com.strumenta.starlasu.language.Reference
import io.lionweb.model.AnnotationInstance

interface ASTNode :
    HasID,
    Origin,
    Destination {
    @Internal
    val annotations: MutableList<AnnotationInstance>

    /**
     * The parent node, if any.
     */
    @property:Internal
    var parent: ASTNode?

    @property:Internal
    override var source: Source?

    @property:Internal
    override var position: Position?

    @property:Internal
    val properties: List<PropertyDescription>

    @property:Internal
    val originalProperties: List<PropertyDescription>

    @property:Internal
    var origin: Origin?

    @Internal
    var destination: Destination?

    @Internal
    val nodeType: String

    @Internal
    open val simpleNodeType: String
        get() = nodeType.split(".").last()

    /**
     * Tests whether the given position is contained in the interval represented by this object.
     * @param position the position
     */
    fun contains(position: Position?): Boolean = this.position?.contains(position) ?: false

    /**
     * Tests whether the given position overlaps the interval represented by this object.
     * @param position the position
     */
    fun overlaps(position: Position?): Boolean = this.position?.overlaps(position) ?: false

    fun getAttributeValue(attribute: Attribute): Any?

    fun getChildren(
        containment: Containment,
        includeDerived: Boolean = false,
    ): List<ASTNode>

    fun getChildren(
        propertyName: String,
        includeDerived: Boolean = false,
    ): List<ASTNode>

    fun getReference(name: String): ReferenceByName<*>?

    fun getReference(reference: Reference): ReferenceByName<*>? = getReference(reference.name)

    fun getAttributeValue(name: String): Any?

    fun addAnnotation(instance: AnnotationInstance): Boolean
}
