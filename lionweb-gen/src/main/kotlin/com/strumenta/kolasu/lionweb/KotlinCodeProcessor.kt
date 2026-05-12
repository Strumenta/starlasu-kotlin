package com.strumenta.kolasu.lionweb

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

private val KtDeclaration.fqName: String
    get() {
        val packageName = (this.parent as KtFile).packageDirective?.fqName
        return if (packageName == null) {
            this.name!!
        } else {
            "$packageName.$name"
        }
    }

/**
 * This class finds Kotlin classes definitions.
 */
class KotlinCodeProcessor {

    fun classesDeclaredInFile(code: String): Set<String> {
        val ktFile = parse(code)
        return ktFile.declarations.map {
            it.fqName
        }.toSet()
    }

    fun classesDeclaredInFile(file: File): Set<String> {
        require(file.isFile)
        return classesDeclaredInFile(file.readText())
    }

    fun classesDeclaredInDir(file: File): Set<String> {
        if (!file.exists()) {
            return emptySet()
        }
        require(file.isDirectory)
        val set = mutableSetOf<String>()
        file.listFiles()?.forEach {
            when {
                it.isFile -> set.addAll(classesDeclaredInFile(it))
                it.isDirectory -> set.addAll(classesDeclaredInDir(it))
            }
        }
        return set
    }

    private fun parse(code: String): KtFile {
        val disposable = Disposer.newDisposable()
        try {
            val env = KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
            val file = LightVirtualFile("temp.kt", KotlinFileType.INSTANCE, code)
            return PsiManager.getInstance(env.project).findFile(file) as KtFile
        } finally {
            disposable.dispose()
        }
    }
}
