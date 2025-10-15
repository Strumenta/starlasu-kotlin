package com.strumenta.starlasu.language

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.CommonElement
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.asAttribute
import com.strumenta.starlasu.model.containedType
import com.strumenta.starlasu.model.isAttribute
import com.strumenta.starlasu.model.isContainment
import com.strumenta.starlasu.model.isMarkedAsNodeType
import com.strumenta.starlasu.model.isReference
import com.strumenta.starlasu.model.nodeProperties
import com.strumenta.starlasu.model.referredType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses

/**
 * There is no explicit Language defined in Kolasu, it is just a bunch of AST classes.
 * We create this Class to represent that collection of AST classes.
 */
@Deprecated("Use LionWeb's Language")
class KolasuLanguage(
    val qualifiedName: String,
) {
    val astClasses: List<KClass<*>>
        get() = _astClasses
    val enumClasses: List<KClass<out Enum<*>>>
        get() = _enumClasses
    val primitiveClasses: List<KClass<*>>
        get() = _primitiveClasses
    private val _astClasses: MutableList<KClass<*>> = mutableListOf()
    private val _enumClasses: MutableList<KClass<out Enum<*>>> = mutableListOf()
    private val _primitiveClasses: MutableList<KClass<*>> = mutableListOf()

    val simpleName: String
        get() = qualifiedName.split(".").last()

    fun addEnumClass(kClass: KClass<out Enum<*>>) {
        if (_enumClasses.contains(kClass)) {
            return
        }
        _enumClasses.add(kClass)
    }

    fun addPrimitiveClass(kClass: KClass<*>) {
        if (kClass in setOf(Int::class, String::class, Boolean::class, Char::class)) {
            return
        }
        if (_primitiveClasses.contains(kClass)) {
            return
        }
        _primitiveClasses.add(kClass)
    }

    fun addInterfaceClass(kClass: KClass<*>): Boolean {
        val attempt = tentativeAddInterfaceClass(kClass)
        if (attempt.issues.isEmpty()) {
            return attempt.result
        } else {
            throw RuntimeException(
                "Issues prevented from adding $kClass:\n${
                    attempt.issues.map { it.message }
                        .joinToString("\n")
                }",
            )
        }
    }

    fun tentativeAddInterfaceClass(
        kClass: KClass<*>,
        exceptions: MutableList<Exception> = mutableListOf(),
    ): Attempt<Boolean, Exception> {
        if (!_astClasses.contains(kClass) && _astClasses.add(kClass)) {
            kClass.supertypes.forEach { superType -> processSuperType(superType, exceptions) }
            return Attempt(true, exceptions)
        } else {
            return Attempt(false, exceptions)
        }
    }

    private fun processSuperType(
        superType: KType,
        exceptions: MutableList<Exception> = mutableListOf(),
    ) {
        // In case the super type is already mapped to another language we may want to not add it into this language,
        // once we introduce the concept of extending languages
        val kClass = superType.classifier as? KClass<*>
        when (kClass) {
            null -> Unit
            Node::class -> Unit
            Named::class -> Unit
            PossiblyNamed::class -> Unit
            Any::class -> Unit
            else -> {
                if (kClass.java.isInterface) {
                    if (kClass.isMarkedAsNodeType() && !kClass.isSubclassOf(CommonElement::class)) {
                        // Note: CommonElement subclasses are added to the Starlasu LW language manually
                        tentativeAddInterfaceClass(kClass, exceptions)
                    }
                } else {
                    if (kClass.isSubclassOf(Node::class)) {
                        tentativeAddClass(kClass as KClass<out Node>, exceptions)
                    }
                }
            }
        }
    }

    fun <N : Node> addClass(kClass: KClass<N>): Boolean {
        val attempt = tentativeAddClass(kClass)
        if (attempt.issues.isEmpty()) {
            return attempt.result
        } else {
            throw RuntimeException(
                "Issues prevented from adding $kClass:\n${
                    attempt.issues.map { it.message }
                        .joinToString("\n")
                }",
            )
        }
    }

    fun <N : ASTNode> tentativeAddClass(
        kClass: KClass<N>,
        exceptions: MutableList<Exception> = mutableListOf(),
    ): Attempt<Boolean, Exception> {
        if (kClass == Node::class ||
            kClass == Named::class ||
            kClass == PossiblyNamed::class ||
            kClass.superclasses.contains(
                CommonElement::class,
            )
        ) {
            return Attempt(false, exceptions)
        }
        if (!_astClasses.contains(kClass) && _astClasses.add(kClass)) {
            kClass.supertypes.forEach { superType ->
                processSuperType(superType, exceptions)
            }
            if (kClass.isSealed) {
                kClass.sealedSubclasses.forEach {
                    tentativeAddClass(it, exceptions)
                }
            }
            kClass.nodeProperties.forEach { nodeProperty ->
                if (nodeProperty.isContainment()) {
                    tentativeAddClass(nodeProperty.containedType(), exceptions)
                } else if (nodeProperty.isReference()) {
                    tentativeAddClass(nodeProperty.referredType(), exceptions)
                } else if (nodeProperty.isAttribute()) {
                    try {
                        val attributeKClass = nodeProperty.asAttribute().type.classifier as? KClass<*>
                        if (attributeKClass != null) {
                            if (attributeKClass.superclasses.contains(Enum::class)) {
                                addEnumClass(attributeKClass as KClass<out Enum<*>>)
                            } else {
                                addPrimitiveClass(attributeKClass as KClass<*>)
                            }
                        }
                    } catch (e: Exception) {
                        exceptions.add(
                            RuntimeException(
                                "Issue while examining kotlin class $kClass and its property $nodeProperty",
                                e,
                            ),
                        )
                    }
                }
            }
            return Attempt(true, exceptions)
        } else {
            return Attempt(false, exceptions)
        }
    }

    fun findASTClass(name: String): KClass<*>? = astClasses.find { it.simpleName == name }

    fun findEnumClass(name: String): KClass<out Enum<*>>? = enumClasses.find { it.simpleName == name }

    fun findPrimitiveClass(name: String): KClass<*>? = primitiveClasses.find { it.simpleName == name }
}

data class Attempt<V, I>(
    val result: V,
    val issues: List<I>,
)
