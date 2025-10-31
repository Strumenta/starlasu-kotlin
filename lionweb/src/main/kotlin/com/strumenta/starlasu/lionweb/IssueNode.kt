package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.model.Position
import io.lionweb.kotlin.BaseNode
import io.lionweb.language.Concept
import io.lionweb.model.impl.EnumerationValue
import com.strumenta.starlasu.base.v2.ASTLanguageV2 as ASTLanguage

class IssueNode : BaseNode(LIONWEB_VERSION_USED_BY_STARLASU) {
    var type: EnumerationValue? by property("type")
    var message: String? by property("message")
    var severity: EnumerationValue? by property("severity")
    var position: Position? by property("position")

    override fun getClassifier(): Concept = ASTLanguage.getIssue()
}
