package com.strumenta.starlasu.lwstubs.generators

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.strumenta.starlasu.base.v1.ASTLanguageV1
import com.strumenta.starlasu.lwstubs.model.NamedLW
import com.strumenta.starlasu.lwstubs.model.StarlasuLWBaseASTNode
import io.lionweb.LionWebVersion
import io.lionweb.kotlin.BaseNode
import io.lionweb.kotlin.SpecificReferenceValue
import io.lionweb.language.Classifier
import io.lionweb.language.Concept
import io.lionweb.language.Containment
import io.lionweb.language.Enumeration
import io.lionweb.language.Feature
import io.lionweb.language.Interface
import io.lionweb.language.Language
import io.lionweb.language.LionCoreBuiltins
import io.lionweb.language.PrimitiveType
import io.lionweb.language.Property
import io.lionweb.language.Reference
import io.lionweb.model.Node
import io.lionweb.serialization.AbstractSerialization
import java.io.File

class ClassesGeneratorCommand : AbstractGeneratorCommand("classgen") {
    override val dependenciesFiles: List<File> by option(
        "--dependency",
        help = "Dependency file to generate classes for",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeFile = true)
        .multiple(required = false)
    override val languageFiles: List<File> by option("--language", help = "Language file to generate classes for")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeFile = true)
        .multiple(required = true)
    override val outputDir: File by option("--output", help = "Output directory for generated classes")
        .file(mustExist = false, canBeDir = true, mustBeReadable = false, canBeFile = false)
        .default(File("out"))
    override val lwVersion: LionWebVersion by option("--lwversion", help = "LionWeb version to generate classes for")
        .enum<LionWebVersion>(ignoreCase = true)
        .default(LionWebVersion.v2023_1)
    override val names: List<String>
        by option("--name", help = "Name of the generated language").multiple(required = false)

    override fun processLanguage(
        language: Language,
        overridenName: String?,
    ) {
        echo("Generating classes for language ${language.name}")
        echo("-------------------------------------------------------------------")
        echo()
        val languageType = ClassName(language.name!!, overridenName ?: language.name!!)
        val generationContext = GenerationContext(language.name!!, language, languageType)
        language.elements.forEach { element ->
            echo(" - Generating class for ${element.javaClass.simpleName} ${element.name}")
            val fileSpec: FileSpec? =
                when (element) {
                    is Enumeration -> generateEnumeration(element)
                    is Concept -> generateConcept(element, generationContext)
                    is Interface -> generateInterface(element, generationContext)
                    is PrimitiveType -> generatePrimitiveType(element)
                    else -> TODO("Not yet implemented for element type ${element::class.simpleName}")
                }
            fileSpec?.let { save(it) }
        }
        generateDeserializer(language, overridenName)
    }

    private fun generateDeserializer(
        language: Language,
        overridenName: String?,
    ) {
        val packageName = language.name!!
        val langName = overridenName ?: packageName.split(".").last()
        val deserializerName = langName.capitalize() + "Deserializer"

        val abstractSerialization = AbstractSerialization::class.asClassName()
        val instantiatorClassifier =
            ClassName("io.lionweb.serialization", "Instantiator", "ClassifierSpecificInstantiator")
        val rpgLanguage = ClassName(packageName, langName)

        val deserializer =
            FunSpec
                .builder("registerDeserializersFor${langName.capitalize()}")
                .receiver(abstractSerialization)

        language.elements.forEach { element ->
            when (element) {
                is Concept if !element.isAbstract -> {
                    val astClass = ClassName(packageName, element.name!!)

                    deserializer.addStatement(
                        """
                        instantiator.registerCustomDeserializer(%T.${element.name!!.decapitalize()}.id,
                            %T<%T> { classifier, serializedClassifierInstance, deserializedNodesByID, propertiesValues ->
                                %T().apply { setID(serializedClassifierInstance.id!!) }
                            })
                        """.trimIndent(),
                        rpgLanguage,
                        instantiatorClassifier,
                        astClass,
                        astClass,
                    )
                }

                is Enumeration -> {
                    val enumClass = ClassName(packageName, element.name!!)
                    deserializer.addStatement(
                        "primitiveValuesSerialization.registerEnumClass(%T::class.java, %T.${element.name!!.decapitalize()})",
                        enumClass,
                        ClassName(packageName, langName),
                    )
                }

                is PrimitiveType -> {
                    val primitiveTypeClass = ClassName(packageName, element.name!!)

                    deserializer.addStatement(
                        "%L.registerDeserializer(%T.${element.name!!.decapitalize()}.id!!) { it?.let { %T(it) } }",
                        "primitiveValuesSerialization",
                        ClassName(packageName, langName),
                        primitiveTypeClass,
                    )
                }
            }
        }

        val fileSpec =
            FileSpec
                .builder("$packageName.serialization", deserializerName)
                .addFunction(deserializer.build())
                .build()
        save(fileSpec)
    }

    private fun generatePrimitiveType(primitiveType: PrimitiveType): FileSpec {
        val packageName = primitiveType.language!!.name!!
        val primitiveTypeClass =
            TypeSpec
                .classBuilder(primitiveType.name!!)
                .primaryConstructor(
                    FunSpec
                        .constructorBuilder()
                        .addParameter("value", String::class)
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("value", String::class)
                        .mutable(true)
                        .initializer("value")
                        .build(),
                )
        return FileSpec
            .builder(packageName, primitiveType.name!!)
            .addType(primitiveTypeClass.build())
            .build()
    }

    private fun save(fileSpec: FileSpec) {
        fileSpec.writeTo(outputDir)
    }

    private fun generateFeature(
        typeSpec: TypeSpec.Builder,
        packageName: String,
        container: Classifier<*>,
        feature: Feature<*>,
        overridden: Boolean = false,
    ) {
        when (feature) {
            is Property -> {
                val featureType = feature.type!!
                val baseType: TypeName =
                    when {
                        featureType.language == container.language -> ClassName(packageName, featureType.name!!)
                        featureType ==
                            LionCoreBuiltins.getString(
                                LionWebVersion.v2023_1,
                            )
                        -> ClassName("kotlin", "String")
                        featureType == LionCoreBuiltins.getInteger(LionWebVersion.v2023_1) -> ClassName("kotlin", "Int")
                        featureType ==
                            LionCoreBuiltins.getBoolean(
                                LionWebVersion.v2023_1,
                            )
                        -> ClassName("kotlin", "Boolean")
                        featureType == ASTLanguageV1.getChar() -> ClassName("kotlin", "Char")
                        else -> TODO()
                    }
                val prop =
                    PropertySpec
                        .builder(
                            feature.name!!,
                            baseType.copy(nullable = true),
                        ).mutable(true)
                if (overridden) {
                    prop.addModifiers(KModifier.OVERRIDE)
                }
                if (container is Concept) {
                    prop.delegate("property(%S)", feature.name!!)
                }
                typeSpec.addProperty(prop.build())
            }
            is Reference -> {
                val featureType = feature.type!!
                val baseType: TypeName =
                    when {
                        featureType.language == container.language ->
                            ClassName(
                                packageName,
                                featureType.name!!,
                            )
                        featureType ==
                            LionCoreBuiltins.getINamed(
                                LionWebVersion.v2023_1,
                            )
                        -> NamedLW::class.asClassName()
                        else -> TODO()
                    }
                if (feature.isMultiple) {
                    val prop =
                        PropertySpec.builder(
                            feature.name!!,
                            ClassName("kotlin.collections", "MutableList")
                                .parameterizedBy(baseType),
                        )
                    if (container is Concept) {
                        prop.initializer(
                            "multipleReference<%T>(%S)",
                            baseType,
                            feature.name!!,
                        )
                    }

                    if (overridden) {
                        prop.addModifiers(KModifier.OVERRIDE)
                    }
                    typeSpec.addProperty(prop.build())
                } else {
                    val prop =
                        PropertySpec.builder(
                            feature.name!!,
                            SpecificReferenceValue::class
                                .asClassName()
                                .parameterizedBy(baseType)
                                .copy(nullable = true),
                        )
                    if (container is Concept) {
                        prop.delegate("singleReference(%S)", feature.name!!)
                    }
                    if (overridden) {
                        prop.addModifiers(KModifier.OVERRIDE)
                    }
                    typeSpec.addProperty(prop.build())
                }
            }
            is Containment -> {
                val featureType = feature.type!!
                val baseType: TypeName =
                    when {
                        featureType.language == container.language -> ClassName(packageName, featureType.name!!)
                        featureType == ASTLanguageV1.getASTNode() ->
                            StarlasuLWBaseASTNode::class.asClassName()
                        else -> TODO()
                    }
                if (feature.isMultiple) {
                    val prop =
                        PropertySpec.builder(
                            feature.name!!,
                            ClassName("kotlin.collections", "MutableList")
                                .parameterizedBy(baseType),
                        )
                    if (container is Concept) {
                        prop.initializer(
                            "multipleContainment<%T>(%S)",
                            baseType,
                            feature.name!!,
                        )
                    }
                    if (overridden) {
                        prop.addModifiers(KModifier.OVERRIDE)
                    }
                    typeSpec.addProperty(prop.build())
                } else {
                    val prop =
                        PropertySpec.builder(
                            feature.name!!,
                            baseType.copy(nullable = true),
                        )
                    if (container is Concept) {
                        prop.delegate("singleContainment(%S)", feature.name!!)
                    }
                    if (overridden) {
                        prop.addModifiers(KModifier.OVERRIDE)
                    }
                    typeSpec.addProperty(prop.build())
                }
            }
        }
    }

    private fun generateConcept(
        concept: Concept,
        generationContext: GenerationContext,
    ): FileSpec {
        val packageName = concept.language!!.name!!
        val baseNode = BaseNode::class.asClassName()
        val conceptType =
            TypeSpec
                .classBuilder(concept.name!!)
                .addModifiers(KModifier.PUBLIC, KModifier.OPEN)
        if (concept.isAbstract) {
            conceptType.addModifiers(KModifier.ABSTRACT)
        }
        if (concept.extendedConcept != null) {
            conceptType.superclass(generationContext.classifierToClassName(concept.extendedConcept) ?: baseNode)
        }
        concept.implemented.forEach { interf ->
            conceptType.addSuperinterface(
                generationContext.classifierToClassName(interf) ?: throw IllegalStateException(),
            )
            interf.features.forEach { feature ->
                generateFeature(conceptType, packageName, concept, feature, overridden = true)
            }
        }

        val getClassifierFun =
            FunSpec
                .builder("getClassifier")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Concept::class.asClassName()) // use the actual return type if known
                .addCode("return %T.${concept.name!!.decapitalize()}\n", generationContext.languageType)
                .build()
        conceptType.addFunction(getClassifierFun)

        concept.features.forEach { feature ->
            generateFeature(conceptType, packageName, concept, feature, overridden = false)
        }

        return FileSpec
            .builder(packageName, concept.name!!)
            .addType(conceptType.build())
            .build()
    }

    private fun generateInterface(
        interf: Interface,
        generationContext: GenerationContext,
    ): FileSpec {
        val packageName = interf.language!!.name!!
        val interfaceType =
            TypeSpec
                .interfaceBuilder(interf.name!!)
                .addModifiers(KModifier.PUBLIC)
        interf.extendedInterfaces.forEach { superInterf ->
            interfaceType.addSuperinterface(
                generationContext.classifierToClassName(superInterf) ?: throw IllegalStateException(),
            )
        }
        if (interf.extendedInterfaces.isEmpty()) {
            interfaceType.addSuperinterface(Node::class.asClassName())
        }

        interf.features.forEach { feature ->
            generateFeature(interfaceType, packageName, interf, feature, overridden = false)
        }

        return FileSpec
            .builder(packageName, interf.name!!)
            .addType(interfaceType.build())
            .build()
    }

    private fun generateEnumeration(enumeration: Enumeration): FileSpec {
        val enumType =
            TypeSpec
                .enumBuilder(enumeration.name!!)
                .addModifiers(KModifier.PUBLIC)

        for (literal in enumeration.literals) {
            enumType.addEnumConstant(literal.name!!)
        }

        return FileSpec
            .builder(enumeration.language!!.name!!, enumeration.name!!)
            .addType(enumType.build())
            .build()
    }
}

fun main(args: Array<String>) {
    ClassesGeneratorCommand().main(args)
}
