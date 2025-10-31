package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.parsing.FirstStageParsingResult
import com.strumenta.starlasu.parsing.ParsingResult
import com.strumenta.starlasu.parsing.StarlasuToken
import com.strumenta.starlasu.validation.Issue

class ParsingResultWithTokens<RootNode : SNode>(
    issues: List<Issue>,
    root: RootNode?,
    val tokens: List<StarlasuToken>,
    code: String? = null,
    incompleteNode: Node? = null,
    firstStage: FirstStageParsingResult<*>? = null,
    time: Long? = null,
    source: Source? = null,
) : ParsingResult<RootNode>(issues, root, code, incompleteNode, firstStage, time, source)
