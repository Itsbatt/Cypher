package com.cypher.desktop.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone shares a ZIP with `cypher-library.json` inside. These tests build exactly that
 * file and check the two things that matter: it lands in the library, and importing the
 * same file twice does not duplicate anything.
 */
class BackupImportTest {

    private fun zipWith(json: String): File {
        val file = File.createTempFile("cypher-backup-", ".zip")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("cypher-library.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return file
    }

    private val sample = """
        {
          "version": 4,
          "appName": "Cypher",
          "timestamp": 1700000000000,
          "playlists": [
            {
              "id": "pl-1",
              "name": "Noche en casa",
              "description": "Para dormir",
              "coverUri": "",
              "createdAt": 1700000000000,
              "updatedAt": 1700000000000,
              "tracks": [
                {
                  "id": "t-1",
                  "title": "Una bala",
                  "artist": "Milo J",
                  "album": "111",
                  "durationMs": 321000,
                  "artworkUrl": "",
                  "streamUrl": "https://music.youtube.com/watch?v=abc123",
                  "sourceType": "YOUTUBE"
                }
              ]
            }
          ],
          "favorites": ["t-1"]
        }
    """.trimIndent()

    @Test
    fun `imports playlists tracks and favorites from the zip`() {
        val (merged, summary) = BackupImport.import(zipWith(sample), LibraryData())

        assertEquals(1, summary.playlists)
        assertEquals(1, summary.tracks)
        assertEquals(1, summary.favorites)
        assertEquals(1, merged.playlists.size)
        assertEquals("Noche en casa", merged.playlists.single().name)
        assertEquals(listOf("t-1"), merged.playlists.single().trackIds)
        assertTrue(merged.remoteTrack("t-1") != null, "the YouTube song must arrive as a playable reference")
        assertEquals(listOf("t-1"), merged.favorites)
    }

    @Test
    fun `importing the same zip twice changes nothing`() {
        val file = zipWith(sample)
        val (once, _) = BackupImport.import(file, LibraryData())
        val (twice, second) = BackupImport.import(file, once)

        assertEquals(0, second.playlists)
        assertEquals(0, second.tracks)
        assertEquals(1, twice.playlists.size)
        assertEquals(1, twice.remoteTracks.size)
        assertEquals(1, twice.favorites.size)
    }

    @Test
    fun `reads a bare json too`() {
        val file = File.createTempFile("cypher-backup-", ".json")
        file.deleteOnExit()
        file.writeText(sample)
        val (merged, summary) = BackupImport.import(file, LibraryData())
        assertEquals(1, summary.playlists)
        assertEquals(1, merged.playlists.size)
    }

    @Test
    fun `round trip through our own export keeps the playlist`() {
        val (merged, _) = BackupImport.import(zipWith(sample), LibraryData())
        val exported = BackupImport.exportJson(merged)
        val file = File.createTempFile("cypher-round-", ".json")
        file.deleteOnExit()
        file.writeText(exported)
        val (again, summary) = BackupImport.import(file, LibraryData())
        assertEquals(1, summary.playlists)
        assertEquals(1, again.playlists.size)
        assertEquals(1, again.playlists.single().trackIds.size)
    }
}
