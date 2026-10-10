package com.strumenta.starlasu.codebase

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.parsing.ParsingResult
import java.io.File
import java.util.Stack

/**
 * A [Codebase] made of the files under [baseDir] whose extension is in [extensions] (case-insensitive). Files and
 * directories whose name starts with `.` are skipped.
 *
 * Files are parsed on first access with [parser], which receives the relative path and the file. Files for which
 * parsing produces no root are left out.
 */
class FileBasedCodebase<R : Node>(
    val baseDir: File,
    val extensions: Set<String>,
    override val name: String = "UnnamedCodebase",
    val parser: (String, File) -> ParsingResult<R>,
) : Codebase<R> {
    private val cachedFiles: List<CodebaseFile<R>> by lazy {
        parseFiles().toList()
    }

    override fun files(): Sequence<CodebaseFile<R>> = cachedFiles.asSequence()

    override fun fileByRelativePath(relativePath: String): CodebaseFile<R>? =
        cachedFiles.find {
            it.relativePath == relativePath
        }

    private fun parseFiles(): Sequence<CodebaseFile<R>> =
        sequence {
            val stackOfDirs = Stack<File>()
            stackOfDirs.add(baseDir)
            while (stackOfDirs.isNotEmpty()) {
                val dir = stackOfDirs.pop()
                if (!dir.name.startsWith(".")) {
                    dir.listFiles()?.forEach { child ->
                        if (child.isDirectory) {
                            stackOfDirs.add(child)
                        } else if (child.isFile) {
                            if (!child.name.startsWith(".")) {
                                if (child.extension.lowercase() in extensions.map { it.lowercase() }) {
                                    val relativePath = child.relativeTo(baseDir).path
                                    val parsingResult = parser.invoke(relativePath, child)
                                    val root = parsingResult.root
                                    if (root != null) {
                                        val codebaseFile =
                                            CodebaseFile(
                                                this@FileBasedCodebase,
                                                relativePath,
                                                child.readText(),
                                                root,
                                                tokens = null,
                                                parsingResult.issues,
                                            )
                                        yield(codebaseFile)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}
