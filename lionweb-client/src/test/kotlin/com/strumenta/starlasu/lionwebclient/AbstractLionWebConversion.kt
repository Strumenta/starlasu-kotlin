package com.strumenta.starlasu.lionwebclient
import com.strumenta.starlasu.language.KolasuLanguage
import com.strumenta.starlasu.lionweb.StructuralLionWebNodeIdProvider
import com.strumenta.starlasu.model.assignParents
import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.parsing.ParsingResult
import com.strumenta.starlasu.testing.assertASTsAreEqual
import com.strumenta.starlasu.traversing.children
import com.strumenta.starlasu.traversing.walk
import java.io.InputStream
import com.strumenta.starlasu.model.Node as KNode
import io.lionweb.model.Node as LWNode

abstract class AbstractLionWebConversion<R : KNode>(val kolasuLanguage: KolasuLanguage) {
    protected abstract fun parse(inputStream: InputStream): ParsingResult<R>

    protected open fun initializeClient(kolasuClient: KolasuClient) {
    }

    protected fun checkSerializationAndDeserialization(
        inputStream: InputStream,
        astChecker: (ast: R) -> Unit = {},
        lwASTChecker: (lwAST: LWNode) -> Unit = {},
        jsonChecker: (json: String) -> Unit = {}
    ) {
        val result = parse(inputStream)
        val ast = result.root ?: throw IllegalStateException()
        ast.assignParents()
        val encounteredNodes = mutableListOf<ASTNode>()
        ast.walk().forEach { descendant ->
            val encounteredChildren = mutableListOf<ASTNode>()
            descendant.children.forEach { child ->
                if (encounteredChildren.any { encounteredChild -> encounteredChild === child }) {
                    throw IllegalStateException("Duplicate child: $child in $descendant")
                } else {
                    encounteredChildren.add(child)
                }
            }
            if (encounteredNodes.any { encounteredNode -> encounteredNode === descendant }) {
                throw IllegalStateException("Duplicate node: $descendant (parent ${descendant.parent?.nodeType})")
            } else {
                encounteredNodes.add(descendant)
            }
        }
        astChecker.invoke(ast)

        val client = KolasuClient()
        client.registerLanguage(kolasuLanguage)
        initializeClient(client)
        val baseId = "foo"
        val lwAST = client.nodeConverter.exportModelToLionWeb(ast, StructuralLionWebNodeIdProvider(baseId))
        lwASTChecker.invoke(lwAST)
        val json = client.jsonSerialization.serializeTreeToJsonString(lwAST)
        jsonChecker.invoke(json)
        val lwASTDeserialized =
            client.jsonSerialization.deserializeToNodes(json)
                .find { it.id == lwAST.id } ?: throw IllegalStateException()
        val astDeserialized = client.nodeConverter.importModelFromLionWeb(lwASTDeserialized) as KNode

        assertASTsAreEqual(ast, astDeserialized)
    }
}
