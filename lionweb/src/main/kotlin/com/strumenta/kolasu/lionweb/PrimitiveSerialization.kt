package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.parsing.KolasuToken
import com.strumenta.kolasu.parsing.TokenCategory
import io.lionweb.kotlin.DefaultMetamodelRegistry
import io.lionweb.kotlin.MetamodelRegistry
import io.lionweb.serialization.DataTypesValuesSerialization.DataTypeDeserializer
import io.lionweb.serialization.DataTypesValuesSerialization.DataTypeSerializer
import com.strumenta.starlasu.base.v1.ASTLanguageV1 as ASTLanguage

fun registerSerializersAndDeserializersInMetamodelRegistry(
    metamodelRegistry: MetamodelRegistry = DefaultMetamodelRegistry
) {
    metamodelRegistry.addSerializerAndDeserializer(ASTLanguage.getChar(), charSerializer, charDeserializer)
    metamodelRegistry.addSerializerAndDeserializer(
        ASTLanguage.getPosition(),
        positionSerializer,
        positionDeserializer
    )
    metamodelRegistry.addSerializerAndDeserializer(
        ASTLanguage.getTokensList(),
        tokensListDataTypeSerializer,
        tokensListPrimitiveDeserializer
    )
}

/**
 * Wraps a list of [KolasuToken].
 *
 * When constructed via [TokensList.fromRaw] the token list is parsed **lazily** — the raw
 * serialized string is kept as-is and the actual [KolasuToken] objects are only created on the
 * first access to [tokens].  Pipeline stages that never read the token list therefore pay zero
 * deserialization cost.
 *
 * When constructed with a pre-built [List] (e.g. during export) the list is returned directly
 * without any extra parsing.
 */
class TokensList private constructor(
    private val eagerList: List<KolasuToken>?,
    private val raw: String?
) {
    /** Construct an eager [TokensList] from an already-parsed list (used during export). */
    constructor(tokens: List<KolasuToken>) : this(tokens, null)

    val tokens: List<KolasuToken> by lazy {
        eagerList ?: parseRaw(raw!!)
    }

    companion object {
        /** Construct a [TokensList] that parses [raw] only on first [tokens] access. */
        internal fun fromRaw(raw: String): TokensList = TokensList(null, raw)

        private fun parseRaw(raw: String): List<KolasuToken> =
            if (raw.isEmpty()) {
                emptyList()
            } else {
                raw.split(";").map {
                    val dollarIdx = it.indexOf('$')
                    require(dollarIdx > 0) { "Invalid token entry: $it" }
                    KolasuToken(
                        TokenCategory(it.substring(0, dollarIdx)),
                        positionDeserializer.deserialize(it.substring(dollarIdx + 1))!!
                    )
                }
            }
    }
}

//
// Char
//

val charSerializer = DataTypeSerializer<Char> { value -> "$value" }
val charDeserializer = DataTypeDeserializer<Char> { serialized ->
    if (serialized == null) {
        return@DataTypeDeserializer null
    }
    require(serialized.length == 1)
    serialized[0]
}

//
// Point
//

/**
 * LRU cache that interns [Point] instances during deserialization.
 *
 * A source file has a bounded set of distinct (line, column) pairs — typically far fewer than
 * the number of AST nodes — so interning them eliminates the majority of [Point] allocations.
 * The cache key encodes the pair as a single [Long] to avoid boxing.
 * Capacity is capped at 1 024 entries; older entries are evicted automatically.
 */
private val pointCache: MutableMap<Long, Point> =
    object : LinkedHashMap<Long, Point>(1024 * 10 / 7, 0.7f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Long, Point>): Boolean = size > 1024
    }

private fun internPoint(line: Int, column: Int): Point {
    val key = (line.toLong() shl 32) or (column.toLong() and 0xFFFFFFFFL)
    return pointCache.getOrPut(key) { Point(line, column) }
}

val pointSerializer: DataTypeSerializer<Point> =
    DataTypeSerializer<Point> { value ->
        if (value == null) {
            return@DataTypeSerializer null
        }
        "L${value.line}:${value.column}"
    }

val pointDeserializer: DataTypeDeserializer<Point> =
    DataTypeDeserializer<Point> { serialized ->
        if (serialized == null) {
            return@DataTypeDeserializer null
        }
        require(serialized.length > 1 && serialized[0] == 'L') {
            "Point string must start with 'L', got: $serialized"
        }
        val colonIdx = serialized.indexOf(':', 1)
        require(colonIdx > 1) { "Point string missing ':', got: $serialized" }
        val line = serialized.substring(1, colonIdx).toInt()
        val column = serialized.substring(colonIdx + 1).toInt()
        internPoint(line, column)
    }

//
// Position
//

val positionSerializer = DataTypeSerializer<Position> { value ->
    if (value == null) {
        return@DataTypeSerializer null
    }
    "${pointSerializer.serialize((value as Position).start)}-${pointSerializer.serialize(value.end)}"
}

val positionDeserializer = DataTypeDeserializer<Position> { serialized ->
    if (serialized == null) {
        return@DataTypeDeserializer null
    }
    // Format: L{line}:{col}-L{line}:{col}. Since line≥1 and col≥0, no negative signs appear,
    // so "-L" is the unique separator between start and end points.
    val sepIdx = serialized.indexOf("-L")
    require(sepIdx > 0) {
        "Position has an unexpected format: $serialized"
    }
    // Parse start/end points from substrings without allocating intermediate List
    val startStr = serialized.substring(0, sepIdx)
    val endStr = serialized.substring(sepIdx + 1)
    Position(pointDeserializer.deserialize(startStr)!!, pointDeserializer.deserialize(endStr)!!)
}

//
// Tokens List
//

val tokensListDataTypeSerializer = DataTypeSerializer<TokensList?> { value: TokensList? ->
    value?.tokens?.joinToString(";") { kt ->
        kt.category.type + "$" + positionSerializer.serialize(kt.position)
    }
}

val tokensListPrimitiveDeserializer = DataTypeDeserializer<TokensList?> { serialized ->
    if (serialized == null) {
        return@DataTypeDeserializer null
    }
    TokensList.fromRaw(serialized)
}
