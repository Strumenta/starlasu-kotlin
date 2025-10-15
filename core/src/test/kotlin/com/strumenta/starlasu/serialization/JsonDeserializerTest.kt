package com.strumenta.starlasu.serialization

import com.google.gson.stream.JsonWriter
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueType
import com.strumenta.starlasu.validation.Result
import org.junit.Test
import java.io.StringWriter
import kotlin.test.assertEquals

class JsonDeserializerTest {
    @Test
    fun deserializeNodeFromJson() {
        val myRoot =
            MyRoot(
                mainSection =
                    Section(
                        "Section1",
                        listOf(
                            Content(1, null),
                            Content(2, Content(3, Content(4, null))),
                        ),
                    ),
                otherSections = listOf(),
            )
        val json = JsonGenerator().generateString(myRoot)
        val deserialized = JsonDeserializer().deserialize(MyRoot::class.java, json)
        assertEquals(myRoot, deserialized)
    }

    @Test
    fun deserializePositiveResultFromJson() {
        val myRoot =
            MyRoot(
                mainSection =
                    Section(
                        "Section1",
                        listOf(
                            Content(1, null),
                            Content(2, Content(3, Content(4, null))),
                        ),
                    ),
                otherSections = listOf(),
            )
        val originalResult = Result(emptyList(), myRoot)
        val json = JsonGenerator().generateString(originalResult)
        val deserialized = JsonDeserializer().deserializeResult(MyRoot::class.java, json)
        assertEquals(originalResult, deserialized)
    }

    @Test
    fun deserializeNegativeResultFromJson() {
        val originalResult: Result<MyRoot> =
            Result(
                listOf(
                    Issue(
                        IssueType.LEXICAL,
                        "foo",
                        position = Position(Point(1, 10), Point(4, 540)),
                    ),
                ),
                null,
            )
        val json = JsonGenerator().generateString(originalResult)
        val deserialized: Result<MyRoot> = JsonDeserializer().deserializeResult(MyRoot::class.java, json)
        assertEquals(originalResult, deserialized)
    }

    @Test
    fun deserializeNodeFromStreaming() {
        val myRoot =
            MyRoot(
                mainSection =
                    Section(
                        "Section1",
                        listOf(
                            Content(1, null),
                            Content(2, Content(3, Content(4, null))),
                        ),
                    ),
                otherSections = listOf(),
            )
        val writer = StringWriter()
        JsonGenerator().generateJSONWithStreaming(root = myRoot, writer = JsonWriter(writer))
        val json = writer.toString()
        val deserialized = JsonDeserializer().deserialize(MyRoot::class.java, json)
        assertEquals(myRoot, deserialized)
    }

    @Test
    fun deserializeNodeFromStreamingWithShortNames() {
        val myRoot =
            MyRoot(
                mainSection =
                    Section(
                        "Section1",
                        listOf(
                            Content(1, null),
                            Content(2, Content(3, Content(4, null))),
                        ),
                    ),
                otherSections = listOf(),
            )
        val writer = StringWriter()
        JsonGenerator().generateJSONWithStreaming(root = myRoot, writer = JsonWriter(writer), shortClassNames = true)
        val json = writer.toString()
        val deserialized = JsonDeserializer().deserialize(MyRoot::class.java, json)
        assertEquals(myRoot, deserialized)
    }

    @Test
    fun deserializeNegativeResultFromJsonStreaming() {
        val originalResult: Result<MyRoot> =
            Result(
                listOf(
                    Issue(
                        IssueType.LEXICAL,
                        "foo",
                        position = Position(Point(1, 10), Point(4, 540)),
                    ),
                ),
                null,
            )
        val writer = StringWriter()
        JsonGenerator().generateJSONWithStreaming(
            result = originalResult,
            writer = JsonWriter(writer),
            shortClassNames = false,
        )
        val json = writer.toString()
        val deserialized: Result<MyRoot> = JsonDeserializer().deserializeResult(MyRoot::class.java, json)
        assertEquals(originalResult, deserialized)
    }

    @Test
    fun deserializeNegativeResultFromJsonStreamingWithShortNames() {
        val originalResult: Result<MyRoot> =
            Result(
                listOf(
                    Issue(
                        IssueType.LEXICAL,
                        "foo",
                        position = Position(Point(1, 10), Point(4, 540)),
                    ),
                ),
                null,
            )
        val writer = StringWriter()
        JsonGenerator().generateJSONWithStreaming(
            result = originalResult,
            writer = JsonWriter(writer),
            shortClassNames = true,
        )
        val json = writer.toString()
        val deserialized: Result<MyRoot> = JsonDeserializer().deserializeResult(MyRoot::class.java, json)
        assertEquals(originalResult, deserialized)
    }
}
