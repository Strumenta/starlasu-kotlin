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
//val <T : Any> KClass<T>.nodeProperties: Collection<KProperty1<T, *>>
//    get() = memberProperties.asSequence()
//        .filter { it.visibility == KVisibility.PUBLIC }
//        .filter { it.findAnnotation<Internal>() == null }
//        .filter { it.findAnnotation<Link>() == null }
//        .map {
//            require(it.name !in RESERVED_FEATURE_NAMES) {
//                "Property ${it.name} in ${this.qualifiedName} should be marked as internal"
//            }
//            it
//        }
//        .toList()


// 1. Define a thread-safe static cache for class properties
private val nodePropertiesCache = java.util.concurrent.ConcurrentHashMap<kotlin.reflect.KClass<*>, Collection<kotlin.reflect.KProperty1<*, *>>>()

// 2. Update the extension property to use the cache
@Suppress("UNCHECKED_CAST")
val <T : Any> KClass<T>.nodeProperties: Collection<KProperty1<T, *>>
    get() = nodePropertiesCache.computeIfAbsent(this) { kClass ->
        // This heavy reflection block will now run EXACTLY ONCE per class type
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

val <T : Any> KClass<T>.nodeOriginalProperties: Collection<KProperty1<T, *>>
    get() = nodeProperties
        .filter { it.findAnnotation<Derived>() == null }

val <T : Any> KClass<T>.nodeDerivedProperties: Collection<KProperty1<T, *>>
    get() = nodeProperties
        .filter { it.findAnnotation<Derived>() != null }

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
