package com.strumenta.kolasu.emf.rpgast

import com.smeup.rpgparser.parsing.ast.Expression
import com.strumenta.kolasu.model.Derived
import com.strumenta.kolasu.model.Link
import com.strumenta.kolasu.model.Named
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Position
import java.math.BigDecimal

abstract class AbstractDataDefinition(
    override val name: String,
    open val type: Type,
    specifiedPosition: Position? = null,
    private val hashCode: Int = name.hashCode()
) : Node(specifiedPosition), Named {
    fun numberOfElements() = type.numberOfElements()
    open fun elementSize() = type.elementSize()

    open fun isArray(): Boolean {
        return type is ArrayType
    }

    override fun hashCode() = hashCode

    override fun equals(other: Any?) = if (other is AbstractDataDefinition) name == other.name else false
}

data class FileDefinition private constructor(override val name: String, val specifiedPosition: Position?) :
    Node(
        specifiedPosition
    ),
    Named {
    companion object {
        operator fun invoke(name: String, specifiedPosition: Position? = null): FileDefinition {
            return FileDefinition(name.uppercase(), specifiedPosition)
        }
    }

    var internalFormatName: String? = null
        set(value) {
            field = value?.uppercase()
        }
}

data class DataDefinition(
    override val name: String,
    override val type: Type,
    var fields: List<FieldDefinition> = emptyList(),
    val initializationValue: Expression? = null,
    val inz: Boolean = false,
    val specifiedPosition: Position? = null
) :
    AbstractDataDefinition(name, type, specifiedPosition) {

    override fun isArray() = type is ArrayType
    fun isCompileTimeArray() = type is ArrayType && type.compileTimeArray()

    @Deprecated("The start offset should be calculated before defining the FieldDefinition")
    fun startOffset(fieldDefinition: FieldDefinition): Int {
        var start = 0
        for (f in fields) {
            if (f == fieldDefinition) {
                require(start >= 0)
                return start
            }
            start += f.elementSize()
        }
        throw IllegalArgumentException("Unknown field $fieldDefinition")
    }

    @Deprecated("The end offset should be calculated before defining the FieldDefinition")
    fun endOffset(fieldDefinition: FieldDefinition): Int {
        return (startOffset(fieldDefinition) + fieldDefinition.elementSize())
    }

    fun getFieldByName(fieldName: String): FieldDefinition {
        return this.fields.find { it.name == fieldName } ?: throw java.lang.IllegalArgumentException(
            "Field not found $fieldName"
        )
    }
}

data class FieldDefinition(
    override val name: String,
    override val type: Type,
    val explicitStartOffset: Int? = null,
    val explicitEndOffset: Int? = null,
    val calculatedStartOffset: Int? = null,
    val calculatedEndOffset: Int? = null,
    // In case of using LIKEDS we reuse a FieldDefinition, but specifying a different
    // container. We basically duplicate it
    @property:Link
    var overriddenContainer: DataDefinition? = null,
    val initializationValue: Expression? = null,
    val descend: Boolean = false,
    val specifiedPosition: Position? = null,

    // true when the FieldDefinition contains a DIM keyword on its line
    val declaredArrayInLineOnThisField: Int? = null
) :
    AbstractDataDefinition(name, type, specifiedPosition) {

    init {
        require((explicitStartOffset != null) != (calculatedStartOffset != null)) {
            "Field $name should have either an explicit start offset ($explicitStartOffset) " +
                "or a calculated one ($calculatedStartOffset)"
        }
        require((explicitEndOffset != null) != (calculatedEndOffset != null)) {
            "Field $name should have either an explicit end offset ($explicitEndOffset) " +
                "or a calculated one ($calculatedEndOffset)"
        }
    }

    // true when the FieldDefinition contains a DIM keyword on its line
    // or when the field is overlaying on an a field which has the DIM keyword
    val declaredArrayInLine: Int?
        get() = declaredArrayInLineOnThisField ?: (overlayingOn as? FieldDefinition)?.declaredArrayInLine

    val size: Int = type.size

    @property:Link
    var overlayingOn: AbstractDataDefinition? = null
    internal var overlayTarget: String? = null

    // when they are arrays, how many bytes should we skip into the DS to find the next element?
    // normally it would be the same size as an element of the DS, however if they are declared
    // as on overlay of a field with a DIM keyword, then we should use the size of an element
    // of such field
    val stepSize: Int
        get() {
            return if (declaredArrayInLineOnThisField != null) {
                elementSize()
            } else {
                (overlayingOn as? FieldDefinition)?.stepSize ?: elementSize()
            }
        }

    override fun elementSize(): Int {
        return if (container.type is ArrayType) {
            super.elementSize()
        } else if (this.declaredArrayInLine != null) {
            super.elementSize()
        } else {
            size
        }
    }

    fun toDataStructureValue(value: Value) = type.toDataStructureValue(value)

    /**
     * The fields used through LIKEDS cannot be used unqualified
     */
    fun canBeUsedUnqualified() = this.overriddenContainer == null

    @Derived
    val container
        get() = overriddenContainer
            ?: this.parent as? DataDefinition
            ?: throw IllegalStateException(
                "Parent of field ${this.name} was expected to be a DataDefinition, instead it is ${this.parent} " +
                    "(${this.parent?.javaClass})"
            )

    /**
     * The start offset is zero based, while in RPG code you could find explicit one-based offsets.
     *
     * For example:
     * 0002.00 DCURTIMSTP        DS
     * 0003.00 DCURTIMDATE               1      8S 0
     *
     * In this case CURTIMDATE will have startOffset 0.
     */
    val startOffset: Int
        get() {
            if (explicitStartOffset != null) {
                return explicitStartOffset
            }
            if (calculatedStartOffset != null) {
                return calculatedStartOffset
            }
            return container.startOffset(this)
        }

    /**
     * The end offset is non-inclusive if considered zero based, or inclusive if considered one based.
     *
     * For example:
     * 0002.00 DCURTIMSTP        DS
     * 0003.00 DCURTIMDATE               1      8S 0
     *
     * In this case CURTIMDATE will have endOffset 8.
     *
     * In the case of an array endOffset indicates the end of the first element.
     */
    val endOffset: Int
        get() {
            if (explicitEndOffset != null) {
                return explicitEndOffset
            }
            if (calculatedEndOffset != null) {
                return calculatedEndOffset
            }
            return container.endOffset(this)
        }

    @Derived
    val offsets: Pair<Int, Int>
        get() = Pair(startOffset, endOffset)

    override fun hashCode(): Int {
        return name.hashCode() * 31 + type.hashCode() * 7
    }
}

// Positions 64 through 68 specify the length of the result field. This entry is optional, but can be used to define a
// numeric or character field not defined elsewhere in the program. These definitions of the field entries are allowed
// if the result field contains a field name. Other data types must be defined on the definition specification or on the
// calculation specification using the *LIKE DEFINE operation.

class InStatementDataDefinition(
    override val name: String,
    override val type: Type,
    val specifiedPosition: Position? = null,
    val initializationValue: Expression? = null
) : AbstractDataDefinition(name, type, specifiedPosition)

/**
 * Encoding/Decoding a binary value for a data structure
 */

fun encodeBinary(inValue: BigDecimal, size: Int): String {
    val buffer = ByteArray(size)
    val lsb = inValue.toInt()

    if (size == 1) {
        buffer[0] = (lsb and 0x0000FFFF).toByte()

        return buffer[0].toInt().toChar().toString()
    }

    if (size == 2) {
        buffer[0] = ((lsb shr 8) and 0x000000FF).toByte()
        buffer[1] = (lsb and 0x000000FF).toByte()

        return buffer[1].toInt().toChar().toString() + buffer[0].toInt().toChar().toString()
    }
    if (size == 4) {
        buffer[0] = ((lsb shr 24) and 0x0000FFFF).toByte()
        buffer[1] = ((lsb shr 16) and 0x0000FFFF).toByte()
        buffer[2] = ((lsb shr 8) and 0x0000FFFF).toByte()
        buffer[3] = (lsb and 0x0000FFFF).toByte()

        return buffer[3].toInt().toChar().toString() + buffer[2].toInt().toChar().toString() +
            buffer[1].toInt().toChar().toString() + buffer[0].toInt().toChar().toString()
    }
    if (size == 8) {
        val llsb = inValue.toLong()
        buffer[0] = ((llsb shr 56) and 0x0000FFFF).toByte()
        buffer[1] = ((llsb shr 48) and 0x0000FFFF).toByte()
        buffer[2] = ((llsb shr 40) and 0x0000FFFF).toByte()
        buffer[3] = ((llsb shr 32) and 0x0000FFFF).toByte()
        buffer[4] = ((llsb shr 24) and 0x0000FFFF).toByte()
        buffer[5] = ((llsb shr 16) and 0x0000FFFF).toByte()
        buffer[6] = ((llsb shr 8) and 0x0000FFFF).toByte()
        buffer[7] = (llsb and 0x0000FFFF).toByte()

        return buffer[7].toInt().toChar().toString() + buffer[6].toInt().toChar().toString() +
            buffer[5].toInt().toChar().toString() + buffer[4].toInt().toChar().toString() +
            buffer[3].toInt().toChar().toString() + buffer[2].toInt().toChar().toString() +
            buffer[1].toInt().toChar().toString() + buffer[0].toInt().toChar().toString()
    }
    TODO("encode binary for $size not implemented")
}

fun encodeInteger(inValue: BigDecimal, size: Int): String {
    return encodeBinary(inValue, size)
}

fun encodeUnsigned(inValue: BigDecimal, size: Int): String {
    return encodeBinary(inValue, size)
}

fun decodeBinary(value: String, size: Int): BigDecimal {
    if (size == 1) {
        var number: Long = 0x0000000
        if (value[0].toInt() and 0x0010 != 0) {
            number = 0x00000000
        }
        number += (value[0].toInt() and 0x00FF)
        return BigDecimal(number.toInt().toString())
    }

    if (size == 2) {
        var number: Long = 0x0000000
        if (value[1].toInt() and 0x8000 != 0) {
            number = 0xFFFF0000
        }
        number += (value[0].toInt() and 0x00FF) + ((value[1].toInt() and 0x00FF) shl 8)
        return BigDecimal(number.toInt().toString())
    }

    if (size == 4) {
        val number = (value[0].code and 0x00FF) +
            ((value[1].code and 0x00FF) shl 8) +
            ((value[2].code and 0x00FF) shl 16) +
            ((value[3].code and 0x00FF) shl 24)

        return BigDecimal(number.toInt().toString())
    }
    if (size == 8) {
        val number = (value[0].code and 0x00FF) +
            ((value[1].code and 0x00FF) shl 8) +
            ((value[2].code and 0x00FF) shl 16) +
            ((value[3].code and 0x00FF) shl 24) +
            ((value[4].code and 0x00FF) shl 32) +
            ((value[5].code and 0x00FF) shl 40) +
            ((value[6].code and 0x00FF) shl 48) +
            ((value[7].code and 0x00FF) shl 56)

        return BigDecimal(number.toInt().toString())
    }
    TODO("decode binary for $size not implemented")
}

fun decodeInteger(value: String, size: Int): BigDecimal {
    if (size == 1) {
        var number: Int = 0x0000000
        number += (value[0].toByte())
        return BigDecimal(number.toString())
    }

    if (size == 2) {
        var number: Long = 0x0000000
        if (value[1].toInt() and 0x8000 != 0) {
            number = 0xFFFF0000
        }
        number += (value[0].toInt() and 0x00FF) + ((value[1].toInt() and 0x00FF) shl 8)
        return BigDecimal(number.toInt().toString())
    }
    if (size == 4) {
        val number = (value[0].code and 0x00FF) +
            ((value[1].code and 0x00FF) shl 8) +
            ((value[2].code and 0x00FF) shl 16) +
            ((value[3].code and 0x00FF) shl 24)

        return BigDecimal(number.toInt().toString())
    }
    if (size == 8) {
        val number = (value[0].code and 0x00FF) +
            ((value[1].code and 0x00FF) shl 8) +
            ((value[2].code and 0x00FF) shl 16) +
            ((value[3].code and 0x00FF) shl 24) +
            ((value[4].code and 0x00FF) shl 32) +
            ((value[5].code and 0x00FF) shl 40) +
            ((value[6].code and 0x00FF) shl 48) +
            ((value[7].code and 0x00FF) shl 56)

        return BigDecimal(number.toString())
    }
    TODO("decode binary for $size not implemented")
}

fun decodeUnsigned(value: String, size: Int): BigDecimal {
    if (size == 1) {
        var number: Long = 0x0000000
        if (value[0].toInt() and 0x0010 != 0) {
            number = 0x00000000
        }
        number += (value[0].toInt() and 0x00FF)
        return BigDecimal(number.toInt().toString())
    }

    if (size == 2) {
        var number: Long = 0x0000000
        if (value[1].toInt() and 0x1000 != 0) {
            number = 0xFFFF0000
        }
        number += (value[0].toInt() and 0x00FF) + ((value[1].toInt() and 0x00FF) shl 8)
        // make sure you count onlu 16 bits
        number = number and 0x0000FFFF
        return BigDecimal(number.toString())
    }
    if (size == 4) {
        val number = (value[0].code and 0x00FF) +
            ((value[1].code and 0x00FF) shl 8) +
            ((value[2].code and 0x00FF) shl 16) +
            ((value[3].code and 0x00FF) shl 24)

        return BigDecimal(number.toString())
    }
    if (size == 8) {
        val number = (value[0].code and 0x00FF) +
            ((value[1].code and 0x00FF) shl 8) +
            ((value[2].code and 0x00FF) shl 16) +
            ((value[3].code and 0x00FF) shl 24) +
            ((value[4].code and 0x00FF) shl 32) +
            ((value[5].code and 0x00FF) shl 40) +
            ((value[6].code and 0x00FF) shl 48) +
            ((value[7].code and 0x00FF) shl 56)

        return BigDecimal(number.toInt().toString())
    }
    TODO("decode binary for $size not implemented")
}

/**
 * Encode a zoned value for a data structure
 */
fun encodeToZoned(inValue: BigDecimal, digits: Int, scale: Int): String {
    // get just the digits from BigDecimal, "normalize" away sign, decimal place etc.
    val inChars = inValue.abs().movePointRight(scale).toBigInteger().toString().toCharArray()
    var buffer = IntArray(inChars.size)

    // read the sign
    val sign = inValue.signum()

    inChars.forEachIndexed { index, char ->
        val digit = char.toInt()
        buffer[index] = digit
    }
    if (sign < 0) {
        buffer[inChars.size - 1] = (buffer[inChars.size - 1] - 0x030) + 0x0049
    }

    var s = ""
    buffer.forEach { byte ->
        s += byte.toChar()
    }

    s = s.padStart(digits, '0')
    return s
}

fun decodeFromZoned(value: String, digits: Int, scale: Int): BigDecimal {
    val builder = StringBuilder()

    value.forEach {
        when {
            it.isDigit() -> builder.append(it)
            else -> {
                if (it.code == 0) {
                    builder.append('0')
                } else {
                    builder.insert(0, '-')
                    builder.append((it.code - 0x0049 + 0x0030).toInt().toChar())
                }
            }
        }
    }
    if (scale != 0) {
        builder.insert(builder.length - scale, ".")
    }
    return BigDecimal(builder.toString())
}

/**
 * Encoding/Decoding a numeric value for a data structure
 */
fun encodeToDS(inValue: BigDecimal, digits: Int, scale: Int): String {
    // get just the digits from BigDecimal, "normalize" away sign, decimal place etc.
    val inChars = inValue.abs().movePointRight(scale).toBigInteger().toString().toCharArray()
    var buffer = IntArray(inChars.size / 2 + 1)

    // read the sign
    val sign = inValue.signum()

    var offset = 0
    var inPosition = 0
    var firstNibble: Int
    var secondNibble: Int

    // place all the digits except last one
    while (inPosition < inChars.size - 1) {
        firstNibble = ((inChars[inPosition++].toInt()) and 0x000F) shl 4
        secondNibble = (inChars[inPosition++].toInt()) and 0x000F
        buffer[offset++] = (firstNibble + secondNibble).toInt()
    }

    // place last digit and sign nibble
    firstNibble = if (inPosition == inChars.size) {
        0x00F0
    } else {
        (inChars[inChars.size - 1].toInt()) and 0x000F shl 4
    }
    if (sign != -1) {
        buffer[offset] = (firstNibble + 0x000F).toInt()
    } else {
        buffer[offset] = (firstNibble + 0x000D).toInt()
    }

    var s = ""
    buffer.forEach { byte ->
        s += byte.toChar()
    }

    return s
}

fun decodeFromDS(value: String, digits: Int, scale: Int): BigDecimal {
    val buffer = IntArray(value.length)
    for (i in value.indices) {
        buffer[i] = value[i].code
    }

    var sign: String = ""
    var number: String = ""
    var nibble = ((buffer[buffer.size - 1]) and 0x0F)
    if (nibble == 0x0B || nibble == 0x0D) {
        sign = "-"
    }

    var offset = 0
    while (offset < (buffer.size - 1)) {
        nibble = (buffer[offset] and 0xFF).ushr(4)
        number += Character.toString((nibble or 0x30).toChar())
        nibble = buffer[offset] and 0x0F or 0x30
        number += Character.toString((nibble or 0x30).toChar())

        offset++
    }

    // read last digit
    nibble = (buffer[offset] and 0xFF).ushr(4)
    if (nibble <= 9) {
        number += (nibble or 0x30).toChar().toString()
    }
    // adjust the scale
    if (scale > 0 && number != "0") {
        val len = number.length
        number = buildString {
            append(number.substring(0, len - scale))
            append(".")
            append(number.substring(len - scale, len))
        }
    }
    number = sign + number
    return try {
        value.toBigDecimal()
    } catch (e: Exception) {
        number.toBigDecimal()
    }
}

enum class RpgType(val rpgType: String) {
    PACKED("P"),
    ZONED("S"),
    INTEGER("I"),
    UNSIGNED("U"),
    BINARY("B")
}
