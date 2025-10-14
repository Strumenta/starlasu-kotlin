package com.strumenta.kolasu.model

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

val <T : Any> Class<T>.nodeProperties: Collection<KProperty1<T, *>>
    get() = this.kotlin.nodeProperties
val <T : Any> Class<T>.nodeOriginalProperties: Collection<KProperty1<T, *>>
    get() = this.kotlin.nodeOriginalProperties
val <T : Any> Class<T>.nodeDerivedProperties: Collection<KProperty1<T, *>>
    get() = this.kotlin.nodeDerivedProperties
val <T : Any> KClass<T>.nodeProperties: Collection<KProperty1<T, *>>
    get() =
        memberProperties
            .asSequence()
            .filter { it.visibility == KVisibility.PUBLIC }
            .filter { it.findAnnotation<Internal>() == null }
            .filter { it.findAnnotation<Link>() == null }
            .map {
                require(it.name !in RESERVED_FEATURE_NAMES) {
                    "Property ${it.name} in ${this.qualifiedName} should be marked as internal"
                }
                it
            }.toList()

val <T : Any> KClass<T>.nodeOriginalProperties: Collection<KProperty1<T, *>>
    get() =
        nodeProperties
            .filter { it.findAnnotation<Derived>() == null }

val <T : Any> KClass<T>.nodeDerivedProperties: Collection<KProperty1<T, *>>
    get() =
        nodeProperties
            .filter { it.findAnnotation<Derived>() != null }

/**
 * @return all properties of this node that are considered AST properties.
 */
val <T : ASTNode> T.nodeProperties: Collection<KProperty1<T, *>>
    get() = (this::class as KClass<T>).nodeProperties

/**
 * @return all non-derived properties of this node that are considered AST properties.
 */
val <T : ASTNode> T.nodeOriginalProperties: Collection<KProperty1<T, *>>
    get() = (this::class as KClass<T>).nodeOriginalProperties

/**
 * @return all derived properties of this node that are considered AST properties.
 */
val <T : ASTNode> T.nodeDerivedProperties: Collection<KProperty1<T, *>>
    get() = (this::class as KClass<T>).nodeDerivedProperties
