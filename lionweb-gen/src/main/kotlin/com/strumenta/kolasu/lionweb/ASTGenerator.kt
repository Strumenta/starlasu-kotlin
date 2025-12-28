package com.strumenta.kolasu.lionweb

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.strumenta.kolasu.model.Named
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.NodeType
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.starlasu.base.v1.ASTLanguage
import io.lionweb.language.Classifier
import io.lionweb.language.Concept
import io.lionweb.language.Containment
import io.lionweb.language.DataType
import io.lionweb.language.Enumeration
import io.lionweb.language.Feature
import io.lionweb.language.Interface
import io.lionweb.language.LionCoreBuiltins
import io.lionweb.language.Property
import io.lionweb.language.Reference
import org.jetbrains.kotlin.konan.file.File
import java.lang.UnsupportedOperationException

data class KotlinFile(val path: String, val code: String)

/**
 * This class generates Kotlin code for a given LionWeb Language.
 */
class ASTGenerator(val packageName: String, val language: LWLanguage) {

    fun generateClasses(existingKotlinClasses: Set<String> = emptySet()): Set<KotlinFile> {
        val fileSpecBuilder = FileSpec.builder(packageName, "${language.name}AST.kt")
        language.elements.forEach { element ->
            when (element) {
                is Concept -> {
                    val typeSpec = TypeSpec.classBuilder(element.name!!)
                    typeSpec.addAnnotation(
                        AnnotationSpec.builder(LionWebAssociation::class.java)
                            .addMember("key = \"${element.key}\"")
                            .build()
                    )
                    val fqName = "$packageName.${element.name!!}"
                    if (fqName in existingKotlinClasses) {
                        println("    Skipping ${element.name} as a Kotlin class with that name already exist")
                        fileSpecBuilder.addFileComment(
                            "Skipping ${element.name} as a Kotlin class with that name already exist"
                        )
                    } else {
                        if (element.isAbstract) {
                            typeSpec.modifiers.add(KModifier.SEALED)
                        }
                        if (element.allFeatures().isNotEmpty() && !element.isAbstract) {
                            val hasSubclasses = language.elements.filterIsInstance<Concept>().any {
                                it.extendedConcept == element
                            }
                            // if it has subclasses it needs to be open, to be extended
                            if (hasSubclasses) {
                                typeSpec.modifiers.add(KModifier.OPEN)
                            } else {
                                typeSpec.modifiers.add(KModifier.DATA)
                            }
                        }
                        if (element.allFeatures().isEmpty() && !element.isAbstract) {
                            val hasSubclasses = language.elements.filterIsInstance<Concept>().any {
                                it.extendedConcept == element
                            }
                            if (hasSubclasses) {
                                typeSpec.modifiers.add(KModifier.OPEN)
                            }
                        }
                        if (element.extendedConcept == null) {
                            typeSpec.superclass(ClassName.bestGuess(Node::class.qualifiedName!!))
                        } else {
                            typeSpec.superclass(typeName(element.extendedConcept!!))
                            (element.extendedConcept as Concept).allFeatures().forEach {
                                if (it !in ASTLanguage.getInstance().astNode.features) {
                                    typeSpec.addSuperclassConstructorParameter(it.name!!)
                                }
                            }
                        }
                        element.implemented.forEach {
                            typeSpec.addSuperinterface(typeName(it))
                        }
                        val constructor = FunSpec.constructorBuilder()
                        element.inheritedFeatures().forEach { feature ->
                            if (feature in ASTLanguage.getInstance().astNode.features) {
                                // skip it
                            } else {
                                processFeature(feature, constructor, typeSpec, true, element.isAbstract)
                            }
                        }
                        element.features.forEach { feature ->
                            if (feature in ASTLanguage.getInstance().astNode.features) {
                                // skip it
                            } else {
                                processFeature(feature, constructor, typeSpec, false, element.isAbstract)
                            }
                        }
                        if (constructor.parameters.isNotEmpty()) {
                            typeSpec.primaryConstructor(constructor.build())
                        }
                        fileSpecBuilder.addType(typeSpec.build())
                    }
                }
                is Enumeration -> {
                    val typeSpec = TypeSpec.enumBuilder(element.name!!)
                    typeSpec.addAnnotation(
                        AnnotationSpec.builder(LionWebAssociation::class.java)
                            .addMember("key = \"${element.key}\"")
                            .build()
                    )
                    element.literals.forEach {
                        typeSpec.addEnumConstant(it.name!!)
                    }
                    fileSpecBuilder.addType(typeSpec.build())
                }
                is Interface -> {
                    val typeSpec = TypeSpec.interfaceBuilder(element.name!!)
                    typeSpec.addAnnotation(
                        AnnotationSpec.builder(LionWebAssociation::class.java)
                            .addMember("key = \"${element.key}\"")
                            .build()
                    )
                    typeSpec.addAnnotation(
                        AnnotationSpec.builder(NodeType::class.java)
                            .build()
                    )
                    val fqName = "$packageName.${element.name!!}"
                    if (fqName in existingKotlinClasses) {
                        println("    Skipping ${element.name} as a Kotlin interface with that name already exist")
                        fileSpecBuilder.addFileComment(
                            "Skipping ${element.name} as a Kotlin interface with that name already exist"
                        )
                    } else {
                        element.extendedInterfaces.forEach {
                            typeSpec.addSuperinterface(typeName(it))
                        }
                        element.features.forEach { feature ->
                            processFeature(feature, null, typeSpec, inherited = false, sealed = false)
                        }
                        fileSpecBuilder.addType(typeSpec.build())
                    }
                }
                else -> throw UnsupportedOperationException(
                    "We do not know how to convert to Kolasu this element: $element"
                )
            }
        }
        val path = if (packageName.isNullOrEmpty()) {
            "AST.kt"
        } else {
            packageName.split(".")
                .joinToString(File.separator) + File.separator + "AST.kt"
        }
        val file = KotlinFile(path = path, fileSpecBuilder.build().toString())
        return setOf(file)
    }

    private fun processFeature(
        feature: Feature<*>,
        constructor: FunSpec.Builder?,
        typeSpec: TypeSpec.Builder,
        inherited: Boolean,
        sealed: Boolean
    ) {
        val modifiers = mutableListOf<KModifier>()
        if (inherited) {
            modifiers.add(KModifier.OVERRIDE)
        }
        if (sealed) {
            modifiers.add(KModifier.OPEN)
        }
        when (feature) {
            is Property -> {
                val type = typeName(feature.type!!)
                constructor?.addParameter(feature.name!!, type)
                typeSpec.addProperty(
                    PropertySpec.builder(feature.name!!, type)
                        .addModifiers(modifiers)
                        .mutable(true).initializer(feature.name!!).build()
                )
            }

            is Containment -> {
                var type = typeName(feature.type!!)
                if (feature.isMultiple) {
                    type =
                        ClassName.bestGuess("kotlin.collections.MutableList").parameterizedBy(type)
                }
                constructor?.addParameter(feature.name!!, type)
                typeSpec.addProperty(
                    PropertySpec.builder(feature.name!!, type)
                        .addModifiers(modifiers)
                        .mutable(true).initializer(feature.name!!).build()
                )
            }

            is Reference -> {
                var type = typeName(feature.type!!)
                type =
                    ClassName.bestGuess(ReferenceByName::class.qualifiedName!!).parameterizedBy(type)
                if (feature.isOptional) {
                    type = type.copy(nullable = true)
                }
                constructor?.addParameter(feature.name!!, type)
                typeSpec.addProperty(
                    PropertySpec.builder(feature.name!!, type)
                        .addModifiers(modifiers)
                        .mutable(true).initializer(feature.name!!).build()
                )
            }
        }
    }

    private fun typeName(classifier: Classifier<*>): TypeName {
        return when {
            classifier.id == ASTLanguage.getInstance().astNode.id -> {
                Node::class.java.asTypeName()
            }
            classifier.id == LionCoreBuiltins.getNode(LIONWEB_VERSION_USED_BY_KOLASU).id -> {
                Node::class.java.asTypeName()
            }
            classifier.id == LionCoreBuiltins.getINamed(LIONWEB_VERSION_USED_BY_KOLASU).id -> {
                Named::class.java.asTypeName()
            }
            classifier.id == ASTLanguage.getInstance().position.id -> {
                Position::class.java.asTypeName()
            }
            classifier.language == this.language -> {
                ClassName(packageName, classifier.name!!)
            }
            else -> {
                TODO("Classifier $classifier. ID ${classifier.id}, NODE id: ${LionCoreBuiltins.getNode().id}")
            }
        }
    }

    private fun typeName(dataType: DataType<*>): TypeName {
        return when {
            dataType == LionCoreBuiltins.getString(LIONWEB_VERSION_USED_BY_KOLASU) -> {
                ClassName.bestGuess("kotlin.String")
            }
            dataType == LionCoreBuiltins.getBoolean(LIONWEB_VERSION_USED_BY_KOLASU) -> {
                Boolean::class.java.asTypeName()
            }
            dataType == LionCoreBuiltins.getInteger(LIONWEB_VERSION_USED_BY_KOLASU) -> {
                ClassName.bestGuess("kotlin.Int")
            }
            dataType.language == this.language -> {
                ClassName.bestGuess("$packageName.${dataType.name}")
            }
            else -> {
                TODO("DataType: $dataType")
            }
        }
    }
}
