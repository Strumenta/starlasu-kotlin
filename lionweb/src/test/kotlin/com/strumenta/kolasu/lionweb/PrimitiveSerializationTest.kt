package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PrimitiveSerializationTest {

    // -------------------------------------------------------------------------
    // pointSerializer
    // -------------------------------------------------------------------------

    @Test
    fun pointSerializerProducesExpectedFormat() {
        assertEquals("L1:0", pointSerializer.serialize(Point(1, 0)))
        assertEquals("L42:100", pointSerializer.serialize(Point(42, 100)))
        assertEquals("L1:1", pointSerializer.serialize(Point(1, 1)))
    }

    @Test
    fun pointSerializerReturnsNullForNull() {
        assertNull(pointSerializer.serialize(null))
    }

    // -------------------------------------------------------------------------
    // pointDeserializer — valid inputs
    // -------------------------------------------------------------------------

    @Test
    fun pointDeserializerHandlesStartPoint() {
        assertEquals(Point(1, 0), pointDeserializer.deserialize("L1:0"))
    }

    @Test
    fun pointDeserializerHandlesMultiDigit() {
        assertEquals(Point(42, 100), pointDeserializer.deserialize("L42:100"))
    }

    @Test
    fun pointDeserializerHandlesSingleDigits() {
        assertEquals(Point(3, 5), pointDeserializer.deserialize("L3:5"))
    }

    @Test
    fun pointDeserializerHandlesLargeValues() {
        assertEquals(Point(9999, 9999), pointDeserializer.deserialize("L9999:9999"))
    }

    @Test
    fun pointDeserializerReturnsNullForNull() {
        assertNull(pointDeserializer.deserialize(null))
    }

    // -------------------------------------------------------------------------
    // pointDeserializer — invalid inputs
    // -------------------------------------------------------------------------

    @Test
    fun pointDeserializerRejectsEmptyString() {
        assertFailsWith<IllegalArgumentException> { pointDeserializer.deserialize("") }
    }

    @Test
    fun pointDeserializerRejectsMissingL() {
        assertFailsWith<IllegalArgumentException> { pointDeserializer.deserialize("1:0") }
    }

    @Test
    fun pointDeserializerRejectsMissingColon() {
        assertFailsWith<IllegalArgumentException> { pointDeserializer.deserialize("L10") }
    }

    @Test
    fun pointDeserializerRejectsOnlyL() {
        assertFailsWith<IllegalArgumentException> { pointDeserializer.deserialize("L") }
    }

    // -------------------------------------------------------------------------
    // pointSerializer + pointDeserializer — roundtrip
    // -------------------------------------------------------------------------

    @Test
    fun pointRoundtrip() {
        listOf(Point(1, 0), Point(1, 1), Point(10, 20), Point(999, 0)).forEach { point ->
            assertEquals(point, pointDeserializer.deserialize(pointSerializer.serialize(point)))
        }
    }

    // -------------------------------------------------------------------------
    // positionSerializer
    // -------------------------------------------------------------------------

    @Test
    fun positionSerializerProducesExpectedFormat() {
        assertEquals("L3:5-L27:200", positionSerializer.serialize(Position(Point(3, 5), Point(27, 200))))
    }

    @Test
    fun positionSerializerReturnsNullForNull() {
        assertNull(positionSerializer.serialize(null))
    }

    // -------------------------------------------------------------------------
    // positionDeserializer — valid inputs
    // -------------------------------------------------------------------------

    @Test
    fun positionDeserializerHandlesTypicalInput() {
        assertEquals(
            Position(Point(3, 5), Point(27, 200)),
            positionDeserializer.deserialize("L3:5-L27:200")
        )
    }

    @Test
    fun positionDeserializerHandlesStartPosition() {
        assertEquals(
            Position(Point(1, 0), Point(1, 0)),
            positionDeserializer.deserialize("L1:0-L1:0")
        )
    }

    @Test
    fun positionDeserializerHandlesMultiDigits() {
        assertEquals(
            Position(Point(10, 20), Point(30, 40)),
            positionDeserializer.deserialize("L10:20-L30:40")
        )
    }

    @Test
    fun positionDeserializerReturnsNullForNull() {
        assertNull(positionDeserializer.deserialize(null))
    }

    // -------------------------------------------------------------------------
    // positionDeserializer — invalid inputs
    // -------------------------------------------------------------------------

    @Test
    fun positionDeserializerRejectsMissingSeparator() {
        // No "-L" separator
        assertFailsWith<IllegalArgumentException> { positionDeserializer.deserialize("L1:0L2:0") }
    }

    @Test
    fun positionDeserializerRejectsEmptyString() {
        assertFailsWith<IllegalArgumentException> { positionDeserializer.deserialize("") }
    }

    // -------------------------------------------------------------------------
    // positionSerializer + positionDeserializer — roundtrip
    // -------------------------------------------------------------------------

    @Test
    fun positionRoundtrip() {
        listOf(
            Position(Point(1, 0), Point(1, 0)),
            Position(Point(1, 0), Point(50, 120)),
            Position(Point(3, 5), Point(27, 200))
        ).forEach { pos ->
            assertEquals(pos, positionDeserializer.deserialize(positionSerializer.serialize(pos)))
        }
    }
}
