package com.strumenta.starlasu.nextgen.generators

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.strumenta.starlasu.base.v1.ASTLanguageV1
import io.lionweb.LionWebVersion
import io.lionweb.language.Classifier
import io.lionweb.language.Concept
import io.lionweb.language.Containment
import io.lionweb.language.Enumeration
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
import kotlin.collections.associateWith
import kotlin.collections.forEach

class LanguagesGeneratorCommand : CliktCommand("langgen") {
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
        languageFiles.forEachIndexed { index, languageFile ->
            val overriddenName = names.getOrNull(index)
            val language = loadLanguage(languageFile)
            generateLanguage(language, overriddenName)
        }
    }

    private fun generateLanguage(
        language: Language,
        overriddenName: String? = null,
    ) {
        echo("Generating classes for language ${language.name}")
        echo("-------------------------------------------------------------------")
        echo()
        val langClassName =
            overriddenName ?: language.name!!
                .split(".")
                .last()
                .capitalize() + "Language"
        val packageName =
            language.name!!
                .split(".")
                .dropLast(1)
                .joinToString(".")
        val langInit = CodeBlock.builder()
        langInit.addStatement("setID(\"${language.id!!}\")")
        langInit.addStatement("setKey(\"${language.key!!}\")")
        langInit.addStatement("setVersion(\"${language.version!!}\")")

        val langClassBuilder =
            TypeSpec
                .objectBuilder(langClassName)
                .superclass(ClassName("io.lionweb.language", "Language"))
                .addSuperclassConstructorParameter("%T.v2023_1", ClassName("io.lionweb", "LionWebVersion"))

        val dataTypeCreator = FunSpec.builder("createDataTypes").addModifiers(KModifier.PRIVATE)
        val dataTypeCreatorCode = CodeBlock.builder()

        language.primitiveTypes.forEach { primitiveType ->
            createPrimitiveType(primitiveType, dataTypeCreatorCode, langClassBuilder)
        }
        language.elements.filterIsInstance(Enumeration::class.java).forEach { enumeration ->
            createEnumeration(enumeration, dataTypeCreatorCode, langClassBuilder)
        }
        langInit.addStatement("createDataTypes()")
        dataTypeCreator.addCode(dataTypeCreatorCode.build())
        langClassBuilder.addFunction(dataTypeCreator.build())

        val classifiers = language.elements.filterIsInstance(io.lionweb.language.Classifier::class.java)
        createClassifiers(classifiers, langInit, langClassBuilder, packageName)

        val langClass =
            langClassBuilder
                .addInitializerBlock(langInit.build())
                .build()
        val fileSpec =
            FileSpec
                .builder(language!!.name!!, langClassName)
                .addType(langClass)
                .build()
        save(fileSpec)
    }

    private fun createEnumeration(
        enumeration: Enumeration,
        langInit: CodeBlock.Builder,
        langClassBuilder: TypeSpec.Builder,
    ) {
        val varName = enumeration.name!!.decapitalize()
        langClassBuilder.addProperty(
            PropertySpec
                .builder(varName, ClassName("io.lionweb.language", "Enumeration"), KModifier.LATEINIT)
                .mutable(true)
                .build(),
        )
        langInit.addStatement("$varName = Enumeration()")
        langInit.addStatement("$varName.setID(\"${enumeration.id!!}\")")
        langInit.addStatement("$varName.setName(\"${enumeration.name!!}\")")
        langInit.addStatement("$varName.setKey(\"${enumeration.key!!}\")")
        enumeration.literals.forEach { literal ->
            langInit.addStatement(
                "%L.addLiteral(%T(%L, %S).setKey(%S))",
                varName,
                ClassName("io.lionweb.language", "EnumerationLiteral"),
                varName,
                literal.name!!,
                literal.key!!,
            )
        }
        langInit.addStatement("addElement($varName)")
    }

    fun sortTopologically(classifiers: Set<Classifier<*>>): List<Classifier<*>> {
        val adj = classifiers.associateWith { mutableSetOf<Classifier<*>>() }.toMutableMap()
        val indegree = classifiers.associateWith { 0 }.toMutableMap()

        for (n in classifiers) {
            for (p in n.directAncestors().filter { it in classifiers }) {
                if (adj.getValue(p).add(n)) {
                    indegree[n] = indegree.getValue(n) + 1
                }
            }
        }

        val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys.sortedBy { it.name })
        val result = mutableListOf<Classifier<*>>()

        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            result += u
            for (v in adj.getValue(u)) {
                indegree[v] = indegree.getValue(v) - 1
                if (indegree.getValue(v) == 0) queue.addLast(v)
            }
        }

        if (result.size != classifiers.size) {
            val cyclic = indegree.filterValues { it > 0 }.keys.joinToString { it.name!! }
            error("Cycle detected among: $cyclic")
        }
        return result
    }

    private fun createClassifiers(
        classifiers: List<Classifier<*>>,
        langInit: CodeBlock.Builder,
        langClassBuilder: TypeSpec.Builder,
        packageName: String,
    ) {
        val sortedClassifiers = sortTopologically(classifiers.toSet())
        sortedClassifiers.forEach { classifier ->
            val varName = classifier.name!!.decapitalize()
            when (classifier) {
                is Concept -> {
                    langClassBuilder.addProperty(varName, ClassName("io.lionweb.language", "Concept"))
                    langInit.addStatement("$varName = Concept()")
                    langInit.addStatement("$varName.setID(\"${classifier.id!!}\")")
                    langInit.addStatement("$varName.setKey(\"${classifier.key!!}\")")
                    langInit.addStatement("addElement($varName)")
                }

                is Interface -> {
                    langClassBuilder.addProperty(varName, ClassName("io.lionweb.language", "Interface"))
                    langInit.addStatement("$varName = Interface()")
                    langInit.addStatement("$varName.setID(\"${classifier.id!!}\")")
                    langInit.addStatement("$varName.setKey(\"${classifier.key!!}\")")
                    langInit.addStatement("addElement($varName)")
                }

                else -> throw IllegalArgumentException("Unsupported classifier type: ${classifier.javaClass.name}")
            }
        }
        populateClassifiers(classifiers, langInit, langClassBuilder, packageName)
    }

    private fun populateClassifiers(
        classifiers: List<Classifier<*>>,
        langInit: CodeBlock.Builder,
        langClassBuilder: TypeSpec.Builder,
        packageName: String,
    ) {
        val sortedClassifiers = sortTopologically(classifiers.toSet())
        sortedClassifiers.forEach { classifier ->
            val varName = classifier.name!!.decapitalize()

            // if (!classifier.features.isEmpty()) {
            val populateMethod = FunSpec.builder("populate${classifier.name!!.capitalize()}")
            val populateMethodCode = CodeBlock.builder()

            when (classifier) {
                is Concept -> {
                    if (classifier.extendedConcept == null) {
                        TODO()
                    } else {
                        if (classifier.extendedConcept == ASTLanguageV1.getASTNode()) {
                            populateMethodCode.addStatement(
                                "$varName.extendedConcept = %T.getASTNode()",
                                ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                            )
                        } else if (classifier.language == classifier.extendedConcept!!.language) {
                            populateMethodCode.addStatement(
                                "$varName.extendedConcept = %L",
                                classifier.extendedConcept!!.name!!.decapitalize(),
                            )
                        } else {
                            TODO()
                        }
                    }
                    classifier.implemented.forEach { implementedClassifier ->
                        if (implementedClassifier == ASTLanguageV1.getExpression()) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.getExpression())",
                                ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                            )
                        } else if (implementedClassifier == ASTLanguageV1.getStatement()) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.getStatement())",
                                ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                            )
                        } else if (implementedClassifier.language == ASTLanguageV1.getLanguage()) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.get${implementedClassifier.name!!.capitalize()}())",
                                ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                            )
                        } else if (implementedClassifier == ASTLanguageV1.getDocumentation()) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.getDocumentation())",
                                ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                            )
                        } else if (implementedClassifier == LionCoreBuiltins.getINamed(LionWebVersion.v2023_1)) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.getINamed(LionWebVersion.v2023_1))",
                                ClassName("io.lionweb.language", "LionCoreBuiltins"),
                            )
                        } else if (implementedClassifier.language == classifier.language) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%L)",
                                implementedClassifier.name!!.decapitalize(),
                            )
                        } else {
                            TODO()
                        }
                    }
                }
                is Interface -> {
                    classifier.extendedInterfaces.forEach {
                        if (it == LionCoreBuiltins.getINamed(LionWebVersion.v2023_1)) {
                            populateMethodCode.addStatement(
                                "$varName.addExtendedInterface(%T.getINamed(LionWebVersion.v2023_1))",
                                ClassName("io.lionweb.language", "LionCoreBuiltins"),
                            )
                        } else {
                            TODO()
                        }
                    }
                }
            }

            classifier.features.forEach { feature ->
                when (feature) {
                    is Property -> {
                        if (feature.type!!.language == LionCoreBuiltins.getInstance(LionWebVersion.v2023_1)) {
                            populateMethodCode.addStatement(
                                "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setType(%T.getInstance(LionWebVersion.v2023_1).getPrimitiveTypeByName(%S)))",
                                varName,
                                ClassName("io.lionweb.language", "Property"),
                                feature.id,
                                feature.key,
                                feature.name!!,
                                feature.isOptional,
                                ClassName("io.lionweb.language", "LionCoreBuiltins"),
                                feature.type!!.name!!,
                            )
                        } else if (feature.type!!.language == ASTLanguageV1.getLanguage()) {
                            populateMethodCode.addStatement(
                                "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setType(%T.getLanguage().getPrimitiveTypeByName(%S)))",
                                varName,
                                ClassName("io.lionweb.language", "Property"),
                                feature.id,
                                feature.key,
                                feature.name!!,
                                feature.isOptional,
                                ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                                feature.type!!.name!!,
                            )
                        } else {
                            populateMethodCode.addStatement(
                                "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setType(%L))",
                                varName,
                                ClassName("io.lionweb.language", "Property"),
                                feature.id,
                                feature.key,
                                feature.name!!,
                                feature.isOptional,
                                feature.type!!.name!!.decapitalize(),
                            )
                        }
                    }

                    is Containment -> {
                        if (feature.type!!.language == LionCoreBuiltins.getInstance(LionWebVersion.v2023_1)) {
                            TODO()
                        } else if (feature.type!!.language == ASTLanguageV1.getLanguage()) {
                            if (feature.type is Concept) {
                                populateMethodCode.addStatement(
                                    "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%T.getLanguage().getConceptByName(%S)))",
                                    varName,
                                    ClassName("io.lionweb.language", "Containment"),
                                    feature.id,
                                    feature.key,
                                    feature.name!!,
                                    feature.isOptional,
                                    feature.isMultiple,
                                    ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                                    feature.type!!.name!!,
                                )
                            } else {
                                populateMethodCode.addStatement(
                                    "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%T.getLanguage().getInterfaceByName(%S)))",
                                    varName,
                                    ClassName("io.lionweb.language", "Containment"),
                                    feature.id,
                                    feature.key,
                                    feature.name!!,
                                    feature.isOptional,
                                    feature.isMultiple,
                                    ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                                    feature.type!!.name!!,
                                )
                            }
                        } else {
                            populateMethodCode.addStatement(
                                "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%L))",
                                varName,
                                ClassName("io.lionweb.language", "Containment"),
                                feature.id,
                                feature.key,
                                feature.name!!,
                                feature.isOptional,
                                feature.isMultiple,
                                feature.type!!.name!!.decapitalize(),
                            )
                        }
                    }

                    is Reference -> {
                        if (feature.type!!.language == LionCoreBuiltins.getInstance(LionWebVersion.v2023_1)) {
                            if (feature.type is Concept) {
                                populateMethodCode.addStatement(
                                    "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%T.getInstance(LionWebVersion.v2023_1).getConceptByName(%S)))",
                                    varName,
                                    ClassName("io.lionweb.language", "Reference"),
                                    feature.id,
                                    feature.key,
                                    feature.name!!,
                                    feature.isOptional,
                                    feature.isMultiple,
                                    ClassName("io.lionweb.language", "LionCoreBuiltins"),
                                    feature.type!!.name!!,
                                )
                            } else {
                                populateMethodCode.addStatement(
                                    "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%T.getInstance(LionWebVersion.v2023_1).getInterfaceByName(%S)))",
                                    varName,
                                    ClassName("io.lionweb.language", "Reference"),
                                    feature.id,
                                    feature.key,
                                    feature.name!!,
                                    feature.isOptional,
                                    feature.isMultiple,
                                    ClassName("io.lionweb.language", "LionCoreBuiltins"),
                                    feature.type!!.name!!,
                                )
                            }
                        } else if (feature.type!!.language == ASTLanguageV1.getLanguage()) {
                            populateMethodCode.addStatement(
                                "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%T.getLanguage().%L(%S)))",
                                varName,
                                ClassName("io.lionweb.language", "Reference"),
                                feature.id,
                                feature.key,
                                feature.name!!,
                                feature.isOptional,
                                feature.isMultiple,
                                if (feature.type is Concept) "getConceptByName" else "getInterfaceByName",
                                ClassName("com.strumenta.starlasu.base.v1", "ASTLanguageV1"),
                                feature.type!!.name!!,
                            )
                        } else {
                            populateMethodCode.addStatement(
                                "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%L))",
                                varName,
                                ClassName("io.lionweb.language", "Reference"),
                                feature.id,
                                feature.key,
                                feature.name!!,
                                feature.isOptional,
                                feature.isMultiple,
                                feature.type!!.name!!.decapitalize(),
                            )
                        }
                    }

                    else -> TODO()
                }
            }

            langInit.addStatement("populate${classifier.name!!.capitalize()}()")
            populateMethod.addCode(populateMethodCode.build())
            langClassBuilder.addFunction(populateMethod.build())
        }
        // }
    }

    private fun save(fileSpec: FileSpec) {
        fileSpec.writeTo(outputDir)
    }

    private fun createPrimitiveType(
        primitiveType: PrimitiveType,
        langInit: CodeBlock.Builder,
        langClassBuilder: TypeSpec.Builder,
    ) {
        val varName = primitiveType.name!!.decapitalize()
        langClassBuilder.addProperty(
            PropertySpec
                .builder(varName, ClassName("io.lionweb.language", "PrimitiveType"), KModifier.LATEINIT)
                .mutable(true)
                .build(),
        )
        langInit.addStatement("$varName = PrimitiveType()")
        langInit.addStatement("$varName.setID(\"${primitiveType.id!!}\")")
        langInit.addStatement("$varName.setName(\"${primitiveType.name!!}\")")
        langInit.addStatement("$varName.setKey(\"${primitiveType.key!!}\")")
        langInit.addStatement("addElement($varName)")
    }
}

fun main(args: Array<String>) {
    LanguagesGeneratorCommand().main(args)
}
