package com.strumenta.starlasu.nextgen.generators

import com.github.ajalt.clikt.core.CliktCommand
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
import com.strumenta.starlasu.base.v1.ASTLanguageV1
import io.lionweb.LionWebVersion
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
import io.lionweb.serialization.AbstractSerialization
import io.lionweb.serialization.JsonSerialization
import io.lionweb.serialization.ProtoBufSerialization
import io.lionweb.serialization.SerializationProvider
import java.io.File

class ClassesGeneratorCommand : CliktCommand("classgen") {
    val dependenciesFiles: List<File> by option("--dependency", help = "Dependency file to generate classes for")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeFile = true)
        .multiple(required = false)
    val languageFiles: List<File> by option("--language", help = "Language file to generate classes for")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeFile = true)
        .multiple(required = true)
    val outputDir: File by option("--output", help = "Output directory for generated classes")
        .file(mustExist = false, canBeDir = true, mustBeReadable = false, canBeFile = false)
        .default(File("out"))
    val lwVersion: LionWebVersion by option("--lwversion", help = "LionWeb version to generate classes for")
        .enum<LionWebVersion>(ignoreCase = true)
        .default(LionWebVersion.v2023_1)
    val names: List<String>
        by option("--name", help = "Name of the generated language").multiple(required = false)

    override fun run() {
        val extensions = (languageFiles.map { it.extension } + dependenciesFiles.map { it.extension }).toSet()
        if (extensions.size != 1) {
            throw IllegalArgumentException("All language files and dependencies must have the same extension")
        }
        val extension = extensions.first().lowercase()
        val serialization: AbstractSerialization =
            when (extension) {
                "json" -> SerializationProvider.getStandardJsonSerialization(lwVersion)
                "pb" -> SerializationProvider.getStandardProtoBufSerialization(lwVersion)
                else -> throw IllegalArgumentException("Unsupported language extension: $extension")
            }
        serialization.registerLanguage(ASTLanguageV1.getLanguage())

        fun loadLanguage(file: File): Language {
            val language =
                when (serialization) {
                    is ProtoBufSerialization -> {
                        val nodes = serialization.deserializeToNodes(file)
                        val languages = nodes.filterIsInstance(Language::class.java)
                        if (languages.size != 1) {
                            throw IllegalArgumentException("Expected exactly one language in language file: $file")
                        }
                        languages.first()
                    }
                    is JsonSerialization -> {
                        serialization.loadLanguage(file)
                    }
                    else -> throw UnsupportedOperationException("Serialization not supported for language file: $file")
                }
            serialization.registerLanguage(language)
            return language
        }
        dependenciesFiles.forEach { dependencyFile ->
            loadLanguage(dependencyFile)
        }
        val languages =
            languageFiles.map { languageFile ->
                loadLanguage(languageFile)
            }
        languages.forEachIndexed { index, language ->
            val overriddenName = names.getOrNull(index)
            generateLanguage(language, overriddenName)
        }
    }

    private fun generateLanguage(
        language: Language,
        overridenName: String?,
    ) {
        echo("Generating classes for language ${language.name}")
        echo("-------------------------------------------------------------------")
        echo()
        val languageType = ClassName(language.name!!, overridenName ?: language.name!!)
        language.elements.forEach { element ->
            echo(" - Generating class for ${element.javaClass.simpleName} ${element.name}")
            val fileSpec: FileSpec? =
                when (element) {
                    is Enumeration -> generateEnumeration(element)
                    is Concept -> generateConcept(element, languageType)
                    is Interface -> generateInterface(element)
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

        val abstractSerialization = ClassName("io.lionweb.serialization", "AbstractSerialization")
        val instantiatorClassifier =
            ClassName("io.lionweb.serialization", "Instantiator", "ClassifierSpecificInstantiator")
        val rpgLanguage = ClassName(packageName, langName)

        val deserializer =
            FunSpec
                .builder("registerDeserializersFor${langName.capitalize()}")
                .receiver(abstractSerialization)

        language.elements.forEach { element ->
            if (element is Concept && !element.isAbstract) {
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
            } else if (element is Enumeration) {
                val enumClass = ClassName(packageName, element.name!!)
                deserializer.addStatement(
                    "primitiveValuesSerialization.registerEnumClass(%T::class.java, %T.${element.name!!.decapitalize()})",
                    enumClass,
                    ClassName(packageName, langName),
                )
            } else if (element is PrimitiveType) {
                val primitiveTypeClass = ClassName(packageName, element.name!!)
                deserializer.addStatement(
                    "%L.registerDeserializer(%T.${element.name!!.decapitalize()}.id!!) { %T(it) }",
                    "primitiveValuesSerialization",
                    ClassName(packageName, langName),
                    primitiveTypeClass,
                )
            }
        }

        val fileSpec =
            FileSpec
                .builder(packageName + ".serialization", deserializerName)
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
                        .mutable(true) // makes it 'var'
                        .initializer("value") // links property to constructor parameter
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
                        -> ClassName("com.strumenta.starlasulw", "NamedLW")
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
                            ClassName("io.lionweb.kotlin", "SpecificReferenceValue")
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
                            ClassName(
                                "com.strumenta.starlasulw",
                                "StarlasuLWBaseASTNode",
                            )
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
        languageType: ClassName,
    ): FileSpec {
        val packageName = concept.language!!.name!!
        val baseNode = ClassName("io.lionweb.kotlin", "BaseNode")
        val conceptType =
            TypeSpec
                .classBuilder(concept.name!!)
                .addModifiers(KModifier.PUBLIC, KModifier.OPEN)
        if (concept.isAbstract) {
            conceptType.addModifiers(KModifier.ABSTRACT)
        }
        if (concept.extendedConcept != null) {
            val superConcept = concept.extendedConcept
            when {
                superConcept == null -> conceptType.superclass(baseNode)
                superConcept == ASTLanguageV1.getASTNode() ->
                    conceptType.superclass(
                        ClassName("com.strumenta.starlasulw", "StarlasuLWBaseASTNode"),
                    )
                // else -> conceptType.superclass(dynamicNode)
                concept.language == superConcept.language ->
                    conceptType.superclass(
                        ClassName(packageName, superConcept.name!!),
                    )
                else -> TODO()
            }
        }
        concept.implemented.forEach { interf ->
            when {
                concept.language == interf.language ->
                    conceptType.addSuperinterface(
                        ClassName(packageName, interf.name!!),
                    )
                interf == ASTLanguageV1.getStatement() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "StatementLW"),
                    )
                interf == ASTLanguageV1.getExpression() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "ExpressionLW"),
                    )
                interf == ASTLanguageV1.getParameter() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "ParameterLW"),
                    )
                interf == ASTLanguageV1.getBehaviorDeclaration() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "BehaviorDeclarationLW"),
                    )
                interf == ASTLanguageV1.getPlaceholderElement() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "PlaceholderElementLW"),
                    )
                interf == ASTLanguageV1.getEntityDeclaration() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "EntityDeclarationLW"),
                    )
                interf == ASTLanguageV1.getDocumentation() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "DocumentationLW"),
                    )
                interf ==
                    LionCoreBuiltins.getINamed(
                        LionWebVersion.v2023_1,
                    )
                -> conceptType.addSuperinterface(ClassName("com.strumenta.starlasulw", "NamedLW"))
                interf == ASTLanguageV1.getTypeAnnotation() ->
                    conceptType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "TypeAnnotationLW"),
                    )
                else -> TODO()
            }
            interf.features.forEach { feature ->
                generateFeature(conceptType, packageName, concept, feature, overridden = true)
            }
        }

        val getClassifierFun =
            FunSpec
                .builder("getClassifier")
                .addModifiers(KModifier.OVERRIDE)
                .returns(ClassName("io.lionweb.language", "Concept")) // use the actual return type if known
                .addCode("return %T.${concept.name!!.decapitalize()}\n", languageType)
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

    private fun generateInterface(interf: Interface): FileSpec {
        val packageName = interf.language!!.name!!
        val interfaceType =
            TypeSpec
                .interfaceBuilder(interf.name!!)
                .addModifiers(KModifier.PUBLIC)
        interf.extendedInterfaces.forEach { superInterf ->
            when {
                superInterf.language == interf.language ->
                    interfaceType.addSuperinterface(
                        ClassName(packageName, interf.name!!),
                    )
                superInterf == ASTLanguageV1.getStatement() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "StatementLW"),
                    )
                superInterf == ASTLanguageV1.getExpression() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "ExpressionLW"),
                    )
                superInterf == ASTLanguageV1.getParameter() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "ParameterLW"),
                    )
                superInterf == ASTLanguageV1.getBehaviorDeclaration() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "BehaviorDeclarationLW"),
                    )
                superInterf == ASTLanguageV1.getPlaceholderElement() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "PlaceholderElementLW"),
                    )
                superInterf == ASTLanguageV1.getEntityDeclaration() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "EntityDeclarationLW"),
                    )
                superInterf == ASTLanguageV1.getDocumentation() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "DocumentationLW"),
                    )
                superInterf ==
                    LionCoreBuiltins.getINamed(
                        LionWebVersion.v2023_1,
                    )
                -> interfaceType.addSuperinterface(ClassName("com.strumenta.starlasulw", "NamedLW"))
                superInterf == ASTLanguageV1.getTypeAnnotation() ->
                    interfaceType.addSuperinterface(
                        ClassName("com.strumenta.starlasulw", "TypeAnnotationLW"),
                    )
                else -> TODO()
            }
        }
        if (interf.extendedInterfaces.isEmpty()) {
            interfaceType.addSuperinterface(ClassName("io.lionweb.model", "Node"))
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
