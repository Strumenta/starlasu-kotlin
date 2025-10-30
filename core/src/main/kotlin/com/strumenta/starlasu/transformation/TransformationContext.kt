package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueSeverity

/**
 * A context holding metadata relevant to the transformation process, such as parent node reference, associated source,
 * and issues discovered during transformation.
 *
 * This is an open class so that specialized AST transformers can extend it to track additional information. However,
 * to keep type signatures reasonably simple, AST transformers are not generic on the context class. This is also
 * because we expect that only few, if any, AST transformation rules ([TransformationRule] instances) will require a
 * custom context; those few can just cast the context to the desired subclass.
 *
 * @constructor Creates an instance of the transformation context.
 * @param issues A mutable list of issues encountered during the transformation.
 *               Defaults to an empty list if not provided.
 * @param parent The parent [com.strumenta.starlasu.model.ASTNode] in the hierarchy, if available. Defaults to null.
 * @param source The [com.strumenta.starlasu.model.Source] object associated with this context, if any. Defaults to null.
 */
open class TransformationContext
    @JvmOverloads
    constructor(
        /**
         * Additional issues found during the transformation process.
         */
        val issues: MutableList<Issue> = mutableListOf(),
        var parent: ASTNode? = null,
        var source: Source? = null,
    ) {
        fun addIssue(
            message: String,
            severity: IssueSeverity = IssueSeverity.ERROR,
            position: Position? = null,
        ): Issue {
            val issue =
                Issue.semantic(
                    message,
                    severity,
                    position?.apply { source = this@TransformationContext.source },
                )
            issues.add(issue)
            return issue
        }
    }
