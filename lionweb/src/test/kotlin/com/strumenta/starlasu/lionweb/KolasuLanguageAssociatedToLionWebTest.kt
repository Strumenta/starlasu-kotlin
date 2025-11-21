package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.language.KolasuLanguage
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.ReferenceByName
import io.lionweb.language.Concept
import io.lionweb.language.Enumeration
import io.lionweb.language.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@LionWebAssociation("key-123")
data class LWRoot(
    val _id: Int,
    val childrez: MutableList<LWNodeA>,
) : Node()

@LionWebAssociation("key-456")
data class LWNodeA(
    override val name: String,
    val ref: ReferenceByName<LWNodeA>,
    val child: LWNodeB?,
) : Node(),
    Named

@LionWebAssociation("key-000")
enum class MyEnum {
    A,
    B,
    C,
}

@LionWebAssociation("key-789")
data class LWNodeB(
    val value: String,
    val anotherValue: MyEnum,
) : Node()

class KolasuLanguageAssociatedToLionWebTest {
    @Test
    fun enumsAreRecorded() {
        val lwImpExp = LionWebModelConverter()
        val lwLanguage =
            lwImpExp.exportLanguageToLionWeb(
                KolasuLanguage("pricing").apply {
                    addClass(LWRoot::class)
                },
            )
        assertEquals(4, lwLanguage.elements.size)
        val myEnum = lwLanguage.getElementByName("MyEnum")
        assertTrue { myEnum is Enumeration }
    }

    @Test
    fun conceptsAreAssociatedWithRightKey() {
        val lwLang =
            Language("LangA").apply {
                setID("lang-id")
                setKey("lang-key")
                setVersion("45")
            }
        val lwRoot =
            Concept(lwLang, "Root", "id-123").apply {
                setKey("key-123")
            }
        val lwNodeA =
            Concept(lwLang, "NodeA", "id-456").apply {
                setKey("key-456")
            }
        val lwNodeB =
            Concept(lwLang, "NodeA", "id-789").apply {
                setKey("key-789")
            }
        val lwEnum =
            Enumeration(lwLang, "MyEnum", "id-000").apply {
                setKey("key-000")
            }
        val kolasuLanguage =
            KolasuLanguage("LangA").apply {
                addClass(LWRoot::class)
            }
        val lie = LionWebLanguageConverter()
        lie.associateLanguages(lwLang, kolasuLanguage)
        assertEquals(LWRoot::class, lie.correspondingStartlasuClass(lwRoot))
        assertEquals(LWNodeA::class, lie.correspondingStartlasuClass(lwNodeA))
        assertEquals(LWNodeB::class, lie.correspondingStartlasuClass(lwNodeB))
    }
}
