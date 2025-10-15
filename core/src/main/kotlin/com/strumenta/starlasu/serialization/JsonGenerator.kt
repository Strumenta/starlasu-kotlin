package com.strumenta.starlasu.serialization

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter
import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.processOriginalProperties
import com.strumenta.starlasu.parsing.ParsingResult
import com.strumenta.starlasu.traversing.walk
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.Result
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
    for (value in values) {
        array.add(value.toJsonElement())
    }
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

    fun registerCustomSerializer(
        type: KType,
        serializer: com.google.gson.JsonSerializer<*>,
    ) {
        customSerializers[type] = serializer
        gsonBuilder.registerTypeAdapter(type.javaType, serializer)
    }

    /**
     * Converts an AST to JSON format.
     */
    fun generateJSON(
        root: ASTNode,
        withIds: IdentityHashMap<ASTNode, String>? = null,
        withOriginIds: IdentityHashMap<ASTNode, String>? = null,
        withDestinationIds: IdentityHashMap<ASTNode, String>? = null,
        shortClassNames: Boolean = false,
    ): JsonElement =
        nodeToJson(
            root,
            shortClassNames,
            withIds = withIds,
            withOriginIds = withOriginIds,
            withDestinationIds = withDestinationIds,
        )

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSON(
        result: Result<out ASTNode>,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ): JsonElement =
        jsonObject(
            "issues" to result.issues.map { it.toJson() }.toJsonArray(),
            "root" to result.root?.let { nodeToJson(it, shortClassNames, withIds) },
        )

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSON(
        result: ParsingResult<out ASTNode>,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ): JsonElement =
        jsonObject(
            "issues" to result.issues.map { it.toJson() }.toJsonArray(),
            "root" to result.root?.let { nodeToJson(it, shortClassNames, withIds) },
        )

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSONWithStreaming(
        result: Result<out ASTNode>,
        writer: JsonWriter,
        shortClassNames: Boolean = false,
    ) {
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

    fun generateJSONWithStreaming(
        root: ASTNode,
        writer: JsonWriter,
        shortClassNames: Boolean = false,
    ) {
        val gson = gsonBuilder.create()
        gson.toJson(
            generateJSON(
                root = root,
                withIds = null,
                withOriginIds = null,
                withDestinationIds = null,
                shortClassNames = shortClassNames,
            ),
            writer,
        )
    }

    fun generateString(
        root: ASTNode,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ): String {
        val gson = gsonBuilder.setPrettyPrinting().create()
        return gson.toJson(generateJSON(root, withIds))
    }

    fun generateString(
        result: Result<out ASTNode>,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ): String {
        val gson = gsonBuilder.setPrettyPrinting().create()
        return gson.toJson(generateJSON(result, withIds))
    }

    fun generateString(
        result: ParsingResult<out ASTNode>,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ): String {
        val gson = gsonBuilder.setPrettyPrinting().create()
        return gson.toJson(generateJSON(result, withIds))
    }

    fun generateFile(
        root: ASTNode,
        file: File,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ) {
        File(file.toURI()).writeText(generateString(root, withIds))
    }

    fun generateFile(
        result: Result<out ASTNode>,
        file: File,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ) {
        File(file.toURI()).writeText(generateString(result, withIds))
    }

    fun generateFile(
        result: ParsingResult<out ASTNode>,
        file: File,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ) {
        File(file.toURI()).writeText(generateString(result, withIds))
    }

    private fun valueToJson(
        value: Any?,
        withIds: IdentityHashMap<ASTNode, String>? = null,
    ): JsonElement {
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
                            if (value.resolved) withIds[value.referred as ASTNode] ?: "<unknown>" else null,
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

    private fun computeIds(root: ASTNode): IdentityHashMap<ASTNode, String> =
        IdentityHashMap<ASTNode, String>().apply {
            root.walk().forEach { this[it] = UUID.randomUUID().toString() }
        }

    private fun computeIds(result: Result<out ASTNode>): IdentityHashMap<ASTNode, String> =
        if (result.root != null) computeIds(result.root) else IdentityHashMap()

    private fun computeIds(result: ParsingResult<out ASTNode>): IdentityHashMap<ASTNode, String> =
        if (result.root != null) computeIds(result.root) else IdentityHashMap()

    private fun nodeToJson(
        node: ASTNode,
        shortClassNames: Boolean = false,
        withIds: IdentityHashMap<ASTNode, String>? = null,
        withOriginIds: IdentityHashMap<ASTNode, String>? = null,
        withDestinationIds: IdentityHashMap<ASTNode, String>? = null,
    ): JsonElement {
        val nodeType = node.nodeType
        val jsonObject =
            jsonObject(
                JSON_TYPE_KEY to if (shortClassNames) nodeType.substring(nodeType.lastIndexOf('.') + 1) else nodeType,
                JSON_POSITION_KEY to node.position?.toJson(),
            )
        if (withIds != null) {
            val id = withIds[node]
            if (id != null) {
                jsonObject.addProperty(JSON_ID_KEY, id)
            }
        }
        if (withOriginIds != null) {
            if (node.origin is ASTNode) {
                jsonObject.addProperty(JSON_ORIGIN_KEY, withOriginIds[node.origin as ASTNode] ?: "<unknown>")
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
                    if (it.providesNodes) {
                        jsonObject.add(
                            it.name,
                            (it.value as Collection<*>)
                                .map { el ->
                                    nodeToJson(
                                        el as ASTNode,
                                        shortClassNames,
                                        withIds = withIds,
                                        withOriginIds = withOriginIds,
                                        withDestinationIds = withDestinationIds,
                                    )
                                }.toJsonArray(),
                        )
                    } else {
                        jsonObject.add(it.name, valueToJson(it.value, withIds))
                    }
                } else {
                    if (it.providesNodes) {
                        jsonObject.add(
                            it.name,
                            nodeToJson(
                                it.value as ASTNode,
                                shortClassNames,
                                withIds = withIds,
                                withOriginIds = withOriginIds,
                                withDestinationIds = withDestinationIds,
                            ),
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

private fun ASTNode.toJsonStreaming(
    writer: JsonWriter,
    shortClassNames: Boolean = false,
) {
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
            if (it.providesNodes) {
                (it.value as Collection<*>).forEach {
                    (it as ASTNode).toJsonStreaming(writer, shortClassNames)
                }
            } else {
                (it.value as Collection<*>).forEach {
                    it.toJsonStreaming(writer)
                }
            }
            writer.endArray()
        } else {
            if (it.providesNodes) {
                (it.value as ASTNode).toJsonStreaming(writer, shortClassNames)
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

fun Issue.toJson(): JsonElement =
    jsonObject(
        "type" to this.type.name,
        "message" to this.message,
        "severity" to this.severity.name,
        "position" to this.position?.toJson(),
    )

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

fun Position.toJson(): JsonElement =
    jsonObject(
        "description" to this.toString(),
        "start" to this.start.toJson(),
        "end" to this.end.toJson(),
    )

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

private fun Point.toJson(): JsonElement =
    jsonObject(
        "line" to this.line,
        "column" to this.column,
    )

private fun Point.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("line")
    writer.value(this.line)
    writer.name("column")
    writer.value(this.column)
    writer.endObject()
}
