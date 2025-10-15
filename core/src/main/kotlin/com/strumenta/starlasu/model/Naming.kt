package com.strumenta.starlasu.model

import java.io.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf

/**
 * An entity that can have a name
 */
@NodeType
interface PossiblyNamed {
    /**
     * The optional name of the entity.
     */
    val name: String?
}

/**
 * An entity which has a name.
 */
@NodeType
interface Named : PossiblyNamed {
    /**
     * The mandatory name of the entity.
     */
    override val name: String
}

/**
 * A reference associated by using a name.
 * It can be used only to refer to Nodes and not to other values.
 *
 * This is not statically enforced as we may want to use some interface, which cannot extend Node.
 * However, this is enforced dynamically.
 *
 * The node referenced by a ReferenceByName instance can be contained in the same AST or some external source
 * (typically other ASTs). In the latter case, we might want to indicate that, although we know which node the reference
 * is pointing to, we do not want to retrieve it straight away for performance reasons. In these circumstances,
 * the `referred` field is null and the `identifier` field is used instead. This, will be used to retrieve the
 * actual node at a later stage.
 */
class ReferenceByName<N : PossiblyNamed>
    @JvmOverloads
    constructor(
        var name: String,
        initialReferred: N? = null,
        var identifier: String? = null,
    ) : Serializable {
        var referred: N? = null
            set(value) {
                require(value is Node || value == null) {
                    "We cannot enforce it statically but only Node should be referred to. Instead $value was " +
                        "assigned (class: ${value?.javaClass})"
                }
                field = value
            }

        init {
            this.referred = initialReferred
        }

        val resolved: Boolean
            get() = identifier != null || retrieved

        val retrieved: Boolean
            get() = referred != null

        override fun toString(): String =
            if (resolved) {
                "Ref($name)[Solved]"
            } else {
                "Ref($name)[Unsolved]"
            }

        override fun hashCode(): Int = name.hashCode() * (1 + identifier.hashCode()) * (7 + if (resolved) 2 else 1)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ReferenceByName<*>) return false
            if (name != other.name) return false
            if (identifier != other.identifier) return false
            if (referred != other.referred) return false
            return true
        }
    }

/**
 * Try to resolve the reference by finding a named element with a matching name.
 * The name match is performed in a case sensitive or insensitive way depending on the value of @param[caseInsensitive].
 */
fun <N> ReferenceByName<N>.tryToResolve(
    candidates: Iterable<N>,
    caseInsensitive: Boolean = false,
): Boolean where N : PossiblyNamed {
    val res: N? = candidates.find { if (it.name == null) false else it.name.equals(this.name, caseInsensitive) }
    this.referred = res
    return res != null
}

/**
 * Try to resolve the reference by assigining @param[possibleValue]. The assignment is not performed if
 * @param[possibleValue] is null.
 *
 * @return true if the assignment has been performed
 */
fun <N> ReferenceByName<N>.tryToResolve(possibleValue: N?): Boolean where N : PossiblyNamed =
    if (possibleValue == null) {
        false
    } else {
        this.referred = possibleValue
        true
    }

/**
 * Typealias representing reference properties.
 **/
typealias KReferenceByName<S> = KProperty1<S, ReferenceByName<out PossiblyNamed>?>

/**
 * Builds a type representation for a reference
 **/
fun kReferenceByNameType(targetClass: KClass<out PossiblyNamed> = PossiblyNamed::class): KType =
    ReferenceByName::class.createType(
        arguments = listOf(KTypeProjection(variance = KVariance.OUT, type = targetClass.createType())),
        nullable = true,
    )

/**
 * Retrieves the referred type for a given reference property.
 **/
@Suppress("unchecked_cast")
fun KReferenceByName<*>.getReferredType(): KClass<out PossiblyNamed> =
    this.returnType.arguments[0]
        .type!!
        .classifier!! as KClass<out PossiblyNamed>

/**
 * Retrieves all reference properties for a given node.
 **/
fun ASTNode.kReferenceByNameProperties(targetClass: KClass<out PossiblyNamed> = PossiblyNamed::class) =
    this.nodeProperties.filter { it.returnType.isSubtypeOf(kReferenceByNameType(targetClass)) }
