@file:JvmName("ASTGeneratorCLI")

package com.strumenta.kolasu.lionweb

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.strumenta.starlasu.base.v1.ASTLanguage
import io.lionweb.language.Language
import io.lionweb.serialization.SerializationProvider
import java.io.File
import java.io.FileInputStream

class ASTGeneratorCommand : CliktCommand() {
    val packageName by argument()
    val languageFile by argument().file(mustExist = true, mustBeReadable = true, canBeDir = false)
    val destinationDir by argument().file(mustExist = true, canBeFile = false)
    val existingKotlinCode by argument().file(mustExist = true, canBeFile = false)

    override fun run() {
        val existingKotlinClasses = KotlinCodeProcessor().classesDeclaredInDir(existingKotlinCode)

        val jsonser = SerializationProvider.getStandardJsonSerialization(LIONWEB_VERSION_USED_BY_KOLASU)
        jsonser.instanceResolver.addTree(ASTLanguage.getInstance())
        val language = jsonser.deserializeToNodes(FileInputStream(languageFile)).first() as Language
        val ktFiles = ASTGenerator(packageName, language).generateClasses(existingKotlinClasses)
        ktFiles.forEach {
            File(destinationDir, it.path).writeText(it.code)
        }
    }
}

fun main(args: Array<String>) {
    ASTGeneratorCommand().main(args)
}
