package com.strumenta.starlasu.utils

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathGlobTest {
    @Test
    fun patternWithoutSlashMatchesFileNameOnly() {
        val glob = PathGlob("*.cob")
        assertTrue(glob.matches("A.cob"))
        assertTrue(glob.matches("src/deep/A.COB"))
        assertFalse(glob.matches("src/A.cbl"))
    }

    @Test
    fun singleStarAndQuestionMarkStayWithinSegment() {
        assertTrue(PathGlob("src/*.cbl").matches("src/A.cbl"))
        assertFalse(PathGlob("src/*.cbl").matches("src/x/A.cbl"))
        assertTrue(PathGlob("src/?.cbl").matches("src/A.cbl"))
        assertFalse(PathGlob("src/?.cbl").matches("src/AB.cbl"))
        assertFalse(PathGlob("src?A.cbl").matches("src/A.cbl"))
    }

    @Test
    fun doubleStarCrossesDirectories() {
        val glob = PathGlob("legacy/**")
        assertTrue(glob.matches("legacy/A.cbl"))
        assertTrue(glob.matches("legacy/x/y/A.cbl"))
        assertFalse(glob.matches("other/A.cbl"))
    }

    @Test
    fun doubleStarFollowedBySlashMatchesZeroDirectories() {
        val glob = PathGlob("**/mf/*.cbl")
        assertTrue(glob.matches("mf/A.cbl"))
        assertTrue(glob.matches("x/y/mf/A.cbl"))
        assertFalse(glob.matches("x/y/mf/A.cpy"))
    }

    @Test
    fun alternatives() {
        val glob = PathGlob("src/*.{cbl,cob}")
        assertTrue(glob.matches("src/A.cbl"))
        assertTrue(glob.matches("src/A.cob"))
        assertFalse(glob.matches("src/A.cpy"))
        assertFailsWith<IllegalArgumentException> { PathGlob("*.{cbl,cob") }
    }

    @Test
    fun separatorsAndPrefixesAreNormalized() {
        assertTrue(PathGlob("legacy/**").matches("legacy\\billing\\A.cbl"))
        assertTrue(PathGlob("./src/*.cbl").matches("./src/A.cbl"))
        assertTrue(PathGlob("src/a+b(1).cbl").matches("src/A+B(1).cbl"))
    }

    @Test
    fun emptyPatternIsRejected() {
        assertFailsWith<IllegalArgumentException> { PathGlob("  ") }
    }

    @Test
    fun filesAreMatchedRelativeToBaseDirectory() {
        val base = File("project")
        val glob = PathGlob("src/**/*.cbl")
        assertTrue(glob.matches(File(base, "src/x/A.cbl"), base))
        assertFalse(glob.matches(File("elsewhere/src/x/A.cbl"), base))
        assertFalse(glob.matches(File(base, "../src/A.cbl"), base))
        assertEquals("src/x/A.cbl", PathGlob.relativePath(File(base, "src/x/A.cbl"), base))
        assertNull(PathGlob.relativePath(File("elsewhere/A.cbl"), base))
    }

    @Test
    fun equalityIsBasedOnPattern() {
        assertEquals(PathGlob("*.cbl"), PathGlob("*.cbl"))
        assertEquals(PathGlob("*.cbl").hashCode(), PathGlob("*.cbl").hashCode())
        assertEquals("*.cbl", PathGlob("*.cbl").toString())
    }
}
