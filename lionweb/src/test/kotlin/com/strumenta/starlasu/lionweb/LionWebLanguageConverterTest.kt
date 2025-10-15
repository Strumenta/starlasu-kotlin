package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.language.KolasuLanguage
import com.strumenta.starlasu.model.ASTRoot
import com.strumenta.starlasu.model.EntityDeclaration
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.NodeType
import com.strumenta.starlasu.model.ReferenceByName
import io.lionweb.language.LionCoreBuiltins
import io.lionweb.utils.LanguageValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import com.strumenta.starlasu.base.v2.ASTLanguageV2 as ASTLanguage

@ASTRoot
data class SimpleRoot(
    val _id: Int,
    val childrez: MutableList<SimpleDecl>,
) : Node()

sealed class SimpleDecl :
    Node(),
    EntityDeclaration

@ASTRoot(canBeNotRoot = true)
data class SimpleNodeA(
    override val name: String,
    val ref: ReferenceByName<SimpleNodeA>,
    val child: SimpleNodeB?,
) : SimpleDecl(),
    Named,
    MyRelevantInterface,
    MyIrrelevantInterface

data class MyNonPartition(
    val roots: MutableList<SimpleRoot>,
) : Node()

interface MyIrrelevantInterface

@NodeType
interface MyRelevantInterface

data class SimpleNodeB(
    val value: String,
) : SimpleDecl()

data class MyNodeWithNullableContainmentLists(
    val children: MutableList<MyNodeWithNullableContainmentLists>?,
) : Node()

class LionWebLanguageConverterTest {
    @Test
    fun exportSimpleLanguage() {
        val kLanguage =
            KolasuLanguage("com.strumenta.SimpleLang").apply {
                addClass(SimpleRoot::class)
            }
        assertEquals(5, kLanguage.astClasses.size)
        val lwLanguage = LionWebLanguageConverter().exportToLionWeb(kLanguage)
        assertEquals("1", lwLanguage.version)
        assertEquals(5, lwLanguage.elements.size)

        val simpleRoot = lwLanguage.getConceptByName("SimpleRoot")!!
        val simpleDecl = lwLanguage.getConceptByName("SimpleDecl")!!
        val simpleNodeA = lwLanguage.getConceptByName("SimpleNodeA")!!
        val simpleNodeB = lwLanguage.getConceptByName("SimpleNodeB")!!
        val myRelevantInterface = lwLanguage.getInterfaceByName("MyRelevantInterface")!!
        assertNull(lwLanguage.getInterfaceByName("MyIrrelevantInterface"))

        assertEquals("SimpleRoot", simpleRoot.name)
        assertSame(lwLanguage, simpleRoot.language)
        assertEquals(listOf(ASTLanguage.getASTNode()), simpleRoot.implemented)
        assertEquals(null, simpleRoot.extendedConcept)
        assertEquals(false, simpleRoot.isAbstract)
        assertEquals(2, simpleRoot.features.size)
        assertEquals(5, simpleRoot.allFeatures().size)

        val simpleRootID = simpleRoot.getPropertyByName("_id")!!
        assertEquals("_id", simpleRootID.name)
        assertEquals(false, simpleRootID.isOptional)
        assertEquals(LionCoreBuiltins.getInteger(LIONWEB_VERSION_USED_BY_KOLASU), simpleRootID.type)

        val simpleRootChildren = simpleRoot.getContainmentByName("childrez")!!
        assertEquals("childrez", simpleRootChildren.name)
        assertEquals(true, simpleRootChildren.isOptional)
        assertEquals(true, simpleRootChildren.isMultiple)
        assertEquals(simpleDecl, simpleRootChildren.type)

        assertEquals("SimpleDecl", simpleDecl.name)
        assertSame(lwLanguage, simpleDecl.language)
        assertEquals(listOf(ASTLanguage.getASTNode(), ASTLanguage.getEntityDeclaration()), simpleDecl.implemented)
        assertEquals(null, simpleRoot.extendedConcept)
        assertEquals(true, simpleDecl.isAbstract)

        assertEquals("SimpleNodeA", simpleNodeA.name)
        assertSame(lwLanguage, simpleNodeA.language)
        assertEquals(simpleDecl, simpleNodeA.extendedConcept)
        assertEquals(
            listOf(ASTLanguage.getASTNode(), ASTLanguage.getEntityDeclaration()),
            simpleNodeA.extendedConcept!!.implemented,
        )
        assertEquals(
            listOf(LionCoreBuiltins.getINamed(LIONWEB_VERSION_USED_BY_KOLASU), myRelevantInterface),
            simpleNodeA.implemented,
        )
        assertEquals(false, simpleNodeA.isAbstract)
        assertEquals(2, simpleNodeA.features.size)
        assertEquals(6, simpleNodeA.allFeatures().size)

        assertEquals(
            true,
            LionCoreBuiltins
                .getINamed(LIONWEB_VERSION_USED_BY_KOLASU)
                .getPropertyByName("name") in simpleNodeA.allFeatures(),
        )

        val simpleNodeARef = simpleNodeA.getReferenceByName("ref")!!
        assertEquals("ref", simpleNodeARef.name)
        assertEquals(false, simpleNodeARef.isOptional)
        assertEquals(false, simpleNodeARef.isMultiple)
        assertEquals(simpleNodeA, simpleNodeARef.type)

        val simpleNodeAChild = simpleNodeA.getContainmentByName("child")!!
        assertEquals("child", simpleNodeAChild.name)
        assertEquals(true, simpleNodeAChild.isOptional)
        assertEquals(false, simpleNodeAChild.isMultiple)
        assertEquals(simpleNodeB, simpleNodeAChild.type)

        assertEquals("SimpleNodeB", simpleNodeB.name)
        assertSame(lwLanguage, simpleNodeB.language)
        assertEquals(simpleDecl, simpleNodeB.extendedConcept)
        assertEquals(false, simpleNodeB.isAbstract)
        assertEquals(1, simpleNodeB.features.size)
        assertEquals(4, simpleNodeB.allFeatures().size)

        val simpleNodeBValue = simpleNodeB.getPropertyByName("value")!!
        assertEquals("value", simpleNodeBValue.name)
        assertEquals(false, simpleNodeBValue.isOptional)
        assertEquals(LionCoreBuiltins.getString(LIONWEB_VERSION_USED_BY_KOLASU), simpleNodeBValue.type)

        val validationResult = LanguageValidator().validate(lwLanguage)
        assertEquals(true, validationResult.isSuccessful, validationResult.issues.toString())
    }

    @Test
    fun exportSimpleLanguageWithPartitions() {
        val kLanguage =
            KolasuLanguage("com.strumenta.SimpleLang").apply {
                addClass(SimpleRoot::class)
                addClass(MyNonPartition::class)
            }
        assertEquals(6, kLanguage.astClasses.size)
        val lwLanguage = LionWebLanguageConverter().exportToLionWeb(kLanguage)
        assertEquals("1", lwLanguage.version)
        assertEquals(6, lwLanguage.elements.size)

        val myNonPartition = lwLanguage.getConceptByName("MyNonPartition")!!
        val simpleRoot = lwLanguage.getConceptByName("SimpleRoot")!!
        val simpleDecl = lwLanguage.getConceptByName("SimpleDecl")!!
        val simpleNodeA = lwLanguage.getConceptByName("SimpleNodeA")!!
        val simpleNodeB = lwLanguage.getConceptByName("SimpleNodeB")!!
        val myRelevantInterface = lwLanguage.getInterfaceByName("MyRelevantInterface")!!
        assertNull(lwLanguage.getInterfaceByName("MyIrrelevantInterface"))

        assertEquals("MyNonPartition", myNonPartition.name)
        assertEquals(false, myNonPartition.isPartition)
        assertSame(lwLanguage, myNonPartition.language)
        assertEquals(listOf(ASTLanguage.getASTNode()), myNonPartition.implemented)
        assertEquals(null, myNonPartition.extendedConcept)
        assertEquals(false, myNonPartition.isAbstract)
        assertEquals(1, myNonPartition.features.size)
        assertEquals(4, myNonPartition.allFeatures().size)

        assertEquals("SimpleRoot", simpleRoot.name)
        assertEquals(false, simpleRoot.isPartition)
        assertSame(lwLanguage, simpleRoot.language)
        assertEquals(listOf(ASTLanguage.getASTNode()), simpleRoot.implemented)
        assertEquals(null, simpleRoot.extendedConcept)
        assertEquals(false, simpleRoot.isAbstract)
        assertEquals(2, simpleRoot.features.size)
        assertEquals(5, simpleRoot.allFeatures().size)

        val simpleRootID = simpleRoot.getPropertyByName("_id")!!
        assertEquals("_id", simpleRootID.name)
        assertEquals(false, simpleRootID.isOptional)
        assertEquals(LionCoreBuiltins.getInteger(LIONWEB_VERSION_USED_BY_KOLASU), simpleRootID.type)

        val simpleRootChildren = simpleRoot.getContainmentByName("childrez")!!
        assertEquals("childrez", simpleRootChildren.name)
        assertEquals(true, simpleRootChildren.isOptional)
        assertEquals(true, simpleRootChildren.isMultiple)
        assertEquals(simpleDecl, simpleRootChildren.type)

        assertEquals("SimpleDecl", simpleDecl.name)
        assertEquals(false, simpleDecl.isPartition)
        assertSame(lwLanguage, simpleDecl.language)
        assertEquals(listOf(ASTLanguage.getASTNode(), ASTLanguage.getEntityDeclaration()), simpleDecl.implemented)

        assertEquals(listOf(ASTLanguage.getASTNode()), simpleRoot.implemented)
        assertEquals(true, simpleDecl.isAbstract)

        assertEquals("SimpleNodeA", simpleNodeA.name)
        assertEquals(false, simpleNodeA.isPartition)
        assertSame(lwLanguage, simpleNodeA.language)
        assertEquals(simpleDecl, simpleNodeA.extendedConcept)
        assertEquals(
            listOf(LionCoreBuiltins.getINamed(LIONWEB_VERSION_USED_BY_KOLASU), myRelevantInterface),
            simpleNodeA.implemented,
        )
        assertEquals(false, simpleNodeA.isAbstract)
        assertEquals(2, simpleNodeA.features.size)
        assertEquals(6, simpleNodeA.allFeatures().size)

        assertEquals(
            true,
            LionCoreBuiltins
                .getINamed(LIONWEB_VERSION_USED_BY_KOLASU)
                .getPropertyByName("name") in simpleNodeA.allFeatures(),
        )

        val simpleNodeARef = simpleNodeA.getReferenceByName("ref")!!
        assertEquals("ref", simpleNodeARef.name)
        assertEquals(false, simpleNodeARef.isOptional)
        assertEquals(false, simpleNodeARef.isMultiple)
        assertEquals(simpleNodeA, simpleNodeARef.type)

        val simpleNodeAChild = simpleNodeA.getContainmentByName("child")!!
        assertEquals("child", simpleNodeAChild.name)
        assertEquals(true, simpleNodeAChild.isOptional)
        assertEquals(false, simpleNodeAChild.isMultiple)
        assertEquals(simpleNodeB, simpleNodeAChild.type)

        assertEquals("SimpleNodeB", simpleNodeB.name)
        assertEquals(false, simpleNodeB.isPartition)
        assertSame(lwLanguage, simpleNodeB.language)
        assertEquals(simpleDecl, simpleNodeB.extendedConcept)
        assertEquals(false, simpleNodeB.isAbstract)
        assertEquals(1, simpleNodeB.features.size)
        assertEquals(4, simpleNodeB.allFeatures().size)

        val simpleNodeBValue = simpleNodeB.getPropertyByName("value")!!
        assertEquals("value", simpleNodeBValue.name)
        assertEquals(false, simpleNodeBValue.isOptional)
        assertEquals(LionCoreBuiltins.getString(LIONWEB_VERSION_USED_BY_KOLASU), simpleNodeBValue.type)

        val validationResult = LanguageValidator().validate(lwLanguage)
        assertEquals(true, validationResult.isSuccessful, validationResult.issues.toString())
    }

    @Test(expected = RuntimeException::class)
    fun conversionOfNullableContainmentListIsForbidden() {
        val kLanguage =
            KolasuLanguage("com.strumenta.SimpleLang").apply {
                addClass(MyNodeWithNullableContainmentLists::class)
            }
        val lwLanguage = LionWebLanguageConverter().exportToLionWeb(kLanguage)
    }

    @Test
    fun exportEnumLiterals() {
        val converter = LionWebLanguageConverter()
        val lwLanguage =
            converter.exportToLionWeb(
                KolasuLanguage("myLanguage").apply {
                    addClass(NodeWithEnum::class)
                },
            )
        val enumeration = lwLanguage.getEnumerationByName("AnEnum") ?: throw IllegalStateException()
        assertEquals("AnEnum", enumeration.name)
        assertEquals(3, enumeration.literals.size)
        assertEquals("FOO", enumeration.literals[0].name)
        assertEquals("BAR", enumeration.literals[1].name)
        assertEquals("ZUM", enumeration.literals[2].name)
    }
}
