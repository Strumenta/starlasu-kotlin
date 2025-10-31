package com.strumenta.starlasu.testing

import com.strumenta.starlasu.model.START_POINT
import com.strumenta.starlasu.model.codeAtPosition
import com.strumenta.starlasu.parsing.StarlasuLexer
import com.strumenta.starlasu.parsing.StarlasuToken
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.test.assertEquals

fun <T : StarlasuToken> checkFileTokenization(
    file: File,
    lexer: StarlasuLexer<T>,
    charset: Charset = Charsets.UTF_8,
): List<T> {
    require(file.exists())
    require(file.isFile())
    require(file.canRead())
    val code = file.readText(charset = charset)
    return checkTokenization(code, lexer)
}

fun <T : StarlasuToken> checkTokenization(
    inputStream: InputStream,
    lexer: StarlasuLexer<T>,
    charset: Charset = Charsets.UTF_8,
): List<T> {
    val code = inputStream.bufferedReader(charset = charset).use { it.readText() }
    return checkTokenization(code, lexer)
}

fun <T : StarlasuToken> checkTokenization(
    code: String,
    lexer: StarlasuLexer<T>,
): List<T> {
    val lexingResult = lexer.lex(code, onlyFromDefaultChannel = false)
    require(lexingResult.issues.isEmpty()) {
        "Lexing issues occurred: ${lexingResult.issues}"
    }
    checkTokensAreCoveringText(code, lexingResult.tokens)
    return lexingResult.tokens
}

fun <T : StarlasuToken> checkTokensAreCoveringText(
    code: String,
    tokens: List<T>,
) {
    require(code.isEmpty() == tokens.isEmpty())
    if (code.isEmpty()) {
        return
    }

    // Tokens should be in order and they should cover without gaps or overlaps
    // the text from the very start to the very end of the code

    var prevToken: StarlasuToken? = null
    tokens.forEach { token ->
        if (prevToken == null) {
            // This is the first token, so we should start at the very beginning
            assertEquals(
                token.position.start,
                START_POINT,
                "The first token is expected to be at the start position $START_POINT while it is ast " +
                    "${token.position.start}",
            )
        } else {
            assertEquals(
                token.position.start,
                prevToken!!.position.end,
                "Token $token does not immediately follow $prevToken",
            )
        }

        // The text specified in tokens should be as long as the position indicated
        assertEquals(
            token.position.start + (token.text ?: ""),
            token.position.end,
            "We have a token with position ${token.position} and text '${token.text}'",
        )

        // The text specified in the tokens should correspond to the corresponding code
        val expectedText = code.codeAtPosition(token.position)
        assertEquals(
            expectedText,
            token.text,
            "At position ${token.position} we found '${token.text}' while we expected '$expectedText'",
        )

        prevToken = token
    }

    val codeEnd = START_POINT + code
    assertEquals(prevToken!!.position.end, codeEnd)
}
