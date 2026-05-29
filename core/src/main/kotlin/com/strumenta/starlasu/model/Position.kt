package com.strumenta.starlasu.model

import com.strumenta.kolasu.interning.PointInterner
import java.io.File
import java.io.Serializable
import java.net.URL
import java.nio.file.Path

val START_LINE = 1
val START_COLUMN = 0
val START_POINT = Point(START_LINE, START_COLUMN)

/**
 * A location in a source code file.
 * The line should be in 1..n, the column in 0..n.
 *
 * Consider a file with one line, containing text "HELLO":
 * - the point before the first character will be Point(1, 0)
 * - the point at the end of the first line, after the letter "O" will be Point(1, 5)
 */
data class Point(
    val line: Int,
    val column: Int,
) : Comparable<Point>,
    Serializable {
    override fun compareTo(other: Point): Int {
        if (line == other.line) {
            return this.column - other.column
        }
        return this.line - other.line
    }

    init {
        checkLine(line)
        checkColumn(column)
    }

    companion object {
        fun checkLine(line: Int) {
            require(line >= START_LINE) { "Line should be equal or greater than 1, was $line" }
        }

        fun checkColumn(column: Int) {
            require(column >= START_COLUMN) { "Column should be equal or greater than 0, was $column" }
        }

        // Global bounded interner. 500 K entries ≈ 25 MB worst-case, far cheaper than millions of duplicates.
        private val globalInterner = PointInterner(500_000)

        /**
         * Returns a canonical [Point] equal to `Point(line, column)`, reusing a previously interned
         * instance if one exists. Use this in all hot construction paths (token positions, etc.).
         *
         * The underlying cache is bounded; once full, fresh instances are returned without caching
         * rather than blocking or throwing.
         */
        fun intern(line: Int, column: Int): Point = globalInterner.intern(line, column)

        /** Canonical instance for the most common point in any source file. */
        val START: Point = intern(START_LINE, START_COLUMN)
    }

    override fun toString() = "Line $line, Column $column"

    fun positionWithLength(length: Int): Position {
        require(length >= 0)
        return Position(this, this.plus(length))
    }

    /**
     * Translate the Point to an offset in the original code stream.
     */
    fun offset(code: String): Int = offsetFrom(code, START_COLUMN, START_LINE)

    /**
     * Like [offset] but starts scanning from [fromCharOffset] (which is the start of [fromLine])
     * instead of from the beginning of the string. Use this to continue a scan already in progress,
     * avoiding re-scanning from position 0.
     *
     * Precondition: [fromLine] <= [line].
     */
    fun offsetFrom(code: String, fromCharOffset: Int, fromLine: Int): Int {
        var lineCount = fromLine
        var pos = fromCharOffset
        while (pos < code.length && lineCount < line) {
            when {
                code[pos] == '\r' && pos + 1 < code.length && code[pos + 1] == '\n' -> {
                    pos += 2; lineCount++
                }
                code[pos] == '\r' || code[pos] == '\n' -> {
                    pos++; lineCount++
                }
                else -> pos++
            }
        }
        require(lineCount >= line) {
            "The point does not exist in the given text. " +
                "It indicates line $line but there are only $lineCount lines"
        }
        var lineEnd = pos
        while (lineEnd < code.length && code[lineEnd] != '\r' && code[lineEnd] != '\n') lineEnd++
        require(lineEnd - pos >= column) {
            "The column does not exist in the given text. " +
                "Line $line has ${lineEnd - pos} columns, the point indicates column $column"
        }
        return pos + column
    }

    /**
     * Computes whether this point comes strictly before another point.
     * @param other the other point
     */
    fun isBefore(other: Point) = this < other

    /**
     * Computes whether this point is the same as, or comes before, another point.
     * @param other the other point
     */
    fun isSameOrBefore(other: Point) = this <= other

    /**
     * Computes whether this point is the same as, or comes after, another point.
     * @param other the other point
     */
    fun isSameOrAfter(other: Point) = this >= other

    operator fun plus(length: Int): Point {
        return intern(this.line, this.column + length)
    }

    operator fun plus(text: String): Point {
        if (text.isEmpty()) {
            return this
        }
        var line = this.line
        var column = this.column
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n' || text[i] == '\r') {
                line++
                column = 0
                if (text[i] == '\r' && i < text.length - 1 && text[i + 1] == '\n') {
                    i++ // Count the \r\n sequence as 1 line
                }
            } else {
                column++
            }
            i++
        }
        return intern(line, column)
    }

    val asPosition: Position
        get() = Position(this, this)
}

fun linePosition(
    lineNumber: Int,
    lineCode: String,
    source: Source? = null,
): Position {
    require(lineNumber >= 1) { "Line numbers are expected to be equal or greater than 1" }
    return Position(Point(lineNumber, START_COLUMN), Point(lineNumber, lineCode.length), source)
}

abstract class Source : Serializable

interface SourceWithID {
    fun sourceID(): String
}

class SourceSet(
    val name: String,
    val root: Path,
)

class SourceSetElement(
    val sourceSet: SourceSet,
    val relativePath: Path,
) : Source()

data class FileSource(
    val file: File,
) : Source()

class StringSource(
    val code: String? = null,
) : Source()

class URLSource(
    val url: URL,
) : Source()

data class CodeBaseSource(
    val codebaseName: String,
    val relativePath: String,
) : Source()

/**
 * This source is intended to be used for nodes that are "calculated".
 * For example, nodes representing types that are derived by examining the code
 * but cannot be associated to any specific point in the code.
 *
 * @param description this is a description of the source. It is used to describe the process that calculated the node.
 *                    Examples of values could be "type inference".
 */
data class SyntheticSource(
    val description: String,
) : Source()

/**
 * An area in a source file, from start to end.
 * The start point is the point right before the starting character.
 * The end point is the point right after the last character.
 * An empty position will have coinciding points.
 *
 * Consider a file with one line, containing text "HELLO".
 * The Position of such text will be Position(Point(1, 0), Point(1, 5)).
 */
data class Position(
    val start: Point,
    val end: Point,
    var source: Source? = null,
) : Comparable<Position>,
    Serializable {
    val isFlat: Boolean
        get() = start == end

    override fun toString(): String =
        "Position(start=$start, end=$end${if (source == null) "" else ", source=$source"})"

    override fun compareTo(other: Position): Int {
        val cmp = this.start.compareTo(other.start)
        return if (cmp == 0) {
            this.end.compareTo(other.end)
        } else {
            cmp
        }
    }

    constructor(start: Point, end: Point, source: Source? = null, validate: Boolean = true) : this(start, end, source) {
        if (validate) {
            require(start.isBefore(end) || start == end) {
                "End should follows start or be the same as start (start: $start, end: $end)"
            }
        }
    }

    /**
     * Given the whole code extract the portion of text corresponding to this position.
     */
    fun text(wholeText: String): String {
        // Optimization: rather than calling Point.offset twice (which would scan from the beginning
        // of the string independently for each point), this performs a single pass. The loop below
        // scans to the start of start.line and captures the intermediate scan state
        // (lineStartPos, lineCount). That state is then passed to Point.offsetFrom for end,
        // which continues scanning from where we stopped instead of restarting from position 0.
        // The loop is intentionally duplicated from offsetFrom to avoid the allocation that a shared
        // helper returning scan state would require.
        var lineCount = START_LINE
        var lineStartPos = START_COLUMN
        while (lineStartPos < wholeText.length && lineCount < start.line) {
            when {
                wholeText[lineStartPos] == '\r' && lineStartPos + 1 < wholeText.length &&
                    wholeText[lineStartPos + 1] == '\n' -> { lineStartPos += 2; lineCount++ }
                wholeText[lineStartPos] == '\r' || wholeText[lineStartPos] == '\n' -> { lineStartPos++; lineCount++ }
                else -> lineStartPos++
            }
        }
        val startOffset = lineStartPos + start.column
        val endOffset = end.offsetFrom(wholeText, lineStartPos, lineCount)
        return wholeText.substring(startOffset, endOffset)
    }

    /**
     * The length in characters of the text under this position in the provided source.
     * @param code the source text.
     */
    fun length(code: String): Int {
        // See text() for the optimization rationale. The scan loop is duplicated here
        // to avoid allocating a shared scan-state object on what can be a hot path.
        var lineCount = START_LINE
        var lineStartPos = START_COLUMN
        while (lineStartPos < code.length && lineCount < start.line) {
            when {
                code[lineStartPos] == '\r' && lineStartPos + 1 < code.length &&
                    code[lineStartPos + 1] == '\n' -> { lineStartPos += 2; lineCount++ }
                code[lineStartPos] == '\r' || code[lineStartPos] == '\n' -> { lineStartPos++; lineCount++ }
                else -> lineStartPos++
            }
        }
        val startOffset = lineStartPos + start.column
        val endOffset = end.offsetFrom(code, lineStartPos, lineCount)
        return endOffset - startOffset
    }

    fun isEmpty(): Boolean = start == end

    /**
     * Tests whether the given point is contained in the interval represented by this object.
     * @param point the point.
     */
    fun contains(point: Point): Boolean =
        ((point == start || start.isBefore(point)) && (point == end || point.isBefore(end)))

    /**
     * Tests whether the given position is contained in the interval represented by this object.
     * @param position the position
     */
    fun contains(position: Position?): Boolean =
        (position != null) &&
            this.start.isSameOrBefore(position.start) &&
            this.end.isSameOrAfter(position.end)

    /**
     * Tests whether the given node is contained in the interval represented by this object.
     * @param node the node
     */
    fun contains(node: ASTNode): Boolean = this.contains(node.position)

    /**
     * Tests whether the given position overlaps the interval represented by this object.
     * @param position the position
     */
    fun overlaps(position: Position?): Boolean =
        (position != null) &&
            (
                (this.start.isSameOrAfter(position.start) && this.start.isSameOrBefore(position.end)) ||
                    (this.end.isSameOrAfter(position.start) && this.end.isSameOrBefore(position.end)) ||
                    (position.start.isSameOrAfter(this.start) && position.start.isSameOrBefore(this.end)) ||
                    (position.end.isSameOrAfter(this.start) && position.end.isSameOrBefore(this.end))
            )
}

/**
 * Utility function to create a Position
 */
fun pos(
    startLine: Int,
    startCol: Int,
    endLine: Int,
    endCol: Int,
) = Position(
    Point(startLine, startCol),
    Point(endLine, endCol),
)

fun ASTNode.isBefore(other: ASTNode): Boolean = position!!.start.isBefore(other.position!!.start)

val ASTNode.startLine: Int?
    get() = this.position?.start?.line

val ASTNode.endLine: Int?
    get() = this.position?.end?.line

/**
 * Given the specified text and point it produces a position that will cover the text, minus the whitespace.
 *
 * If a null text is specified then a null position is returned.
 *
 * If a text with no leading or trailing whitespace is returned than this will return a position:
 * - starting at the given start point
 * - ending to the end point calculated "adding" the text to the start point
 *
 * If the text has leading whitespace, the start point will be advanced to skip such whitespace.
 * Similarly, if the text has trailing whitespace the end point will be receded to skip such whitespace.
 */
fun strippedPosition(
    text: String?,
    start: Point,
): Position? =
    text?.let { text ->
        start.positionWithLength(text.length).stripPosition(text)
    }

/**
 * See strippedPosition.
 */
fun Position.stripPosition(text: String): Position {
    if (text.isNotEmpty()) {
        when (text.first()) {
            ' ' -> return this.advanceStart().stripPosition(text.substring(1))
        }
    }
    if (text.isNotEmpty()) {
        when (text.last()) {
            ' ' -> return this.recedeEnd().stripPosition(text.substring(0, text.length - 1))
        }
    }
    val maxEnd = this.start + text
    if (maxEnd.isBefore(this.end)) {
        return Position(start, maxEnd)
    }
    return this
}

fun Position.advanceStart(): Position = Position(Point.intern(start.line, start.column + 1), end)

fun Position.recedeEnd(): Position = Position(start, Point.intern(end.line, end.column - 1))

private fun <K, V> createLeastRecentlyUsedMap(maxEntries: Int = 100): Map<K, V> =
    object : LinkedHashMap<K, V>(maxEntries * 10 / 7, 0.7f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxEntries
    }

private object LinesSplitter {
    val cache = createLeastRecentlyUsedMap<String, List<String>>() as MutableMap<String, List<String>>

    fun getLines(code: String): List<String> =
        cache.getOrPut(code) {
            code.split("(?<=\n)".toRegex())
        }
}

/**
 * Given a piece of code, it extracts from it the substring at the given position.
 */
fun String.codeAtPosition(position: Position): String {
    try {
        val lines = LinesSplitter.getLines(this)
        var res: String

        var currLine = position.start.line
        if (position.start.line == position.end.line) {
            return lines[currLine - 1].substring(position.start.column, position.end.column)
        }
        res = lines[currLine - 1].substring(position.start.column)
        currLine++
        while (currLine <= lines.size && currLine < position.end.line) {
            res += lines[currLine - 1]
            currLine++
        }
        res += lines[currLine - 1].substring(0, position.end.column)
        return res
    } catch (t: Throwable) {
        throw RuntimeException("Unable to get position $position in text:\n```$this```")
    }
}
