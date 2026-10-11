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

/**
 * Base [SourceIdProvider] offering [cleanId], which turns a string into a valid LionWeb ID.
 */
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

/**
 * Derives the ID from the source: the path of a [FileSource], the description of a [SyntheticSource], the codebase
 * name and relative path of a [CodeBaseSource], or [SourceWithID.sourceID]. Other sources are not supported.
 *
 * A null source yields [UNKNOWN_SOURCE_ID] if [acceptNullSource] is true; otherwise [SourceShouldBeSetException] is
 * thrown.
 */
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

/**
 * Derives the ID of a [FileSource] from its path relative to [baseDir], prefixed by [rootName] if set. Only file
 * sources are supported. A null source yields [UNKNOWN_SOURCE_ID] if [acceptNullSource] is true.
 */
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

/**
 * Thrown when an ID cannot be generated.
 */
open class IDGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Thrown when an ID depends on the source of a node, but the source is not set.
 */
class SourceShouldBeSetException(
    message: String,
    cause: Throwable? = null,
) : IDGenerationException(
        message,
        cause,
    )

/**
 * Thrown when a node whose class is not annotated with [com.strumenta.starlasu.model.ASTRoot] is found at the root
 * of a tree.
 */
class NodeShouldNotBeRootException(
    message: String,
    cause: Throwable? = null,
) : IDGenerationException(
        message,
        cause,
    )

/**
 * Thrown when a node annotated with [com.strumenta.starlasu.model.ASTRoot] (without `canBeNotRoot`) has a parent.
 */
class NodeShouldBeRootException(
    message: String,
    cause: Throwable? = null,
) : IDGenerationException(
        message,
        cause,
    )
