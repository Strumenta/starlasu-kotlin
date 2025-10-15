package com.strumenta.starlasu.ids

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceIdProviderTest {
    @Test
    fun testRemoveCharactersInvalidInLionWebIDs() {
        assertEquals(
            "funny933--_12aaAAAAZz",
            "funny%à, è, é, ì, ò, ù =+933--_12aaAAAAZz".removeCharactersInvalidInLionWebIDs(),
        )
    }
}
