package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.ids.SimpleSourceIdProvider
import com.strumenta.starlasu.ids.SourceShouldBeSetException
import com.strumenta.starlasu.model.Source
import io.lionweb.kotlin.BaseNode
import io.lionweb.language.Concept
import com.strumenta.starlasu.base.v2.ASTLanguageV2 as ASTLanguage

class ParsingResultNode(
    val source: Source?,
) : BaseNode(LIONWEB_VERSION_USED_BY_STARLASU) {
    override fun calculateID(): String? =
        try {
            SimpleSourceIdProvider().sourceId(source) + "_ParsingResult"
        } catch (_: SourceShouldBeSetException) {
            super.calculateID()
        }

    override fun getClassifier(): Concept = ASTLanguage.getParsingResult()
}
