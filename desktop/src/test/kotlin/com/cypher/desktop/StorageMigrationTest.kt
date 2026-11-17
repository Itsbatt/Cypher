package com.cypher.desktop

import com.cypher.desktop.core.Storage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real v2 → v3 migration on a throwaway library.json.
 *
 * The document is the shape 1.4.0/1.5.0 wrote: local tracks, playlists, favourites,
 * cover art and the cloudTracks the phone used to publish. Nothing here reads the
 * user's own library: everything lives in a temporary folder.
 */
class StorageMigrationTest {

    private lateinit var dir: Path
    private lateinit var file: Path

    /** A 1.4.0/1.5.0 document, written by hand so the assertions are exact. */
    private val v2 = """
        {
          "version": 2,
          "tracks": [
            {
              "id": "a1", "title": "Uno", "artist": "Artista B", "album": "Álbum Uno",
              "durationMs": 1000, "path": "C:/música/uno.flac", "artwork": "C:/arte/uno.jpg",
              "addedAt": 100, "updatedAt": 300
            },
            {
              "id": "a2", "title": "Dos", "artist": "Artista A", "album": "Álbum Uno",
              "durationMs": 2000, "path": "C:/música/dos.flac", "artwork": null,
              "addedAt": 101, "updatedAt": 301
            }
          ],
          "playlists": [
            {
              "id": "p1", "name": "Noche", "description": "para dormir",
              "trackIds": ["a1", "a2"], "createdAt": 100, "updatedAt": 200,
              "artwork": "C:/arte/p1.jpg", "origin": "LOCAL"
            }
          ],
          "favorites": ["a1", "yt_dQw4w9WgXcQ"],
          "recent": ["a2", "a1"],
          "stats": [
            {
              "day": "2026-11-14", "trackId": "a1", "listenDurationMs": 4000,
              "playCount": 2, "title": "Uno", "artist": "Artista B",
              "album": "Álbum Uno", "lastListenedAt": 600
            }
          ],
          "cloudTracks": [
            {
              "id": "yt_dQw4w9WgXcQ", "title": "Remota", "artist": "Otro",
              "album": "Single", "durationMs": 200,
              "artworkUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq.jpg",
              "streamUrl": "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
              "sourceType": "YOUTUBE", "updatedAt": 500
            },
            {
              "id": "cloud-local-only", "title": "Sin identidad", "artist": "",
              "durationMs": 0, "sourceType": "LOCAL", "updatedAt": 501
            }
          ],
          "sync": { "cursor": "c-1", "lastSyncedAt": 1234 },
          "outbox": [ { "entityKey": "PLAYLIST:p1", "kind": "PLAYLIST" } ],
          "remoteThemeJson": { "accent": 123 },
          "profile": { "displayName": "Noche" },
          "libraryFolder": "C:/música"
        }
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cypher-migration-")
        Storage.rootOverride = dir
        file = dir.resolve("library.json")
        Files.writeString(file, v2)
    }

    @AfterTest
    fun tearDown() {
        Storage.rootOverride = null
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `migrating keeps every piece of local data`() {
        val data = Storage.load(file)

        assertEquals(3, data.version)
        assertEquals(2, data.tracks.size)
        assertEquals(listOf("Uno", "Dos"), data.tracks.map { it.title })
        assertEquals("C:/música/uno.flac", data.track("a1")?.path)
        // Portadas: la del tema y la de la playlist sobreviven.
        assertEquals("C:/arte/uno.jpg", data.track("a1")?.artwork)
        assertEquals("C:/arte/p1.jpg", data.playlist("p1")?.artwork)

        assertEquals(listOf("a1", "a2"), data.playlist("p1")?.trackIds)
        assertEquals("para dormir", data.playlist("p1")?.description)

        assertEquals(listOf("a1", "yt_dQw4w9WgXcQ"), data.favorites)
        assertTrue(data.isFavorite("yt_dQw4w9WgXcQ"))

        assertEquals(1, data.stats.size)
        assertEquals(4000, data.stats.first().listenDurationMs)
        assertEquals(2, data.stats.first().playCount)

        assertEquals(listOf("a2", "a1"), data.recent)
        assertEquals("Noche", data.profile.displayName)
        assertEquals("C:/música", data.libraryFolder)
    }

    @Test
    fun `cloudTracks become service references and never disappear silently`() {
        val data = Storage.load(file)

        val remote = data.remoteTrack("yt_dQw4w9WgXcQ")
        assertNotNull(remote, "la canción marcada en el móvil debe seguir existiendo como referencia")
        assertEquals("dQw4w9WgXcQ", remote!!.videoId)
        assertEquals("Remota", remote.title)
        assertEquals("Otro", remote.artist)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq.jpg", remote.artworkUrl)
        assertEquals("YOUTUBE", remote.source)
        assertTrue(remote.available)

        // Una fila sin identidad de servicio no tiene audio posible aquí: se descarta.
        assertNull(data.remoteTrack("cloud-local-only"))
        assertEquals(1, data.remoteTracks.size)
    }

    @Test
    fun `the document on disk loses the account keys and keeps version 3`() {
        Storage.load(file)

        val written = Files.readString(file)
        assertFalse(written.contains("\"cloudTracks\""), "cloudTracks no debe sobrevivir")
        assertFalse(written.contains("\"sync\""), "sync no debe sobrevivir")
        assertFalse(written.contains("\"outbox\""), "outbox no debe sobrevivir")
        assertFalse(written.contains("\"remoteThemeJson\""), "remoteThemeJson no debe sobrevivir")
        assertTrue(written.contains("\"version\": 3"), "el documento debe quedar en v3")
        assertTrue(written.contains("\"remoteTracks\""), "debe aparecer remoteTracks")
    }

    @Test
    fun `the bak copy restores the original document byte for byte`() {
        Storage.load(file)

        val backups = Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().startsWith("library-v2-") && it.fileName.toString().endsWith(".json.bak") }
                .toList()
        }
        assertEquals(1, backups.size)
        val backup = backups.first()
        assertEquals(v2, Files.readString(backup))

        // Recuperación real: se devuelve la copia a su sitio y se relee tal cual.
        Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING)
        assertEquals(v2, Files.readString(file))
        assertTrue(Files.readString(file).contains("\"cloudTracks\""))
    }

    @Test
    fun `loading twice is idempotent and leaves a single backup`() {
        val first = Storage.load(file)
        val second = Storage.load(file)

        assertEquals(first.tracks.map { it.id }, second.tracks.map { it.id })
        assertEquals(first.favorites, second.favorites)
        assertEquals(first.remoteTracks.map { it.id }, second.remoteTracks.map { it.id })

        val backups = Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().contains(".json.bak") }.toList()
        }
        assertEquals(1, backups.size, "no debe crearse una copia por cada apertura")
    }

    @Test
    fun `a v3 document is never rewritten`() {
        Storage.load(file)
        val migrated = Files.readString(file)
        Storage.load(file)
        assertEquals(migrated, Files.readString(file))
    }
}
