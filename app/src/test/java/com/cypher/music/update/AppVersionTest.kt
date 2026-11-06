package com.cypher.music.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses tags with and without v prefix`() {
        assertEquals("1.0.2", AppVersion.parse("v1.0.2")?.toString())
        assertEquals("1.0.2", AppVersion.parse("1.0.2")?.toString())
        assertEquals("1.2.0", AppVersion.parse("v1.2")?.toString())
        assertEquals("3.0.0", AppVersion.parse("3")?.toString())
        assertEquals("1.0.3-beta.1", AppVersion.parse("v1.0.3-beta.1")?.toString())
    }

    @Test
    fun `orders numeric parts`() {
        assertTrue(AppVersion.parse("1.0.10")!! > AppVersion.parse("1.0.9")!!)
        assertTrue(AppVersion.parse("1.2.0")!! > AppVersion.parse("1.1.99")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
    }

    @Test
    fun `release outranks its prereleases`() {
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("1.0.0-rc.1")!!)
        assertTrue(AppVersion.parse("1.0.0-rc.2")!! > AppVersion.parse("1.0.0-rc.1")!!)
        assertTrue(AppVersion.parse("1.0.0-beta.10")!! > AppVersion.parse("1.0.0-beta.9")!!)
        assertTrue(AppVersion.parse("1.0.0-alpha")!! < AppVersion.parse("1.0.0-beta")!!)
    }

    @Test
    fun `rejects invalid tags`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("Cypher v1.0.2"))
    }
}
