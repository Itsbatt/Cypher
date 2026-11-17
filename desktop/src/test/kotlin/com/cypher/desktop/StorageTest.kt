package com.cypher.desktop

import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.Playlist
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.Track
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageTest {

    @Test
    fun `library survives a save and reload`() {
        val dir = Files.createTempDirectory("cypher-store")
        val file = dir.resolve("library.json")
        val data = LibraryData(
            tracks = listOf(Track("t1", "Canción", "Artista", "Álbum", 180_000, "C:/m/1.mp3")),
            playlists = listOf(Playlist("p1", "Noche", trackIds = listOf("t1"), createdAt = 1, updatedAt = 2)),
            favorites = listOf("t1"),
            libraryFolder = "C:/m"
        )
        Storage.save(data, file)
        assertTrue(Files.exists(file))
        assertEquals(data, Storage.load(file))
        assertTrue(!Files.exists(dir.resolve("library.json.tmp")), "el temporal no debe quedar")
    }

    @Test
    fun `missing file yields an empty library instead of failing`() {
        val file = Files.createTempDirectory("cypher-empty").resolve("nope.json")
        assertEquals(LibraryData(), Storage.load(file))
    }

    @Test
    fun `corrupt file does not crash the app`() {
        val file = Files.createTempDirectory("cypher-bad").resolve("library.json")
        Files.writeString(file, "{ esto no es json")
        assertEquals(LibraryData(), Storage.load(file))
    }
}
