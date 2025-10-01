package com.strumenta.kolasu.model

import com.strumenta.kolasu.language.Attribute
import com.strumenta.kolasu.language.Containment
import com.strumenta.kolasu.language.Reference
import com.strumenta.kolasu.traversing.walk
import io.lionweb.model.AnnotationInstance
import java.io.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.reflect.cast

val RESERVED_FEATURE_NAMES = setOf("parent", "position", "id", "annotations")

/**
 * The Abstract Syntax Tree will be constituted by instances of Node.
 *
 * It implements Origin as it could be the source of a AST-to-AST transformation, so the node itself can be
 * the Origin of another node.
 */
open class Node() : Origin, Destination, Serializable, HasID {

    @Internal
    override var id: String? = null

    @Internal
    val annotations: MutableList<AnnotationInstance> = CopyOnWriteArrayList()

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
    open val nodeType: String
        get() = this::class.qualifiedName!!

    @Internal
    open val simpleNodeType: String
        get() = nodeType.split(".").last()

    /**
     * The properties of this AST nodes, including attributes, children, and references.
     */
    @property:Internal
    open val properties: List<PropertyDescription>
        get() = originalProperties + derivedProperties

    @property:Internal
    open val derivedProperties: List<PropertyDescription>
        get() = try {
            nodeDerivedProperties.map { PropertyDescription.buildFor(it, this) }
        } catch (e: Throwable) {
            throw RuntimeException("Issue while getting properties of node ${this::class.qualifiedName}", e)
        }

    /**
     * The properties of this AST nodes, including attributes, children, and references, but excluding derived
     * properties.
     */
    @property:Internal
    open val originalProperties: List<PropertyDescription>
        get() = try {
            nodeOriginalProperties.map { PropertyDescription.buildFor(it, this) }
        } catch (e: Throwable) {
            throw RuntimeException("Issue while getting properties of node ${this::class.qualifiedName}", e)
        }

    /**
     * The node from which this AST Node has been generated, if any.
     */
    @property:Internal
    var origin: Origin? = null

    /**
     * The parent node, if any.
     */
    @property:Internal
    var parent: Node? = null

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

    fun setSourceForTree(source: Source): Node {
        this.source = source
        this.walk().forEach {
            it.source = source
        }
        return this
    }

    fun detach(keepPosition: Boolean = true, keepSourceText: Boolean = false) {
        val existingOrigin = origin
        if (existingOrigin != null) {
            if (keepPosition || keepSourceText) {
                this.origin = SimpleOrigin(
                    if (keepPosition) existingOrigin.position else null,
                    if (keepSourceText) existingOrigin.sourceText else null
                )
            } else {
                this.origin = null
            }
            if (existingOrigin is Node && existingOrigin.destination == this) {
                existingOrigin.destination = null
            }
        }
    }

    /**
     * Tests whether the given position is contained in the interval represented by this object.
     * @param position the position
     */
    fun contains(position: Position?): Boolean {
        return this.position?.contains(position) ?: false
    }

    /**
     * Tests whether the given position overlaps the interval represented by this object.
     * @param position the position
     */
    fun overlaps(position: Position?): Boolean {
        return this.position?.overlaps(position) ?: false
    }

    /**
     * The source text for this node
     */
    @Internal
    override val sourceText: String?
        get() = origin?.sourceText

    @Internal
    var destination: Destination? = null

    /**
     * This must be final because otherwise data classes extending this will automatically generate
     * their own implementation. If Link properties are present it could lead to stack overflows in case
     * of circular graphs.
     */
    final override fun toString(): String {
        return "${this.nodeType}(${originalProperties.joinToString(", ") { "${it.name}=${it.valueToString()}" }})"
    }

    fun getChildren(containment: Containment, includeDerived: Boolean = false): List<Node> {
        return getChildren(containment.name, includeDerived)
    }

    fun getChildren(propertyName: String, includeDerived: Boolean = false): List<Node> {
        checkFeatureName(propertyName)
        val property = (if (includeDerived) properties else originalProperties)
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

    fun getReference(reference: Reference): ReferenceByName<*>? {
        return getReference(reference.name)
    }

    fun getReference(name: String): ReferenceByName<*>? {
        val rawValue = properties.find { it.name == name }!!.value
        if (rawValue == null) {
            return null
        }
        return rawValue as ReferenceByName<*>
    }

    fun getAttributeValue(attribute: Attribute): Any? {
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

    fun getAttributeValue(name: String): Any? {
        return properties.find { it.name == name }!!.value
    }
}

fun <N : Node> N.withPosition(position: Position?): N {
    this.position = position
    return this
}

fun <N : Node> N.withOrigin(origin: Origin?): N {
    this.origin = if (origin == this) {
        null
    } else {
        origin
    }
    return this
}

fun <N : Node> N.withDestination(destination: Destination): N {
    this.destination = if (destination == this) {
        null
    } else {
        destination
    }
    return this
}

fun <N : Node> N.withDroppedDestination(): N {
    return withDestination(DroppedDestination)
}

/**
 * Use this to mark properties that are internal, i.e., they are used for bookkeeping and are not part of the model,
 * so that they will not be considered branches of the AST.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Internal

/**
 * Use this to mark all relations which are secondary, i.e., they are calculated from other relations,
 * so that they will not be considered branches of the AST.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Derived

/**
 * Use this to mark all the properties that return a Node or a list of Nodes which are not
 * contained by the Node having the properties. In other words: they are just references.
 * This will prevent them from being considered branches of the AST.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Link

/**
 * Use this to mark something that does not inherit from Node as a node, so it will be included in the AST.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NodeType

fun checkFeatureName(featureName: String) {
    require(featureName !in RESERVED_FEATURE_NAMES) { "$featureName is not a valid feature name" }
}

/**
 * Use this to mark a type representing an AST Root.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ASTRoot(val canBeNotRoot: Boolean = false)
