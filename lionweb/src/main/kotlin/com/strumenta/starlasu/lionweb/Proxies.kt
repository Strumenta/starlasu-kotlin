package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.language.Attribute
import com.strumenta.starlasu.language.Containment
import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Destination
import com.strumenta.starlasu.model.Origin
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.PropertyDescription
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.Source
import io.lionweb.model.AnnotationInstance

data class ProxyNode(
    val nodeId: String,
) : ASTNode {
    override val annotations: MutableList<AnnotationInstance>
        get() = TODO("Not yet implemented")
    override var parent: ASTNode?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var source: Source?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var position: Position?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val properties: List<PropertyDescription>
        get() = TODO("Not yet implemented")
    override val originalProperties: List<PropertyDescription>
        get() = TODO("Not yet implemented")
    override var origin: Origin?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var destination: Destination?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val nodeType: String
        get() = TODO("Not yet implemented")

    override fun getAttributeValue(attribute: Attribute): Any? {
        TODO("Not yet implemented")
    }

    override fun getAttributeValue(name: String): Any? {
        TODO("Not yet implemented")
    }

    override fun getChildren(
        containment: Containment,
        includeDerived: Boolean,
    ): List<ASTNode> {
        TODO("Not yet implemented")
    }

    override fun getChildren(
        propertyName: String,
        includeDerived: Boolean,
    ): List<ASTNode> {
        TODO("Not yet implemented")
    }

    override fun getReference(name: String): ReferenceByName<*>? {
        TODO("Not yet implemented")
    }

    override fun addAnnotation(instance: AnnotationInstance): Boolean {
        TODO("Not yet implemented")
    }

    override var id: String?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val sourceText: String?
        get() = TODO("Not yet implemented")
}

object ProxyBasedNodeResolver : NodeResolver {
    override fun resolve(nodeID: String): SNode? = ProxyNode(nodeID)
}
