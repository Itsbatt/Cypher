package com.cypher.music.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyExportTest {

    @Test
    fun `parses exportify csv with quoted commas`() {
        val csv = """
            Track URI,Track Name,Artist Name(s),Album Name,Track Duration (ms)
            spotify:track:1,"Uno, dos","A; B",Album,198840
            spotify:track:2,Dos,C,Album,1000
        """.trimIndent() + "\r\n"

        val playlists = SpotifyExportParser.parse(csv, "Mi lista")

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
}
