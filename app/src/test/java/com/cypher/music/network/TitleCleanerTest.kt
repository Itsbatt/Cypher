package com.cypher.music.network

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleanerTest {

    @Test
    fun `removes the usual youtube decorations`() {
        assertEquals("La Vida Era Más Corta", TitleCleaner.clean("La Vida Era Más Corta (Official Video)"))
        assertEquals("OJALA", TitleCleaner.clean("OJALA [Official Music Video]"))
        assertEquals("Solifican12", TitleCleaner.clean("Solifican12 (Video Oficial)"))
        assertEquals("Niño", TitleCleaner.clean("Niño (Visualizer)"))
    }

    @Test
    fun `keeps titles without decorations untouched`() {
        assertEquals("Top Picks for You", TitleCleaner.clean("Top Picks for You"))
        assertEquals("", TitleCleaner.clean("   "))
    }

    @Test
    fun `collapses the extra spaces left behind`() {
        assertEquals("Canción limpia", TitleCleaner.clean("Canción   (Official Video)  limpia"))
    }
}
