package com.strumenta.starlasu.codebase

import com.strumenta.starlasu.base.v2.ASTLanguage
import com.strumenta.starlasu.base.v2.CodebaseLanguage
import com.strumenta.starlasu.language.KolasuLanguage
import com.strumenta.starlasu.lionweb.LIONWEB_VERSION_USED_BY_STARLASU
import com.strumenta.starlasu.lionweb.LionWebModelConverter
import com.strumenta.starlasu.lionweb.TokensList
import com.strumenta.starlasu.model.BaseASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.pipeline.CodebaseAccess
import io.lionweb.kotlin.setPropertyValueByName
import io.lionweb.model.ClassifierInstanceUtils
import io.lionweb.model.impl.DynamicNode
import io.lionweb.serialization.SerializationProvider
import io.lionweb.serialization.data.MetaPointer
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

data class TestRoot(
    val value: String,
) : BaseASTNode()

class CodebaseAdaptersTest {
    @Test
    fun `unparsed files do not prevent access to the codebase`() {
        val serialization = SerializationProvider.getEfficientProtoBufSerialization(LIONWEB_VERSION_USED_BY_STARLASU)
        val converter = LionWebModelConverter()
        val language =
            converter.exportLanguageToLionWeb(
                KolasuLanguage("test.language").apply { addClass(TestRoot::class) },
            )
        serialization.registerLanguage(ASTLanguage.getInstance())
        serialization.registerLanguage(CodebaseLanguage.getInstance())
        serialization.registerLanguage(language)
        converter.prepareSerialization(serialization)
        serialization.enableDynamicNodes()
        val missingAst =
            DynamicNode("failed", CodebaseLanguage.getInstance().codebaseFile).apply {
                setPropertyValueByName("language_name", "RPG")
                setPropertyValueByName("relative_path", "failed.dds")
                setPropertyValueByName("code", "invalid")
            }
        val parsed =
            DynamicNode("parsed", CodebaseLanguage.getInstance().codebaseFile).apply {
                setPropertyValueByName("language_name", "RPG")
                setPropertyValueByName("relative_path", "ok.rpg")
                setPropertyValueByName("code", "valid")
                setPropertyValueByName("tokens", TokensList(emptyList()))
                val root = TestRoot("ok").apply { id = "root" }
                ClassifierInstanceUtils.setOnlyChildByContainmentName(this, "ast", converter.exportModelToLionWeb(root))
            }
        val bytes =
            mapOf(
                "failed" to serialization.serializeTreesToByteArray(missingAst),
                "parsed" to serialization.serializeTreesToByteArray(parsed),
            )
        var retrievals = 0
        val access =
            object : CodebaseAccess {
                override fun getName() = "test"

                override fun files(): Stream<String> = Stream.of("failed", "parsed")

                override fun fileByRelativePath(relativePath: String): String? = null

                override fun partitionIDs(): Stream<String> = Stream.empty()

                override fun partitionClassifier(partitionID: String): MetaPointer = error("unused")

                override fun codebaseNode(): ByteArray = error("unused")

                override fun retrieve(partitionId: String): ByteArray {
                    retrievals++
                    return bytes.getValue(partitionId)
                }

                override fun containingPartitionId(nodeId: String): String? = null

                override fun findUsages(targetNodeId: String): Set<String> = emptySet()

                override fun languagesUsed(): Set<ByteArray> = emptySet()
            }
        val codebase = access.convertToCodebase<Node>(converter, setOf("RPG"), serialization)
        assertEquals(listOf("ok.rpg"), codebase.files().map { it.relativePath }.toList())
        assertEquals("ok", (codebase.fileByRelativePath("ok.rpg")!!.ast as TestRoot).value)
        assertNull(codebase.fileByRelativePath("failed.dds"))
        assertEquals(2, retrievals)
    }
}
