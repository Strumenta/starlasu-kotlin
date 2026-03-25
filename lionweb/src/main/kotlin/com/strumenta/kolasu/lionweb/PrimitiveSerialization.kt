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

class TokensList(val tokens: List<KolasuToken>)

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
        Point(line, column)
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
    val tokens = if (serialized.isEmpty()) {
        mutableListOf()
    } else {
        serialized.split(";").map {
            val parts = it.split("$")
            require(parts.size == 2)
            val category = parts[0]
            val position = positionDeserializer.deserialize(parts[1])
            KolasuToken(TokenCategory(category), position!!)
        }.toMutableList()
    }
    TokensList(tokens)
}
