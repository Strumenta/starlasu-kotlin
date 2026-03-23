package com.strumenta.kolasu.model

import java.util.concurrent.ConcurrentHashMap
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

private val nodePropertiesCache = ConcurrentHashMap<KClass<*>, Collection<KProperty1<*, *>>>()
private val nodeOriginalPropertiesCache = ConcurrentHashMap<KClass<*>, Collection<KProperty1<*, *>>>()
private val nodeDerivedPropertiesCache = ConcurrentHashMap<KClass<*>, Collection<KProperty1<*, *>>>()

@Suppress("UNCHECKED_CAST")
val <T : Any> KClass<T>.nodeProperties: Collection<KProperty1<T, *>>
    get() = nodePropertiesCache.computeIfAbsent(this) { kClass ->
        kClass.memberProperties.asSequence()
            .filter { it.visibility == kotlin.reflect.KVisibility.PUBLIC }
            .filter { it.findAnnotation<Internal>() == null }
            .filter { it.findAnnotation<Link>() == null }
            .map {
                require(it.name !in RESERVED_FEATURE_NAMES) {
                    "Property ${it.name} in ${kClass.qualifiedName} should be marked as internal"
                }
                it
            }
            .toList()
    } as Collection<KProperty1<T, *>>

@Suppress("UNCHECKED_CAST")
val <T : Any> KClass<T>.nodeOriginalProperties: Collection<KProperty1<T, *>>
    get() = nodeOriginalPropertiesCache.computeIfAbsent(this) { kClass ->
        kClass.nodeProperties.filter { it.findAnnotation<Derived>() == null }
    } as Collection<KProperty1<T, *>>

@Suppress("UNCHECKED_CAST")
val <T : Any> KClass<T>.nodeDerivedProperties: Collection<KProperty1<T, *>>
    get() = nodeDerivedPropertiesCache.computeIfAbsent(this) { kClass ->
        kClass.nodeProperties.filter { it.findAnnotation<Derived>() != null }
    } as Collection<KProperty1<T, *>>

/**
 * @return all properties of this node that are considered AST properties.
 */
val <T : Node> T.nodeProperties: Collection<KProperty1<T, *>>
    get() = (this::class as KClass<T>).nodeProperties

/**
 * @return all non-derived properties of this node that are considered AST properties.
 */
val <T : Node> T.nodeOriginalProperties: Collection<KProperty1<T, *>>
    get() = (this::class as KClass<T>).nodeOriginalProperties

/**
 * @return all derived properties of this node that are considered AST properties.
 */
val <T : Node> T.nodeDerivedProperties: Collection<KProperty1<T, *>>
    get() = (this::class as KClass<T>).nodeDerivedProperties
