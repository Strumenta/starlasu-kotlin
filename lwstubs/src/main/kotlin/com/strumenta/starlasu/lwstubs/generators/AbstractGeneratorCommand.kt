package com.strumenta.starlasu.lwstubs.generators

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.strumenta.starlasu.base.v1.ASTLanguageV1
import io.lionweb.LionWebVersion
import io.lionweb.language.Language
import io.lionweb.serialization.AbstractSerialization
import io.lionweb.serialization.JsonSerialization
import io.lionweb.serialization.ProtoBufSerialization
import io.lionweb.serialization.SerializationProvider
import java.io.File

abstract class AbstractGeneratorCommand(val name: String) : CliktCommand(name){
    abstract val dependenciesFiles: List<File>
    abstract val languageFiles: List<File>
    abstract val outputDir: File
    abstract val lwVersion: LionWebVersion
    abstract val names: List<String>

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
            processLanguage(language, overriddenName)
        }
    }

    protected abstract fun processLanguage(language: Language, overriddenName: String?)
}