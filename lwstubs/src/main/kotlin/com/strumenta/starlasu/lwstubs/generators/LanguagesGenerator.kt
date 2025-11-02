package com.strumenta.starlasu.lwstubs.generators

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.strumenta.starlasu.base.v1.ASTLanguageV1
import io.lionweb.LionWebVersion
import io.lionweb.language.Classifier
import io.lionweb.language.Concept
import io.lionweb.language.Containment
import io.lionweb.language.Enumeration
import io.lionweb.language.EnumerationLiteral
import io.lionweb.language.Interface
import io.lionweb.language.Language
import io.lionweb.language.LionCoreBuiltins
import io.lionweb.language.PrimitiveType
import io.lionweb.language.Property
import io.lionweb.language.Reference
import java.io.File
import kotlin.collections.associateWith
import kotlin.collections.forEach

class LanguagesGeneratorCommand : AbstractGeneratorCommand("langgen") {
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
        overriddenName: String?,
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
                .superclass(Language::class.asClassName())
                .addSuperclassConstructorParameter("%T.v2023_1", LionWebVersion::class.asClassName())

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

        val classifiers = language.elements.filterIsInstance(Classifier::class.java)
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
                .builder(varName, Enumeration::class.asClassName(), KModifier.LATEINIT)
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
                EnumerationLiteral::class.asClassName(),
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
                    langClassBuilder.addProperty(varName, Concept::class.asClassName())
                    langInit.addStatement("$varName = Concept()")
                    langInit.addStatement("$varName.setID(\"${classifier.id!!}\")")
                    langInit.addStatement("$varName.setKey(\"${classifier.key!!}\")")
                    langInit.addStatement("addElement($varName)")
                }

                is Interface -> {
                    langClassBuilder.addProperty(varName, Interface::class.asClassName())
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
                                ASTLanguageV1::class.asClassName(),
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
                                ASTLanguageV1::class.asClassName(),
                            )
                        } else if (implementedClassifier == ASTLanguageV1.getStatement()) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.getStatement())",
                                ASTLanguageV1::class.asClassName(),
                            )
                        } else if (implementedClassifier.language == ASTLanguageV1.getLanguage()) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.get${implementedClassifier.name!!.capitalize()}())",
                                ASTLanguageV1::class.asClassName(),
                            )
                        } else if (implementedClassifier == ASTLanguageV1.getDocumentation()) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.getDocumentation())",
                                ASTLanguageV1::class.asClassName(),
                            )
                        } else if (implementedClassifier == LionCoreBuiltins.getINamed(LionWebVersion.v2023_1)) {
                            populateMethodCode.addStatement(
                                "$varName.addImplementedInterface(%T.getINamed(LionWebVersion.v2023_1))",
                                LionCoreBuiltins::class.asClassName(),
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
                                LionCoreBuiltins::class.asClassName(),
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
                        val t = requireNotNull(feature.type) { "Property ${feature.name} missing type" }
                        val typeExpr: CodeBlock =
                            when (t.language) {
                                LionCoreBuiltins.getInstance(LionWebVersion.v2023_1) ->
                                    CodeBlock.of(
                                        "%T.getInstance(LionWebVersion.v2023_1).getPrimitiveTypeByName(%S)",
                                        LionCoreBuiltins::class.asClassName(),
                                        t.name,
                                    )
                                ASTLanguageV1.getLanguage() ->
                                    CodeBlock.of(
                                        "%T.getLanguage().getPrimitiveTypeByName(%S)",
                                        ASTLanguageV1::class.asClassName(),
                                        t.name,
                                    )
                                else ->
                                    CodeBlock.of("%L", t.name!!.replaceFirstChar { it.lowercaseChar() })
                            }
                        populateMethodCode.addStatement(
                            "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setType(%L))",
                            varName,
                            Property::class.asClassName(),
                            feature.id,
                            feature.key,
                            requireNotNull(feature.name),
                            feature.isOptional,
                            typeExpr,
                        )
                    }

                    is Containment -> {
                        val t = requireNotNull(feature.type) { "Containment ${feature.name} missing type" }
                        if (t.language == LionCoreBuiltins.getInstance(LionWebVersion.v2023_1)) {
                            TODO("Containment to LionCoreBuiltins not implemented")
                        }

                        val typeExpr: CodeBlock =
                            when (t.language) {
                                ASTLanguageV1.getLanguage() ->
                                    if (t is Concept) {
                                        CodeBlock.of(
                                            "%T.getLanguage().getConceptByName(%S)",
                                            ASTLanguageV1::class.asClassName(),
                                            requireNotNull(t.name),
                                        )
                                    } else {
                                        CodeBlock.of(
                                            "%T.getLanguage().getInterfaceByName(%S)",
                                            ASTLanguageV1::class.asClassName(),
                                            requireNotNull(t.name),
                                        )
                                    }
                                else -> {
                                    val name = requireNotNull(t.name).replaceFirstChar { it.lowercaseChar() }
                                    CodeBlock.of("%L", name)
                                }
                            }

                        populateMethodCode.addStatement(
                            "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L).setMultiple(%L).setType(%L))",
                            varName,
                            Containment::class.asClassName(),
                            feature.id,
                            feature.key,
                            requireNotNull(feature.name),
                            feature.isOptional,
                            feature.isMultiple,
                            typeExpr,
                        )
                    }

                    is Reference -> {
                        val t = requireNotNull(feature.type) { "Reference ${feature.name} missing type" }

                        val typeExpr: CodeBlock =
                            when (t.language) {
                                LionCoreBuiltins.getInstance(LionWebVersion.v2023_1) -> {
                                    val fn = if (t is Concept) "getConceptByName" else "getInterfaceByName"
                                    CodeBlock.of(
                                        "%T.getInstance(LionWebVersion.v2023_1).%L(%S)",
                                        LionCoreBuiltins::class.asClassName(),
                                        fn,
                                        requireNotNull(t.name),
                                    )
                                }
                                ASTLanguageV1.getLanguage() -> {
                                    val fn = if (t is Concept) "getConceptByName" else "getInterfaceByName"
                                    CodeBlock.of(
                                        "%T.getLanguage().%L(%S)",
                                        ASTLanguageV1::class.asClassName(),
                                        fn,
                                        requireNotNull(t.name),
                                    )
                                }
                                else ->
                                    CodeBlock.of(
                                        "%L",
                                        requireNotNull(t.name).replaceFirstChar { it.lowercaseChar() },
                                    )
                            }

                        populateMethodCode.addStatement(
                            "%L.addFeature(%T().setID(%S).setKey(%S).setName(%S).setOptional(%L)" +
                                ".setMultiple(%L).setType(%L))",
                            varName,
                            Reference::class.asClassName(),
                            feature.id,
                            feature.key,
                            requireNotNull(feature.name),
                            feature.isOptional,
                            feature.isMultiple,
                            typeExpr,
                        )
                    }

                    else -> TODO()
                }
            }

            langInit.addStatement("populate${classifier.name!!.capitalize()}()")
            populateMethod.addCode(populateMethodCode.build())
            langClassBuilder.addFunction(populateMethod.build())
        }
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
                .builder(varName, PrimitiveType::class.asClassName(), KModifier.LATEINIT)
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
