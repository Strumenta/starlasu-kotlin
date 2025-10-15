package com.strumenta.starlasu.ids

import com.strumenta.starlasu.model.CodeBaseSource
import com.strumenta.starlasu.model.FileSource
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.model.SourceWithID
import com.strumenta.starlasu.model.SyntheticSource
import java.io.File

const val UNKNOWN_SOURCE_ID = "UNKNOWN_SOURCE"

fun String.removeCharactersInvalidInLionWebIDs(): String =
    this
        .filter {
            it in setOf('-', '_') ||
                it in CharRange('0', '9') ||
                it in CharRange('a', 'z') ||
                it in CharRange('A', 'Z')
        }.toString()

/**
 * Given a Source (even null), it generates a corresponding identifier.
 */
interface SourceIdProvider {
    fun sourceId(source: Source?): String
}

abstract class AbstractSourceIdProvider : SourceIdProvider {
    protected fun cleanId(id: String) =
        id
            .replace('.', '-')
            .replace('/', '-')
            .replace('\\', '-')
            .replace('#', '-')
            .replace(' ', '_')
            .replace("@", "_at_")
            .removeCharactersInvalidInLionWebIDs()
}

class SimpleSourceIdProvider(
    var acceptNullSource: Boolean = false,
) : AbstractSourceIdProvider() {
    override fun sourceId(source: Source?): String =
        when (source) {
            null -> {
                if (acceptNullSource) {
                    UNKNOWN_SOURCE_ID
                } else {
                    throw SourceShouldBeSetException("Source should not be null")
                }
            }

            is FileSource -> {
                cleanId("file_${source.file.path}")
            }

            is SyntheticSource -> {
                cleanId("synthetic_${source.description}")
            }

            is CodeBaseSource -> {
                cleanId("codebase_${source.codebaseName}_relpath_${source.relativePath}")
            }

            is SourceWithID -> source.sourceID()
            else -> {
                TODO("Unable to generate ID for Source $this (${source.javaClass.canonicalName})")
            }
        }
}

class RelativeSourceIdProvider(
    val baseDir: File,
    var rootName: String? = null,
    var acceptNullSource: Boolean = false,
) : AbstractSourceIdProvider() {
    override fun sourceId(source: Source?): String {
        return when (source) {
            null -> {
                if (acceptNullSource) {
                    UNKNOWN_SOURCE_ID
                } else {
                    throw IDGenerationException("Source should not be null")
                }
            }

            is FileSource -> {
                val thisAbsPath = source.file.absolutePath
                val baseAbsPath = baseDir.absolutePath
                val expectedPrefix = baseAbsPath + File.separator
                require(thisAbsPath.startsWith(expectedPrefix))
                val relativePath = thisAbsPath.removePrefix(expectedPrefix)
                val id = if (rootName == null) relativePath else "${rootName}___$relativePath"
                return cleanId(id)
            }

            else -> {
                TODO("Unable to generate ID for Source $this (${this.javaClass.canonicalName})")
            }
        }
    }
}

open class IDGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class SourceShouldBeSetException(
    message: String,
    cause: Throwable? = null,
) : IDGenerationException(
        message,
        cause,
    )

class NodeShouldNotBeRootException(
    message: String,
    cause: Throwable? = null,
) : IDGenerationException(
        message,
        cause,
    )

class NodeShouldBeRootException(
    message: String,
    cause: Throwable? = null,
) : IDGenerationException(
        message,
        cause,
    )
