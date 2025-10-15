package com.strumenta.starlasu.parsing

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.utils.capitalize
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueSeverity
import com.strumenta.starlasu.validation.IssueType
import com.strumenta.starlasu.validation.Result
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.Serializable
import java.nio.charset.Charset

/**
 * The result of processing a piece of source code.
 * @param D the type of the transformed data.
 * @param issues a list of issues encountered while processing the code.
 * @param data the result of the process.
 * @param code the processed source code.
 * @param source where the source code comes from.
 */
open class CodeProcessingResult<D>(
    val issues: List<Issue>,
    val data: D?,
    val code: String? = null,
    val source: Source? = null,
) : Serializable {
    val isCorrect: Boolean
        get() = issues.none { it.severity != IssueSeverity.INFO }

    @Deprecated(message = "Use isCorrect instead.", replaceWith = ReplaceWith("isCorrect"))
    val correct: Boolean = isCorrect

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CodeProcessingResult<*>) return false

        if (issues != other.issues) return false
        if (data != other.data) return false
        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        var result = issues.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + (code?.hashCode() ?: 0)
        return result
    }
}

data class TokenCategory(
    val type: String,
) {
    companion object {
        val COMMENT = TokenCategory("Comment")
        val KEYWORD = TokenCategory("Keyword")
        val NUMERIC_LITERAL = TokenCategory("Numeric literal")
        val STRING_LITERAL = TokenCategory("String literal")
        val OTHER_LITERAL = TokenCategory("Other literal")
        val PLAIN_TEXT = TokenCategory("Plain text")
        val WHITESPACE = TokenCategory("Whitespace")
        val IDENTIFIER = TokenCategory("Identifier")
        val PUNCTUATION = TokenCategory("Punctuation")
        val OPERATOR = TokenCategory("Operator")
    }
}

/**
 * A token is a portion of text that has been assigned a category.
 */
open class KolasuToken(
    open val category: TokenCategory,
    open val position: Position,
    open val text: String? = null,
) : Serializable

/**
 * A [KolasuToken] generated from a [Token]. The [token] contains additional information that is specific to ANTLR,
 * such as type and channel.
 */
data class KolasuANTLRToken(
    override val category: TokenCategory,
    val token: Token,
) : KolasuToken(category, token.position, token.text)

/**
 * The result of lexing (tokenizing) a stream.
 */
class LexingResult<T : KolasuToken>(
    issues: List<Issue>,
    val tokens: List<T>,
    code: String? = null,
    val time: Long? = null,
    source: Source? = null,
) : CodeProcessingResult<List<T>>(issues, tokens, code, source) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LexingResult<*>) return false
        if (!super.equals(other)) return false

        if (tokens != other.tokens) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + tokens.hashCode()
        return result
    }
}

/**
 * The result of first-stage parsing: from source code to a parse tree.
 */
class FirstStageParsingResult<C : ParserRuleContext>(
    issues: List<Issue>,
    val root: C?,
    code: String? = null,
    val incompleteNode: Node? = null,
    val time: Long? = null,
    val lexingTime: Long? = null,
    source: Source? = null,
) : CodeProcessingResult<C>(issues, root, code, source) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirstStageParsingResult<*>) return false
        if (!super.equals(other)) return false

        if (root != other.root) return false
        if (incompleteNode != other.incompleteNode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (root?.hashCode() ?: 0)
        result = 31 * result + (incompleteNode?.hashCode() ?: 0)
        return result
    }
}

/**
 * The complete result of parsing a piece of source code into an AST.
 * @param RootNode the type of the root AST node.
 * @param issues a list of issues encountered while processing the code.
 * @param root the resulting AST.
 * @param code the processed source code.
 * @param firstStage the result of the first parsing stage (from source code to parse tree).
 * @param time the time spent in the entire parsing process.
 */
open class ParsingResult<RootNode : ASTNode>(
    issues: List<Issue>,
    val root: RootNode?,
    code: String? = null,
    val incompleteNode: ASTNode? = null,
    val firstStage: FirstStageParsingResult<*>? = null,
    val time: Long? = null,
    source: Source? = null,
) : CodeProcessingResult<RootNode>(issues, root, code, source) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsingResult<*>) return false
        if (!super.equals(other)) return false

        if (root != other.root) return false
        if (incompleteNode != other.incompleteNode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (root?.hashCode() ?: 0)
        result = 31 * result + (incompleteNode?.hashCode() ?: 0)
        return result
    }

    fun toResult(): Result<RootNode> = Result(issues, root)
}

fun String.toStream(charset: Charset = Charsets.UTF_8) = ByteArrayInputStream(toByteArray(charset))

interface KolasuLexer<T : KolasuToken> : Serializable {
    /**
     * Performs "lexing" on the given code string, i.e., it breaks it into tokens.
     */
    fun lex(
        code: String,
        onlyFromDefaultChannel: Boolean = true,
    ) = lex(code.byteInputStream(Charsets.UTF_8), Charsets.UTF_8, onlyFromDefaultChannel)

    /**
     * Performs "lexing" on the given code string, i.e., it breaks it into tokens.
     */
    fun lex(code: String) = lex(code, true)

    /**
     * Performs "lexing" on the given code stream, i.e., it breaks it into tokens.
     */
    fun lex(
        inputStream: InputStream,
        charset: Charset = Charsets.UTF_8,
        onlyFromDefaultChannel: Boolean = true,
        source: Source? = null,
    ): LexingResult<T>

    /**
     * Performs "lexing" on the given code stream, i.e., it breaks it into tokens.
     */
    fun lex(
        inputStream: InputStream,
        charset: Charset = Charsets.UTF_8,
    ) = lex(inputStream, charset, true)

    /**
     * Performs "lexing" on the given code stream, i.e., it breaks it into tokens.
     */
    fun lex(inputStream: InputStream) = lex(inputStream, Charsets.UTF_8, true)

    /**
     * Performs "lexing" on the given code stream, i.e., it breaks it into tokens.
     */
    fun lex(
        file: File,
        charset: Charset = Charsets.UTF_8,
        onlyFromDefaultChannel: Boolean = true,
    ): LexingResult<T> = BufferedInputStream(FileInputStream(file)).use { lex(it, charset, onlyFromDefaultChannel) }
}

fun Lexer.injectErrorCollectorInLexer(issues: MutableList<Issue>) {
    this.removeErrorListeners()
    this.addErrorListener(
        object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                errorMessage: String?,
                recognitionException: RecognitionException?,
            ) {
                issues.add(
                    Issue(
                        IssueType.LEXICAL,
                        errorMessage ?: "unspecified",
                        position = Point(line, charPositionInLine).asPosition,
                    ),
                )
            }
        },
    )
}

fun Parser.injectErrorCollectorInParser(issues: MutableList<Issue>) {
    this.removeErrorListeners()
    this.addErrorListener(
        object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                errorMessage: String?,
                recognitionException: RecognitionException?,
            ) {
                val startPoint = Point(line, charPositionInLine)
                var endPoint = startPoint
                if (offendingSymbol is CommonToken) {
                    endPoint = offendingSymbol.endPoint
                }
                issues.add(
                    Issue(
                        IssueType.SYNTACTIC,
                        errorMessage?.capitalize() ?: "unspecified",
                        position = Position(startPoint, endPoint),
                    ),
                )
            }
        },
    )
}
