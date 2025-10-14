package com.strumenta.kolasu.model

import com.strumenta.kolasu.language.Attribute
import com.strumenta.kolasu.language.Containment
import com.strumenta.kolasu.language.Reference
import com.strumenta.kolasu.traversing.walk
import io.lionweb.model.AnnotationInstance
import java.io.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.cast

typealias Node = BaseASTNode

/**
 * The Abstract Syntax Tree will be constituted by instances of Node.
 *
 * It implements Origin as it could be the source of a AST-to-AST transformation, so the node itself can be
 * the Origin of another node.
 */
open class BaseASTNode() :
    Origin,
    Destination,
    Serializable,
    HasID,
    ASTNode {
    @Internal
    override val annotations = mutableListOf<AnnotationInstance>()

    @Internal
    override var id: String? = null

    @Internal
    protected var positionOverride: Position? = null

    constructor(position: Position?) : this() {
        this.position = position
    }

    constructor(origin: Origin?) : this() {
        if (origin != null) {
            this.origin = origin
        }
    }

    @Internal
    override val nodeType: String
        get() = this::class.qualifiedName!!

    @Internal
    open val simpleNodeType: String
        get() = nodeType.split(".").last()

    /**
     * The properties of this AST nodes, including attributes, children, and references.
     */
    @property:Internal
    override val properties: List<PropertyDescription>
        get() =
            try {
                nodeProperties.map { PropertyDescription.buildFor(it, this) }
            } catch (e: Throwable) {
                throw RuntimeException("Issue while getting properties of node ${this::class.qualifiedName}", e)
            }

    /**
     * The properties of this AST nodes, including attributes, children, and references, but excluding derived
     * properties.
     */
    @property:Internal
    override val originalProperties: List<PropertyDescription>
        get() =
            try {
                properties.filter { !it.derived }
            } catch (e: Throwable) {
                throw RuntimeException("Issue while getting properties of node ${this::class.qualifiedName}", e)
            }

    /**
     * The node from which this AST Node has been generated, if any.
     */
    @property:Internal
    override var origin: Origin? = null

    /**
     * The parent node, if any.
     */
    @property:Internal
    override var parent: ASTNode? = null

    /**
     * The position of this node in the source text.
     * If a position has been provided when creating this node, it is returned.
     * Otherwise, the value of this property is the position of the origin, if any.
     */
    @property:Internal
    override var position: Position?
        get() = positionOverride ?: origin?.position
        set(position) {
            this.positionOverride = position
        }

    private var explicitlySetSource: Source? = null

    @property:Internal
    override var source: Source?
        get() = explicitlySetSource ?: (position?.source ?: origin?.source)
        set(value) {
            explicitlySetSource = value
            if (value == null) {
                require(this.source == null)
            } else {
                require(this.source === value) {
                    "The source has not been set correctly. It should be $value " +
                        "while it is ${this.source}"
                }
            }
        }

    fun setSourceForTree(source: Source): BaseASTNode {
        this.source = source
        this.walk().forEach {
            it.source = source
        }
        return this
    }

    fun detach(
        keepPosition: Boolean = true,
        keepSourceText: Boolean = false,
    ) {
        val existingOrigin = origin
        if (existingOrigin != null) {
            if (keepPosition || keepSourceText) {
                this.origin =
                    SimpleOrigin(
                        if (keepPosition) existingOrigin.position else null,
                        if (keepSourceText) existingOrigin.sourceText else null,
                    )
            } else {
                this.origin = null
            }
            if (existingOrigin is BaseASTNode && existingOrigin.destination == this) {
                existingOrigin.destination = null
            }
        }
    }



    /**
     * The source text for this node
     */
    @Internal
    override val sourceText: String?
        get() = origin?.sourceText

    @Internal
    override var destination: Destination? = null

    /**
     * This must be final because otherwise data classes extending this will automatically generate
     * their own implementation. If Link properties are present it could lead to stack overflows in case
     * of circular graphs.
     */
    final override fun toString(): String =
        "${this.nodeType}(${originalProperties.joinToString(", ") { "${it.name}=${it.valueToString()}" }})"

    override fun getChildren(
        containment: Containment,
        includeDerived: Boolean
    ): List<ASTNode> = getChildren(containment.name, includeDerived)

    fun getChildren(
        propertyName: String,
        includeDerived: Boolean = false,
    ): List<ASTNode> {
        checkFeatureName(propertyName)
        val property =
            (if (includeDerived) properties else originalProperties)
                .find { it.name == propertyName }
        require(property != null) {
            "Property $propertyName not found in node of type ${this.nodeType} " +
                "(considering derived properties? $includeDerived)"
        }
        return when (
            val rawValue = property!!.value
        ) {
            null -> {
                emptyList()
            }

            is List<*> -> {
                rawValue as List<Node>
            }

            else -> {
                listOf(rawValue as Node)
            }
        }
    }

    fun getReference(reference: Reference): ReferenceByName<*>? = getReference(reference.name)

    fun getReference(name: String): ReferenceByName<*>? {
        val rawValue = properties.find { it.name == name }!!.value
        if (rawValue == null) {
            return null
        }
        return rawValue as ReferenceByName<*>
    }

    override fun getAttributeValue(attribute: Attribute): Any? {
        val value = getAttributeValue(attribute.name)
        if (value == null) {
            if (!attribute.optional) {
                throw IllegalStateException("Mandatory attribute ${attribute.name} is null")
            }
        } else {
            val classifier = attribute.type.classifier
            if (classifier is KClass<*>) {
                return classifier.cast(value)
            }
        }
        return value
    }

    fun getAttributeValue(name: String): Any? = properties.find { it.name == name }!!.value

    fun addAnnotation(instance: AnnotationInstance): Boolean {
        if (this.annotations.contains(instance)) {
            return false
        }
        this.annotations.add(instance)
        return true
    }
}
