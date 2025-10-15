package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.mapping.translateList
import com.strumenta.starlasu.model.Node
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

val IDENTTITY_TRANSFORMATION: (
    source: Any?,
    parent: Node?,
    expectedType: KClass<out Node>,
    astTransformer: ASTTransformer,
) -> List<Node> = { source: Any?, parent: Node?, expectedType: KClass<out Node>, astTransformer: ASTTransformer ->
    when (source) {
        null -> {
            emptyList()
        }

        is Node -> {
            val kClass = source.javaClass.kotlin
            val primaryConstructor =
                kClass.primaryConstructor
                    ?: throw IllegalStateException(
                        "No primary constructor found for $kClass: cannot apply " +
                            "identity transformation",
                    )
            val params = mutableMapOf<KParameter, Any?>()
            primaryConstructor.parameters.forEach { parameter ->
                val mt = parameter.type.javaType
                val correspondingProperty =
                    source.javaClass.kotlin.memberProperties.find {
                        it.name == parameter.name
                    } ?: throw IllegalStateException(
                        "Cannot find property named as parameter $parameter",
                    )
                val originalValue = correspondingProperty.get(source)
                // mt is ParameterizedType && mt.rawType == List::class.java -> mutableListOf<Any>()
                when {
                    (parameter.type.classifier as KClass<*>).isSubclassOf(Node::class) -> {
                        params[parameter] = astTransformer.transform(originalValue)
                    }

                    mt is ParameterizedType &&
                        mt.rawType == List::class.java &&
                        (mt.actualTypeArguments.first() as? Class<*>)?.kotlin?.isSubclassOf(Node::class) == true -> {
                        params[parameter] = astTransformer.translateList<Node>(originalValue as List<Node>)
                    }

                    else -> params[parameter] = originalValue
                }
            }

            val newInstance = primaryConstructor.callBy(params) as Node
            newInstance.parent = parent
            newInstance.origin = source
            listOf(newInstance)
        }

        else -> {
            throw IllegalArgumentException("An Identity Transformation expect to receive a Node")
        }
    }
}
