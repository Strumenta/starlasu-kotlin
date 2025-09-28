package com.strumenta.kolasu.serialization

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.PropertyType
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.model.processOriginalProperties
import com.strumenta.kolasu.parsing.ParsingResult
import com.strumenta.kolasu.traversing.walk
import com.strumenta.kolasu.validation.Issue
import com.strumenta.kolasu.validation.Result
import java.io.File
import java.util.IdentityHashMap
import java.util.UUID
import java.util.function.Function
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

const val JSON_TYPE_KEY = "#type"
const val JSON_POSITION_KEY = "#position"
const val JSON_ORIGIN_KEY = "#origin"
const val JSON_ID_KEY = "#id"
const val JSON_DESTINATION_KEY = "#destination"

fun Iterable<*>.toJsonArray() = jsonArray(this.iterator())
fun jsonArray(values: Iterator<Any?>): JsonArray {
    val array = JsonArray()
    for (value in values)
        array.add(value.toJsonElement())
    return array
}

private fun Any?.toJsonElement(): JsonElement {
    if (this == null) {
        return JsonNull.INSTANCE
    }

    return when (this) {
        is JsonElement -> this
        is String -> toJson()
        is Number -> toJson()
        is Char -> toJson()
        is Boolean -> toJson()
        else -> throw IllegalArgumentException("$this cannot be converted to JSON")
    }
}

fun jsonObject(vararg values: Pair<String, *>): JsonObject {
    val jo = JsonObject()
    values.forEach {
        jo.add(it.first, it.second.toJsonElement())
    }
    return jo
}

/**
 * Converts an AST to JSON.
 * Note that ASTs may also be exported to the EMF-JSON format, which is different.
 */
class JsonGenerator {

    var shortClassNames = false

    private val customSerializers: MutableMap<KType, com.google.gson.JsonSerializer<*>> = HashMap()
    private val gsonBuilder = GsonBuilder()

    fun registerCustomSerializer(type: KType, serializer: com.google.gson.JsonSerializer<*>) {
        customSerializers[type] = serializer
        gsonBuilder.registerTypeAdapter(type.javaType, serializer)
    }

    /**
     * Converts an AST to JSON format.
     */
    fun generateJSON(
        root: Node,
        withIds: IdentityHashMap<Node, String>? = null,
        withOriginIds: IdentityHashMap<Node, String>? = null,
        withDestinationIds: IdentityHashMap<Node, String>? = null,
        shortClassNames: Boolean = false
    ): JsonElement {
        return nodeToJson(
            root,
            shortClassNames,
            withIds = withIds,
            withOriginIds = withOriginIds,
            withDestinationIds = withDestinationIds
        )
    }

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSON(result: Result<out Node>, withIds: IdentityHashMap<Node, String>? = null): JsonElement {
        return jsonObject(
            "issues" to result.issues.map { it.toJson() }.toJsonArray(),
            "root" to result.root?.let { nodeToJson(it, shortClassNames, withIds) }
        )
    }

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSON(result: ParsingResult<out Node>, withIds: IdentityHashMap<Node, String>? = null): JsonElement {
        return jsonObject(
            "issues" to result.issues.map { it.toJson() }.toJsonArray(),
            "root" to result.root?.let { nodeToJson(it, shortClassNames, withIds) }
        )
    }

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSONWithStreaming(result: Result<out Node>, writer: JsonWriter, shortClassNames: Boolean = false) {
        writer.beginObject()
        writer.name("issues")
        writer.beginArray()
        result.issues.forEach { it.toJsonStreaming(writer) }
        writer.endArray()
        if (result.root == null) {
            // do nothing for consistency with non-streaming JSON generation
        } else {
            writer.name("root")
            generateJSONWithStreaming(result.root, writer, shortClassNames)
        }
        writer.endObject()
    }

    fun generateJSONWithStreaming(root: Node, writer: JsonWriter, shortClassNames: Boolean = false) {
        val gson = gsonBuilder.create()
        gson.toJson(
            generateJSON(
                root = root,
                withIds = null,
                withOriginIds = null,
                withDestinationIds = null,
                shortClassNames = shortClassNames
            ),
            writer
        )
    }

    fun generateString(root: Node, withIds: IdentityHashMap<Node, String>? = null): String {
        val gson = gsonBuilder.setPrettyPrinting().create()
        return gson.toJson(generateJSON(root, withIds))
    }

    fun generateString(result: Result<out Node>, withIds: IdentityHashMap<Node, String>? = null): String {
        val gson = gsonBuilder.setPrettyPrinting().create()
        return gson.toJson(generateJSON(result, withIds))
    }

    fun generateString(result: ParsingResult<out Node>, withIds: IdentityHashMap<Node, String>? = null): String {
        val gson = gsonBuilder.setPrettyPrinting().create()
        return gson.toJson(generateJSON(result, withIds))
    }

    fun generateFile(root: Node, file: File, withIds: IdentityHashMap<Node, String>? = null) {
        File(file.toURI()).writeText(generateString(root, withIds))
    }

    fun generateFile(result: Result<out Node>, file: File, withIds: IdentityHashMap<Node, String>? = null) {
        File(file.toURI()).writeText(generateString(result, withIds))
    }

    fun generateFile(result: ParsingResult<out Node>, file: File, withIds: IdentityHashMap<Node, String>? = null) {
        File(file.toURI()).writeText(generateString(result, withIds))
    }

    private fun valueToJson(value: Any?, withIds: IdentityHashMap<Node, String>? = null): JsonElement {
        try {
            return when (value) {
                null -> JsonNull.INSTANCE
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is ReferenceByName<*> -> {
                    val jsonObject = JsonObject()
                    jsonObject.addProperty("name", value.name)
                    if (withIds != null) {
                        jsonObject.addProperty(
                            "referred",
                            if (value.resolved) withIds[value.referred as Node] ?: "<unknown>" else null
                        )
                    }
                    jsonObject
                }

                else -> {
                    return gsonBuilder.create().toJsonTree(value)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Unable to serialize $value (${value?.javaClass?.canonicalName})", e)
        }
    }

    private fun computeIds(root: Node): IdentityHashMap<Node, String> = IdentityHashMap<Node, String>().apply {
        root.walk().forEach { this[it] = UUID.randomUUID().toString() }
    }

    private fun computeIds(result: Result<out Node>): IdentityHashMap<Node, String> =
        if (result.root != null) computeIds(result.root) else IdentityHashMap()

    private fun computeIds(result: ParsingResult<out Node>): IdentityHashMap<Node, String> =
        if (result.root != null) computeIds(result.root) else IdentityHashMap()

    private fun nodeToJson(
        node: Node,
        shortClassNames: Boolean = false,
        withIds: IdentityHashMap<Node, String>? = null,
        withOriginIds: IdentityHashMap<Node, String>? = null,
        withDestinationIds: IdentityHashMap<Node, String>? = null
    ): JsonElement {
        val nodeType = node.nodeType
        val jsonObject = jsonObject(
            JSON_TYPE_KEY to if (shortClassNames) nodeType.substring(nodeType.lastIndexOf('.') + 1) else nodeType,
            JSON_POSITION_KEY to node.position?.toJson()
        )
        if (withIds != null) {
            val id = withIds[node]
            if (id != null) {
                jsonObject.addProperty(JSON_ID_KEY, id)
            }
        }
        if (withOriginIds != null) {
            if (node.origin is Node) {
                jsonObject.addProperty(JSON_ORIGIN_KEY, withOriginIds[node.origin as Node] ?: "<unknown>")
            }
        }
        if (withDestinationIds != null) {
            val destinationId = withDestinationIds[node]
            if (destinationId != null) {
                jsonObject.addProperty(JSON_DESTINATION_KEY, destinationId)
            }
        }
        node.processOriginalProperties {
            try {
                if (it.value == null) {
                    jsonObject.add(it.name, JsonNull.INSTANCE)
                } else if (it.multiple) {
                    if (it.provideNodes) {
                        jsonObject.add(
                            it.name,
                            (it.value as Collection<*>).map { el ->
                                nodeToJson(
                                    el as Node,
                                    shortClassNames,
                                    withIds = withIds,
                                    withOriginIds = withOriginIds,
                                    withDestinationIds = withDestinationIds
                                )
                            }
                                .toJsonArray()
                        )
                    } else {
                        jsonObject.add(it.name, valueToJson(it.value, withIds))
                    }
                } else {
                    if (it.provideNodes) {
                        jsonObject.add(
                            it.name,
                            nodeToJson(
                                it.value as Node,
                                shortClassNames,
                                withIds = withIds,
                                withOriginIds = withOriginIds,
                                withDestinationIds = withDestinationIds
                            )
                        )
                    } else {
                        jsonObject.add(it.name, valueToJson(it.value, withIds))
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Issue occurred while processing property $it of $node", e)
            }
        }
        return jsonObject
    }
}

private fun Node.toJsonStreaming(writer: JsonWriter, shortClassNames: Boolean = false) {
    writer.beginObject()
    writer.name(JSON_TYPE_KEY)
    writer.value(if (shortClassNames) this.javaClass.simpleName else this.javaClass.canonicalName)
    if (this.position != null) {
        writer.name(JSON_POSITION_KEY)
        this.position!!.toJsonStreaming(writer)
    }
    this.processOriginalProperties {
        writer.name(it.name)
        if (it.value == null) {
            writer.nullValue()
        } else if (it.multiple) {
            writer.beginArray()
            if (it.provideNodes) {
                (it.value as Collection<*>).forEach {
                    (it as Node).toJsonStreaming(writer, shortClassNames)
                }
            } else {
                (it.value as Collection<*>).forEach {
                    it.toJsonStreaming(writer)
                }
            }
            writer.endArray()
        } else {
            if (it.propertyType == PropertyType.CONTAINMENT) {
                (it.value as Node).toJsonStreaming(writer, shortClassNames)
            } else {
                it.value.toJsonStreaming(writer)
            }
        }
    }
    writer.endObject()
}

// DEPRECATED use GSon stuff instead
typealias JsonSerializer = Function<Any, JsonElement>

// DEPRECATED use GSon stuff instead
private val AsStringJsonSerializer = JsonSerializer { JsonPrimitive(it.toString()) }

private fun Any?.toJson(jsonSerializer: JsonSerializer = AsStringJsonSerializer): JsonElement {
    try {
        return when (this) {
            null -> JsonNull.INSTANCE
            is String -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            else -> jsonSerializer.apply(this)
        }
    } catch (e: Exception) {
        throw RuntimeException("Unable to serialize $this (${this?.javaClass?.canonicalName})", e)
    }
}

private fun Any?.toJsonStreaming(writer: JsonWriter) {
    when (this) {
        null -> writer.nullValue()
        is String -> writer.value(this)
        is Number -> writer.value(this)
        is Boolean -> writer.value(this)
        else -> writer.value(this.toString())
    }
}

fun Issue.toJson(): JsonElement {
    return jsonObject(
        "type" to this.type.name,
        "message" to this.message,
        "severity" to this.severity.name,
        "position" to this.position?.toJson()
    )
}

private fun Issue.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("type")
    writer.value(this.type.name)
    writer.name("message")
    writer.value(this.message)
    writer.name("severity")
    writer.value(this.severity.name)
    writer.name("position")
    if (this.position == null) {
        writer.nullValue()
    } else {
        this.position.toJsonStreaming(writer)
    }
    writer.endObject()
}

fun Position.toJson(): JsonElement {
    return jsonObject(
        "description" to this.toString(),
        "start" to this.start.toJson(),
        "end" to this.end.toJson()
    )
}

private fun Position.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("description")
    writer.value(this.toString())
    writer.name("start")
    start.toJsonStreaming(writer)
    writer.name("end")
    end.toJsonStreaming(writer)
    writer.endObject()
}

private fun Point.toJson(): JsonElement {
    return jsonObject(
        "line" to this.line,
        "column" to this.column
    )
}

private fun Point.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("line")
    writer.value(this.line)
    writer.name("column")
    writer.value(this.column)
    writer.endObject()
}
