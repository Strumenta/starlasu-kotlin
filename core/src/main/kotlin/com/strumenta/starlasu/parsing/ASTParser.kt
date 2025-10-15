package com.strumenta.starlasu.parsing

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.model.StringSource
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

private fun inputStreamToString(
    inputStream: InputStream,
    charset: Charset = Charsets.UTF_8,
): String = inputStream.bufferedReader(charset).use(BufferedReader::readText)

interface ASTParser<R : Node> {
    /**
     * Parses source code, returning a result that includes an AST and a collection of parse issues (errors, warnings).
     * The parsing is done in accordance to the StarLasu methodology i.e. a first-stage parser builds a parse tree which
     * is then mapped onto a higher-level tree called the AST.
     * @param inputStream the source code.
     * @param charset the character set in which the input is encoded.
     * @param considerPosition if true (the default), parsed AST nodes record their position in the input text.
     * @param measureLexingTime if true, the result will include a measurement of the time spent in lexing i.e. breaking
     * the input stream into tokens.
     */
    fun parse(
        inputStream: InputStream,
        charset: Charset = Charsets.UTF_8,
        considerPosition: Boolean = true,
        measureLexingTime: Boolean = false,
        source: Source? = null,
    ): ParsingResult<R> = parse(inputStreamToString(inputStream, charset), considerPosition, measureLexingTime, source)

    fun parse(
        code: String,
        considerPosition: Boolean = true,
        measureLexingTime: Boolean = false,
        source: Source? = null,
    ): ParsingResult<R>

    fun parse(
        code: String,
        considerPosition: Boolean = true,
        measureLexingTime: Boolean = false,
    ): ParsingResult<R> = parse(code, considerPosition, measureLexingTime, StringSource(code))

    fun parse(
        file: File,
        charset: Charset = Charsets.UTF_8,
        considerPosition: Boolean = true,
        measureLexingTime: Boolean = false,
    ): ParsingResult<R>
}
