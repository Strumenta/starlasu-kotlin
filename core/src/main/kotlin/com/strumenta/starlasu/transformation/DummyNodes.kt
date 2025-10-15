package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.isContainment
import com.strumenta.starlasu.model.nodeProperties
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * This logic instantiate a node of the given class with dummy values.
 * This is useful because it permits to add an element that "fit" and make the typesystem happy.
 * Typically, the only goal of the element would be to hold some annotation that indicates that the element
 * is representing an error or a missing transformation or something of that sort.
 */
fun <T : ASTNode> KClass<T>.dummyInstance(levelOfDummyTree: Int = 0): T {
    val kClassToInstantiate = this.toInstantiableType(levelOfDummyTree)
    val emptyConstructor = kClassToInstantiate.constructors.find { it.parameters.isEmpty() }
    if (emptyConstructor != null) {
        return emptyConstructor.call()
    }
    val constructor = kClassToInstantiate.primaryConstructor!!
    val params = mutableMapOf<KParameter, Any?>()
    constructor.parameters.forEach { param ->
        val mt = param.type.javaType
        val value =
            when {
                param.type.isMarkedNullable -> null
                mt is ParameterizedType && mt.rawType == List::class.java -> mutableListOf<Any>()
                (param.type.classifier as KClass<*>).isSubclassOf(Node::class) ->
                    (param.type.classifier as KClass<out Node>).dummyInstance(levelOfDummyTree + 1)

                param.type == String::class.createType() -> "DUMMY"
                param.type.classifier == ReferenceByName::class -> ReferenceByName<PossiblyNamed>("UNKNOWN")
                param.type == Int::class.createType() -> 0
                param.type == Float::class.createType() -> 0.0f
                param.type == Long::class.createType() -> 0L
                param.type == Double::class.createType() -> 0.0
                param.type == Boolean::class.createType() -> false
                (param.type.classifier as KClass<*>).isSubclassOf(Enum::class)
                -> (param.type.classifier as KClass<*>).java.enumConstants[0]

                else -> TODO("Param type ${param.type}")
            }
        params[param] = value
    }
    return constructor.callBy(params)
}

private fun <T : Any> KClass<T>.toInstantiableType(levelOfDummyTree: Int = 0): KClass<out T> =
    when {
        this.isSealed -> {
            val subclasses = this.sealedSubclasses.filter { it.isDirectlyOrIndirectlyInstantiable() }
            if (subclasses.isEmpty()) {
                throw IllegalStateException("$this has no instantiable sealed subclasses")
            }
            val subClassWithEmptyParam = subclasses.find { it.constructors.any { it.parameters.isEmpty() } }
            if (subClassWithEmptyParam == null) {
                if (subclasses.size == 1) {
                    subclasses.first().toInstantiableType(levelOfDummyTree + 1)
                } else {
                    // Some constructs are recursive (think of the ArrayType)
                    // So we want to avoid just using the same subclass, repeatedly as it would lead to an
                    // infinite loop. Therefore we sort subclasses by the order of containments, preferring the ones
                    // with no or few containments and we take them consider the level of depth in the dummy tree
                    val subclassesByNumberOfContainments =
                        subclasses
                            .map {
                                it to
                                    it.nodeProperties.count {
                                        it.isContainment()
                                    }
                            }.toList()
                            .sortedBy { it.second }
                            .map { it.first }
                    subclasses[levelOfDummyTree].toInstantiableType(levelOfDummyTree + 1)
                }
            } else {
                subClassWithEmptyParam.toInstantiableType(levelOfDummyTree + 1)
            }
        }

        this.isAbstract -> {
            throw IllegalStateException("We cannot instantiate an abstract class (but we can handle sealed classes)")
        }

        this.java.isInterface -> {
            throw IllegalStateException("We cannot instantiate an interface")
        }

        else -> {
            this
        }
    }

/**
 * We can only instantiate concrete classes or sealed classes, if one its subclasses is directly or
 * indirectly instantiable. For interfaces this return false.
 */
fun <T : Any> KClass<T>.isDirectlyOrIndirectlyInstantiable(): Boolean =
    if (this.isSealed) {
        this.sealedSubclasses.any { it.isDirectlyOrIndirectlyInstantiable() }
    } else {
        !this.isAbstract && !this.java.isInterface
    }
