package com.cypher.music.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyExportTest {

    private val header = "Track URI,Track Name,Artist Name(s),Album Name,Track Duration (ms)\n"

    private fun csv(vararg songs: String): String =
        header + songs.joinToString("\n") { "$it" } + "\n"

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun parseZip(bytes: ByteArray) =
        SpotifyExportParser.parseZip(ByteArrayInputStream(bytes), "prueba.zip")

    @Test
    fun `parses exportify csv with quoted commas`() {
        val content = """
            Track URI,Track Name,Artist Name(s),Album Name,Track Duration (ms)
            spotify:track:1,"Uno, dos","A; B",Album,198840
            spotify:track:2,Dos,C,Album,1000
        """.trimIndent() + "\r\n"

        val playlists = SpotifyExportParser.parse(content, "Mi lista")

        assertEquals(1, playlists.size)
        assertEquals("Mi lista", playlists[0].name)
        assertEquals(2, playlists[0].tracks.size)
        assertEquals("Uno, dos", playlists[0].tracks[0].title)
        assertEquals("A, B", playlists[0].tracks[0].artist)
        assertEquals(198840L, playlists[0].tracks[0].durationMs)
        assertEquals("spotify:track:1", playlists[0].tracks[0].spotifyUri)
        assertEquals("Dos", playlists[0].tracks[1].title)
    }

    @Test
    fun `handles escaped quotes inside cells`() {
        val cells = SpotifyExportParser.splitCsvLine(
            "spotify:track:1,\"Canción \"\"especial\"\"\",Artista,Album,1000"
        )

        assertEquals(5, cells.size)
        assertEquals("Canción \"especial\"", cells[1])
    }

    @Test
    fun `parses spotify data dump with several playlists`() {
        val json = """
            {"playlists":[
              {"name":"Favoritas","items":[
                 {"track":{"trackName":"Uno","artistName":"A","trackUri":"spotify:track:1","duration_ms":198840}},
                 {"track":{"trackName":"Dos","artistName":"B","trackUri":"spotify:track:2"}}
              ]},
              {"name":"Vacía","items":[]},
              {"name":"Me gusta","items":[
                 {"track":{"trackName":"Tres","artistName":"C"}}
              ]}
            ]}
        """.trimIndent()

        val playlists = SpotifyExportParser.parse(json, "archivo")

        assertEquals(2, playlists.size)
        assertEquals("Favoritas", playlists[0].name)
        assertEquals(2, playlists[0].tracks.size)
        assertEquals(198840L, playlists[0].tracks[0].durationMs)
        assertEquals("Me gusta", playlists[1].name)
        assertEquals("Tres", playlists[1].tracks[0].title)
    }

    @Test
    fun `parses a single playlist object`() {
        val json = """
            {"name":"Sola","tracks":[
              {"track":{"name":"Uno","artists":[{"name":"A"},{"name":"B"}]}}
            ]}
        """.trimIndent()

        val playlists = SpotifyExportParser.parse(json, "archivo")

        assertEquals(1, playlists.size)
        assertEquals("Sola", playlists[0].name)
        assertEquals("A, B", playlists[0].tracks[0].artist)
    }

    @Test
    fun `returns nothing for empty or unknown content`() {
        assertTrue(SpotifyExportParser.parse("", "x").isEmpty())
        assertTrue(SpotifyExportParser.parse("hola que tal", "x").isEmpty())
        assertTrue(SpotifyExportParser.parse("{}", "x").isEmpty())
    }

    @Test
    fun `accepts a byte order mark before the header`() {
        val csv = "\uFEFFTrack Name,Artist Name(s),Track Duration (ms)\nUno,A,1000\n"
        val playlists = SpotifyExportParser.parse(csv, "con bom")
        assertEquals(1, playlists.size)
        assertEquals("Uno", playlists[0].tracks[0].title)
    }

    // ZIP support

    @Test
    fun `reads every csv playlist inside a zip`() {
        val bytes = zipOf(
            "Rock.csv" to csv("spotify:track:1,Rock Uno,A,Album,1000"),
            "Pop.csv" to csv(
                "spotify:track:2,Pop Uno,B,Album,2000",
                "spotify:track:3,Pop Dos,C,Album,3000"
            ),
            "cover.jpg" to "not really a jpeg",
            "__MACOSX/._Rock.csv" to "metadata"
        )

        val result = parseZip(bytes)

        assertEquals(2, result.playlists.size)
        assertEquals("Rock", result.playlists[0].name)
        assertEquals(1, result.playlists[0].tracks.size)
        assertEquals("Pop", result.playlists[1].name)
        assertEquals(2, result.playlists[1].tracks.size)
        assertTrue(result.issues.isEmpty())
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `skips duplicate playlist names inside the zip`() {
        val bytes = zipOf(
            "Rock.csv" to csv("spotify:track:1,Uno,A,Album,1000"),
            "ROCK.csv" to csv("spotify:track:2,Dos,B,Album,1000"),
            " rock .csv" to csv("spotify:track:3,Tres,C,Album,1000")
        )

        val result = parseZip(bytes)

        assertEquals(1, result.playlists.size)
        assertEquals("Uno", result.playlists[0].tracks[0].title)
        assertEquals(2, result.duplicates.size)
    }

    @Test
    fun `reports unreadable files without cancelling the rest`() {
        val bytes = zipOf(
            "Rota.csv" to "esto no es un csv",
            "Buena.csv" to csv("spotify:track:1,Uno,A,Album,1000"),
            "Vacia.csv" to ""
        )

        val result = parseZip(bytes)

        assertEquals(1, result.playlists.size)
        assertEquals("Buena", result.playlists[0].name)
        assertEquals(2, result.issues.size)
        assertTrue(result.issues.any { it.contains("Rota.csv") })
        assertTrue(result.issues.any { it.contains("Vacia.csv") })
    }

    @Test
    fun `reads spotify json playlists inside a zip and ignores metadata`() {
        val json = """
            {"playlists":[
              {"name":"Favoritas","items":[{"track":{"trackName":"Uno","artistName":"A"}}]},
              {"name":"Otra","items":[{"track":{"trackName":"Dos","artistName":"B"}}]}
            ]}
        """.trimIndent()

        val bytes = zipOf(
            "Playlist1.json" to json,
            "Userdata.json" to """{"username":"x","email":"x@example.com"}""",
            "Readme.txt" to "hola"
        )

        val result = parseZip(bytes)

        assertEquals(2, result.playlists.size)
        assertEquals("Favoritas", result.playlists[0].name)
        assertEquals("Otra", result.playlists[1].name)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `imports a zip with many playlists`() {
        val entries = (1..40).map { playlist ->
            val songs = (1..25).map { song ->
                "spotify:track:$playlist-$song,Cancion $song,Artista $playlist,Album,1000"
            }
            "Playlist $playlist.csv" to csv(*songs.toTypedArray())
        }.toTypedArray()

        val result = parseZip(zipOf(*entries))

        assertEquals(40, result.playlists.size)
        assertEquals(1_000, result.playlists.sumOf { it.tracks.size })
        assertEquals("Playlist 40", result.playlists.last().name)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `rejects a zip that is not really a zip`() {
        val result = SpotifyExportParser.parseZip(
            ByteArrayInputStream("PK no es un zip".toByteArray()),
            "roto.zip"
        )

        assertTrue(result.playlists.isEmpty())
        assertTrue(result.issues.isNotEmpty())
    }
}
