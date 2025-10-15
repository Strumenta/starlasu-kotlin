package com.strumenta.starlasu.lionweb

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.assertEquals

fun assertJSONsAreEqual(
    expected: String,
    actual: String,
    context: String = "<ROOT>",
) {
    assertJSONsAreEqual(JsonParser.parseString(expected), JsonParser.parseString(actual), context)
}

fun assertJSONsAreEqual(
    expected: JsonElement,
    actual: JsonElement,
    context: String = "<ROOT>",
) {
    if (expected is JsonObject && actual is JsonObject) {
        val ejo = expected.asJsonObject
        val ajo = actual.asJsonObject
        assertEquals(ejo.keySet(), ajo.keySet())
        ejo.keySet().forEach { key ->
            assertJSONsAreEqual(ejo[key], ajo[key], "$context.$key")
        }
    } else if (expected is JsonArray && actual is JsonArray) {
        val eja = expected.asJsonArray
        val aja = actual.asJsonArray
        assertEquals(
            eja.size(),
            aja.size(),
            message = "size of $context (expected: ${eja.size()}, actual: ${aja.size()})",
        )
        (0 until eja.size()).forEach { i ->
            assertJSONsAreEqual(eja[i], aja[i], "$context[$i]")
        }
    } else {
        return assertEquals(expected, actual, "Difference in $context")
    }
}
