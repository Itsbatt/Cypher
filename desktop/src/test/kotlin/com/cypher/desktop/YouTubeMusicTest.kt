package com.cypher.desktop

import com.cypher.desktop.core.YouTubeMusic
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class YouTubeMusicTest {

    private val sample = """
        {"_type":"playlist","entries":[
          {"id":"p7YpVl35pac","title":"Milo J: Tiny Desk Concert","channel":"NPR Music","duration":988,
           "thumbnails":[{"url":"https://i.ytimg.com/vi/p7YpVl35pac/hq.jpg"}]},
          {"id":"UCLabAHLHMy2MdTWaZATUmQQ","title":"Milo J","_type":"url"},
          {"id":"zI_te4gSWz4","title":"Niño","duration":211}
        ]}
    """.trimIndent()

    @Test
    fun `parses only entries that are real videos`() {
        val hits = YouTubeMusic.parseSearch(sample)
        assertEquals(listOf("p7YpVl35pac", "zI_te4gSWz4"), hits.map { it.videoId })
        assertEquals("Milo J: Tiny Desk Concert", hits.first().title)
        assertEquals("NPR Music", hits.first().artist)
        assertEquals(988_000, hits.first().durationMs)
        assertEquals("https://i.ytimg.com/vi/p7YpVl35pac/hq.jpg", hits.first().thumbnail)
    }

    @Test
    fun `missing artist and duration stay empty instead of invented`() {
        val last = YouTubeMusic.parseSearch(sample).last()
        assertEquals("", last.artist)
        assertEquals(211_000, last.durationMs)
    }

    @Test
    fun `broken json returns nothing`() {
        assertTrue(YouTubeMusic.parseSearch("not json at all").isEmpty())
        assertTrue(YouTubeMusic.parseSearch("""{"entries":"nope"}""").isEmpty())
    }

    @Test
    fun `configured binary wins over discovery`() {
        val dir = Files.createTempDirectory("cypher-yt").toFile()
        val fake = File(dir, "yt-dlp.exe").apply { writeText("stub") }
        assertEquals(fake, YouTubeMusic.binary(fake.absolutePath))
        dir.deleteRecursively()
    }

    @Test
    fun `a missing configured path is ignored`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "no-existe-${System.nanoTime()}.exe")
        assertNotEquals(missing, YouTubeMusic.binary(missing.absolutePath))
    }

    @Test
    fun `playlist cover survives a save and reload`() {
        val dir = Files.createTempDirectory("cypher-cover")
        val previous = com.cypher.desktop.core.Storage.rootOverride
        try {
            com.cypher.desktop.core.Storage.rootOverride = dir
            val data = com.cypher.desktop.core.LibraryData(
                playlists = listOf(
                    com.cypher.desktop.core.Playlist("p1", "Lista", artwork = "https://i.ytimg.com/vi/abc/hq.jpg")
                )
            )
            com.cypher.desktop.core.Storage.save(data)
            assertEquals("https://i.ytimg.com/vi/abc/hq.jpg", com.cypher.desktop.core.Storage.load().playlist("p1")?.artwork)
        } finally {
            com.cypher.desktop.core.Storage.rootOverride = previous
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an empty playlist link is rejected without touching the network`() {
        val error = runCatching { YouTubeMusic.playlist("   ") }.exceptionOrNull()
        assertTrue(error is com.cypher.desktop.core.YouTubeMusicException)
    }

    @Test
    fun `playback uses the music domain`() {
        assertEquals("https://music.youtube.com/watch?v=zI_te4gSWz4", YouTubeMusic.watchUrl("zI_te4gSWz4"))
    }
}
