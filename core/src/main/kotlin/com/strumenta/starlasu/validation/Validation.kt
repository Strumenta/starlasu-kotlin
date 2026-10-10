package com.strumenta.starlasu.validation

import com.strumenta.starlasu.model.Position
import java.io.Serializable

/**
 * The processing phase in which an [Issue] was found.
 */
enum class IssueType {
    LEXICAL,
    SYNTACTIC,
    SEMANTIC,
    TRANSLATION,
}

/**
 * The severity of an [Issue].
 */
enum class IssueSeverity {
    ERROR,
    WARNING,
    INFO,
}

/**
 * A problem found while processing code, optionally located at a [position].
 */
data class Issue(
    val type: IssueType,
    val message: String,
    val severity: IssueSeverity = IssueSeverity.ERROR,
    val position: Position? = null,
) : Serializable {
    companion object {
        @JvmOverloads
        fun lexical(
            message: String,
            severity: IssueSeverity = IssueSeverity.ERROR,
            position: Position? = null,
        ): Issue = Issue(IssueType.LEXICAL, message, severity, position)

        @JvmOverloads
        fun syntactic(
            message: String,
            severity: IssueSeverity = IssueSeverity.ERROR,
            position: Position? = null,
        ): Issue = Issue(IssueType.SYNTACTIC, message, severity, position)

        @JvmOverloads
        fun semantic(
            message: String,
            severity: IssueSeverity = IssueSeverity.ERROR,
            position: Position? = null,
        ): Issue = Issue(IssueType.SEMANTIC, message, severity, position)

        @JvmOverloads
        fun translation(
            message: String,
            severity: IssueSeverity = IssueSeverity.ERROR,
            position: Position? = null,
        ): Issue = Issue(IssueType.TRANSLATION, message, severity, position)
    }
}
