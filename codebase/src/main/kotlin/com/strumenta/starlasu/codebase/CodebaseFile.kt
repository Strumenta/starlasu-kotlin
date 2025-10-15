package com.strumenta.starlasu.codebase

import com.strumenta.starlasu.lionweb.TokensList
import com.strumenta.starlasu.model.CodeBaseSource
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.PropertyType
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.traversing.walk
import com.strumenta.starlasu.validation.Issue
import java.io.File

data class CodebaseFile<R : Node>(
    val codebase: Codebase<R>,
    val relativePath: String,
    var code: String,
    var ast: R,
    var tokens: TokensList?,
    val parsingIssues: List<Issue> = emptyList(),
) {
    init {
        ast.source = this.asSource()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CodebaseFile<*>) return false

        if (relativePath != other.relativePath) return false

        return true
    }

    override fun hashCode(): Int = relativePath.hashCode()

    fun areAllReferencesResolved(): Boolean =
        ast.walk().all {
            it.originalProperties.filter { it.propertyType == PropertyType.REFERENCE }.all { property ->
                (property.value as ReferenceByName<*>).resolved
            }
        }

    fun hasExtension(extension: String): Boolean = this.relativePath.lowercase().endsWith(".${extension.lowercase()}")

    fun asSource(): CodeBaseSource = CodeBaseSource(codebase.name, relativePath)
}

fun <R : Node> CodebaseFile<R>.hasExtension(extension: String): Boolean =
    this.relativePath.lowercase().endsWith(".${extension.lowercase()}")

fun <R : Node> Codebase<R>.filesWithExtension(vararg extensions: String): Sequence<CodebaseFile<R>> =
    sequence {
        val it = files().iterator()
        while (it.hasNext()) {
            val codebaseFile = it.next()
            if (extensions.any { extension ->
                    codebaseFile.relativePath.lowercase().endsWith(
                        ".${extension.lowercase()}",
                    )
                }
            ) {
                yield(codebaseFile)
            }
        }
    }

val <R : Node> CodebaseFile<R>.nameWithoutExtension: String
    get() {
        val simpleName = relativePath.split("/").last()
        return File(simpleName).nameWithoutExtension
    }
