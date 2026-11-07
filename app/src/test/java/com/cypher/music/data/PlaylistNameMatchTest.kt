package com.cypher.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistNameMatchTest {

    @Test
    fun `names match ignoring case and extra spaces`() {
        assertTrue(PlaylistSync.namesMatch("Rock", " rock "))
        assertTrue(PlaylistSync.namesMatch("Mis   Favoritas", "mis favoritas"))
        assertTrue(PlaylistSync.namesMatch("After Hours", "After Hours"))
    }

    @Test
    fun `different names do not match`() {
        assertFalse(PlaylistSync.namesMatch("Rock", "Rock 2"))
        assertFalse(PlaylistSync.namesMatch("Rock", "Rock en español"))
        assertFalse(PlaylistSync.namesMatch("", "Rock"))
    }

    @Test
    fun `normalizes whitespace and case`() {
        assertEquals("mi playlist", PlaylistSync.normalizeName("  Mi \t Playlist  "))
    }
}
