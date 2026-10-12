package com.strumenta.starlasu.utils

import java.io.File

/**
 * A glob pattern matched against relative paths, using `/` as separator on every platform and ignoring case.
 *
 * Supported syntax:
 * - `*` matches any sequence of characters within a path segment;
 * - `?` matches a single character within a path segment;
 * - `**` matches any sequence of characters, across segments; when followed by a slash it also matches zero
 *   directories, so `**&#47;*.cbl` matches both `A.cbl` and `src/A.cbl`;
 * - `{a,b}` matches either alternative;
 * - a pattern without `/` is matched against the file name only, so `*.cob` matches `.cob` files in any directory.
 */
class PathGlob(
    val pattern: String,
) {
    private val regex: Regex
    private val matchFileNameOnly: Boolean

    init {
        val normalized = normalizePath(pattern.trim())
        require(normalized.isNotEmpty()) { "Empty glob pattern" }
        matchFileNameOnly = !normalized.contains('/')
        regex = Regex(toRegex(normalized), RegexOption.IGNORE_CASE)
    }

    /**
     * Matches a path relative to some base directory. Both `/` and `\` are accepted as separators.
     */
    fun matches(path: String): Boolean {
        val normalized = normalizePath(path)
        val candidate = if (matchFileNameOnly) normalized.substringAfterLast('/') else normalized
        return regex.matches(candidate)
    }

    /**
     * Matches [file] using its path relative to [baseDirectory]. Files outside [baseDirectory] never match.
     */
    fun matches(
        file: File,
        baseDirectory: File,
    ): Boolean {
        val relative = relativePath(file, baseDirectory) ?: return false
        return matches(relative)
    }

    override fun toString(): String = pattern

    override fun equals(other: Any?): Boolean = other is PathGlob && other.pattern == pattern

    override fun hashCode(): Int = pattern.hashCode()

    companion object {
        @JvmStatic
        fun normalizePath(path: String): String = path.replace('\\', '/').removePrefix("./")

        /**
         * The path of [file] relative to [baseDirectory], using `/` as separator, or null if [file] is not inside
         * [baseDirectory].
         */
        @JvmStatic
        fun relativePath(
            file: File,
            baseDirectory: File,
        ): String? {
            val base = baseDirectory.absoluteFile.toPath().normalize()
            val target = file.absoluteFile.toPath().normalize()
            if (!target.startsWith(base)) return null
            return normalizePath(base.relativize(target).toString())
        }

        private fun toRegex(glob: String): String {
            val sb = StringBuilder()
            var i = 0
            var inAlternatives = false
            while (i < glob.length) {
                val c = glob[i]
                when {
                    c == '*' && glob.startsWith("**/", i) -> {
                        sb.append("(?:.*/)?")
                        i += 3
                        continue
                    }
                    c == '*' && glob.startsWith("**", i) -> {
                        sb.append(".*")
                        i += 2
                        continue
                    }
                    c == '*' -> sb.append("[^/]*")
                    c == '?' -> sb.append("[^/]")
                    c == '{' && !inAlternatives -> {
                        sb.append("(?:")
                        inAlternatives = true
                    }
                    c == '}' && inAlternatives -> {
                        sb.append(")")
                        inAlternatives = false
                    }
                    c == ',' && inAlternatives -> sb.append("|")
                    else -> sb.append(Regex.escape(c.toString()))
                }
                i++
            }
            require(!inAlternatives) { "Unclosed '{' in glob pattern '$glob'" }
            return sb.toString()
        }
    }
}
