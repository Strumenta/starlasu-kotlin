package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.validation.Issue

/**
 * Thrown when applying a transformation fails, whatever the kind of transformation
 * (e.g., parse tree to AST, or AST to AST).
 *
 * @param issues the issues found before the transformation failed.
 * @param cause the exception thrown during the transformation.
 */
class MappingException
    @JvmOverloads
    constructor(
        message: String,
        val issues: List<Issue>,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause)
