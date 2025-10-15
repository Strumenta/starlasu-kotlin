package com.strumenta.starlasu.serialization

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.parsing.ParsingResult
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueSeverity
import com.strumenta.starlasu.validation.IssueType
import com.strumenta.starlasu.validation.Result
import java.lang.reflect.InvocationTargetException
import java.util.LinkedList
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

class JsonDeserializer {
    private val gsonBuilder = GsonBuilder()

    fun registerCustomDeserializer(
        type: KType,
        serializer: com.google.gson.JsonDeserializer<*>,
    ) {
        gsonBuilder.registerTypeAdapter(type.javaType, serializer)
    }

    private fun deserializeType(
        typeToDeserialize: KType,
        json: JsonElement?,
    ): Any? {
        if (json == null) {
            return null
        }
        if (typeToDeserialize.classifier is KClass<*>) {
            val rawClass: Class<*> = (typeToDeserialize.classifier as KClass<*>).java
            when {
                Node::class.java.isAssignableFrom(rawClass) -> {
                    val className = json.asJsonObject[JSON_TYPE_KEY].asString
                    val actualClass =
                        try {
                            Class.forName(className)
                        } catch (ex: ClassNotFoundException) {
                            // This handles only the case when the serialized JSON used the simple name
                            // rather than the canonical name for indicating the type of the serialized object.
                            // The rawClass is the class we are expecting,
                            // while className is the type in the JSON serialization.
                            // These two types could be different: rawClass could be a sealed or abstract type,
                            // while className could be a compatible concrete class.
                            // So, when className is a simple name and we cannot resolve it directly,
                            // we need to get the package name of the rawClass and apply it to className.
                            Class.forName(
                                "${
                                    rawClass.canonicalName.substring(
                                        0,
                                        rawClass.canonicalName.lastIndexOf('.') + 1,
                                    )
                                }$className",
                            )
                        }
                    return deserialize(actualClass.asSubclass(Node::class.java), json.asJsonObject)
                }

                Collection::class.java.isAssignableFrom(rawClass) -> {
                    require(typeToDeserialize.arguments.size == 1)
                    val elementType = typeToDeserialize.arguments[0]
                    val ja = json.asJsonArray
                    val list = LinkedList<Any>()
                    for (jel in ja) {
                        list.add(deserializeType(elementType.type!!, jel)!!)
                    }
                    return list
                }
                // TODO handle enum classes
                rawClass == String::class.java -> {
                    return json.asString
                }

                rawClass == Int::class.java -> {
                    return json.asInt
                }

                else -> {
                    if (json.isJsonObject) {
                        if (json.asJsonObject.has(JSON_TYPE_KEY)) {
                            val type = json.asJsonObject.get(JSON_TYPE_KEY).asString
                            val clazz =
                                try {
                                    Class.forName(type)
                                } catch (ex: ClassNotFoundException) {
                                    Class.forName(
                                        "${
                                            rawClass.canonicalName.substring(
                                                0,
                                                rawClass.canonicalName.lastIndexOf('.') + 1,
                                            )
                                        }$type",
                                    )
                                }
                            if (clazz == null) {
                                throw IllegalStateException("Unable to find class $type")
                            } else {
                                return deserializeType(clazz.kotlin.createType(), json)
                            }
                        }
                    }
                    return gsonBuilder.create().fromJson(json, typeToDeserialize.javaType)
                }
            }
        }
        TODO()
    }

    fun <T : Node> deserialize(
        clazz: Class<T>,
        json: String,
    ): T {
        val jo = JsonParser().parse(json).asJsonObject
        return deserialize(clazz, jo)
    }

    fun <T : Node> deserialize(
        clazz: Class<T>,
        jo: JsonObject,
    ): T {
        val instance: T?
        val primaryConstructor = clazz.kotlin.primaryConstructor
        if (primaryConstructor != null) {
            val args = HashMap<KParameter, Any?>()
            for (p in primaryConstructor.parameters) {
                try {
                    val value = deserializeType(p.type, jo.get(p.name))
                    args[p] = value
                } catch (t: Throwable) {
                    throw RuntimeException(
                        "Issue deserializing property ${p.name} of ${p.type}. JSON: ${jo.get(p.name)}",
                        t,
                    )
                }
            }
            try {
                instance = primaryConstructor.callBy(args)
            } catch (e: InvocationTargetException) {
                throw RuntimeException(
                    "Issue instantiating ${clazz.canonicalName} with args ${
                        args.map {
                            "${it.key.name}:${it.key.type} = ${it.value}"
                        }.joinToString(", ")
                    } by using constructor $primaryConstructor",
                    e,
                )
            }
        } else {
            if (clazz.kotlin.objectInstance != null) {
                return clazz.kotlin.objectInstance!!
            }
            val emptyConstructor = clazz.constructors.find { it.parameters.isEmpty() }
            if (emptyConstructor != null) {
                instance = clazz.getConstructor().newInstance()
            } else {
                throw IllegalStateException("Class ${clazz.canonicalName} has no primary or default constructor")
            }
        }
        if (instance != null && jo.has(JSON_POSITION_KEY)) {
            val position = jo[JSON_POSITION_KEY]?.asJsonObject?.decodeAsPosition()
            instance.position = position
        }

        return instance ?: throw UnsupportedOperationException()
    }

    fun <T : Node> deserializeResult(
        rootClass: Class<T>,
        json: String,
    ): Result<T> {
        val jo = JsonParser().parse(json).asJsonObject
        val errors =
            jo["issues"].asJsonArray.map { it.asJsonObject }.map {
                val type = IssueType.valueOf(it["type"].asString)
                val message = it["message"].asString
                val position = it["position"]?.asJsonObject?.decodeAsPosition()
                val severity = IssueSeverity.valueOf(it["severity"].asString)
                Issue(type, message, severity = severity, position = position)
            }
        val root =
            if (jo.has("root")) {
                deserialize(rootClass, jo["root"].asJsonObject)
            } else {
                null
            }

        return Result(errors, root)
    }

    fun <T : Node> deserializeParsingResult(
        rootClass: Class<T>,
        json: String,
    ): ParsingResult<T> {
        val jo = JsonParser.parseString(json).asJsonObject
        val issues =
            jo["issues"].asJsonArray.map { it.asJsonObject }.map {
                val type = IssueType.valueOf(it["type"].asString)
                val message = it["message"].asString
                val position = it["position"]?.asJsonObject?.decodeAsPosition()
                Issue(type, message, position = position)
            }
        val root =
            if (jo.has("root")) {
                deserialize(rootClass, jo["root"].asJsonObject)
            } else {
                null
            }

        return ParsingResult(issues, root)
    }
}

fun JsonObject.decodeAsPosition(): Position {
    val start = this["start"]!!.asJsonObject.decodeAsPoint()
    val end = this["end"]!!.asJsonObject.decodeAsPoint()
    return Position(start, end)
}

private fun JsonObject.decodeAsPoint(): Point {
    val line = this["line"]!!.asInt
    val column = this["column"]!!.asInt
    return Point(line, column)
}
