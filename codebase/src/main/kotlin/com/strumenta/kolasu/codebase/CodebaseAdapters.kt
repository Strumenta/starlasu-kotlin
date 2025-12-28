package com.strumenta.kolasu.codebase

import com.strumenta.kolasu.lionweb.IssueNode
import com.strumenta.kolasu.lionweb.LWNode
import com.strumenta.kolasu.lionweb.LionWebModelConverter
import com.strumenta.kolasu.lionweb.TokensList
import com.strumenta.kolasu.model.Node
import com.strumenta.starlasu.base.v1.CodebaseLanguage
import com.strumenta.starlasu.pipeline.CodebaseAccess
import io.lionweb.kotlin.getChildrenByContainmentName
import io.lionweb.kotlin.getOnlyChildByContainmentName
import io.lionweb.kotlin.getPropertyValueByName
import io.lionweb.kotlin.setPropertyValueByName
import io.lionweb.model.ClassifierInstanceUtils
import io.lionweb.model.impl.DynamicNode
import io.lionweb.serialization.ProtoBufSerialization
import java.util.stream.Collectors

fun <R : Node> deserialize(
    modelConverter: LionWebModelConverter,
    codebase: Codebase<R>,
    codebaseFile: LWNode
): CodebaseFile<R> {
    val relativePath = codebaseFile.getPropertyValueByName("relative_path") as String
    val code = codebaseFile.getPropertyValueByName("code") as String
    val ast = codebaseFile.getOnlyChildByContainmentName("ast") ?: throw IllegalStateException(
        "No AST found for $relativePath"
    )
    val compilationUnit = modelConverter.importModelFromLionWeb(ast) as R
    val issues =
        codebaseFile.getChildrenByContainmentName("issues").map { lwIssue ->
            modelConverter.importIssueFromLionweb(lwIssue as IssueNode).second
        }
    val tokens = codebaseFile.getPropertyValueByName("tokens") as TokensList
    return CodebaseFile(codebase, relativePath, code, compilationUnit, tokens, issues)
}

fun <R : Node> serialize(
    modelConverter: LionWebModelConverter,
    id: String,
    languageName: String,
    codebaseFile: CodebaseFile<R>
): LWNode {
    val lwCodebaseFile = DynamicNode(id, CodebaseLanguage.getInstance().codebaseFile)
    lwCodebaseFile.setPropertyValueByName("language_name", languageName)
    lwCodebaseFile.setPropertyValueByName("relative_path", codebaseFile.relativePath)
    lwCodebaseFile.setPropertyValueByName("code", codebaseFile.code)
    lwCodebaseFile.setPropertyValueByName("tokens", codebaseFile.tokens)
    modelConverter.clearNodesMapping()
    val ast = modelConverter.exportModelToLionWeb(codebaseFile.ast)
    ClassifierInstanceUtils.setOnlyChildByContainmentName(lwCodebaseFile, "ast", ast)
    codebaseFile.parsingIssues.forEachIndexed { index, issue ->
        val lwIssue = modelConverter.exportIssueToLionweb(issue, "$id-issue-$index")
        ClassifierInstanceUtils.addChild(lwCodebaseFile, "issues", lwIssue)
    }
    return lwCodebaseFile
}

fun <R : Node> CodebaseAccess.convertToCodebase(
    modelConverter: LionWebModelConverter,
    languagesWeConsider: Set<String>,
    pbSerialization: ProtoBufSerialization
): Codebase<R> {
    val codebaseAccess = this
    return object : Codebase<R> {
        override val name: String
            get() = codebaseAccess.name

        private val filesCache: List<CodebaseFile<R>> by lazy {
            codebaseAccess.files().map { fileIdentifier ->
                val serializedFile = codebaseAccess.retrieve(fileIdentifier)
                pbSerialization.deserializeToNodes(serializedFile)[0]
            }.filter { serializedCodebaseFile ->
                val languageName = serializedCodebaseFile!!.getPropertyValueByName("language_name") as String
                languagesWeConsider.contains(languageName)
            }.map { serializedCodebaseFile ->
                deserialize(modelConverter, this, serializedCodebaseFile!!)
            }.collect(Collectors.toList())
        }

        override fun files(): Sequence<CodebaseFile<R>> {
            return filesCache.asSequence()
        }

        override fun fileByRelativePath(relativePath: String): CodebaseFile<R>? {
            return files().find { it.relativePath == relativePath }
        }
    }
}
