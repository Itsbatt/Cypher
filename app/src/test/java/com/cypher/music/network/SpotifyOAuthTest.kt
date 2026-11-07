package com.cypher.music.network

import com.cypher.music.data.SpotifyFailedTrack
import com.cypher.music.data.SpotifyTransferSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyOAuthTest {

    @Test
    fun `pkce challenge matches the rfc 7636 vector`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyAuth.challenge(verifier)
        )
    }

    @Test
    fun `verifier uses only url safe characters`() {
        val verifier = SpotifyAuth.createVerifier()
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun `authorize url carries pkce state and redirect`() {
        val url = SpotifyAuth.authorizeUrl("client123", "state-abc", "verifier-xyz")

        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=client123"))
        assertTrue(url.contains("state=state-abc"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("redirect_uri=cypher%3A%2F%2Fspotify-callback"))
        assertTrue(url.contains("scope=user-read-private"))
        assertTrue(url.contains("playlist-read-private"))
        assertTrue(url.contains("user-library-read"))
        assertTrue(url.contains("code_challenge=" + SpotifyAuth.challenge("verifier-xyz")))
    }

    @Test
    fun `parses a playlists page`() {
        val page = JSONObject(
            """
            {"items":[
              {"id":"p1","name":"Road trip","owner":{"display_name":"Ana"},
               "images":[{"url":"https://i.scdn.co/image/a"}],"tracks":{"total":321}},
              {"id":null,"name":"sin id"}
            ],"next":"https://api.spotify.com/v1/me/playlists?offset=50"}
            """.trimIndent()
        )

        val playlists = SpotifyApiClient.parsePlaylists(page)

        assertEquals(1, playlists.size)
        assertEquals("p1", playlists[0].id)
        assertEquals("Road trip", playlists[0].name)
        assertEquals("Ana", playlists[0].ownerName)
        assertEquals(321, playlists[0].trackCount)
        assertEquals("https://i.scdn.co/image/a", playlists[0].coverUrl)
        assertFalse(page.isNull("next"))
    }

    @Test
    fun `parses playlist items and skips episodes and removed tracks`() {
        val page = JSONObject(
            """
            {"items":[
              {"track":{"type":"track","name":"Uno","duration_ms":198840,
                "uri":"spotify:track:1","artists":[{"name":"A"},{"name":"B"}]}},
              {"track":null},
              {"track":{"type":"episode","name":"Podcast","duration_ms":100,"artists":[]}},
              {"track":{"type":"track","name":"Dos","duration_ms":1000,"artists":[{"name":"C"}]}}
            ],"next":null}
            """.trimIndent()
        )

        val tracks = SpotifyApiClient.parseTracks(page)

        assertEquals(2, tracks.size)
        assertEquals("Uno", tracks[0].title)
        assertEquals("A, B", tracks[0].artist)
        assertEquals(198840L, tracks[0].durationMs)
        assertEquals("spotify:track:1", tracks[0].spotifyUri)
        assertEquals("C", tracks[1].artist)
    }

    @Test
    fun `parses saved tracks page`() {
        val page = JSONObject(
            """
            {"items":[
              {"track":{"type":"track","name":"Guardada","duration_ms":120000,
                "uri":"spotify:track:9","artists":[{"name":"D"}]}}
            ],"next":null}
            """.trimIndent()
        )

        val tracks = SpotifyApiClient.parseSavedTracks(page)

        assertEquals(1, tracks.size)
        assertEquals("Guardada", tracks[0].title)
        assertEquals("D", tracks[0].artist)
    }

    @Test
    fun `summary export lists each failed song`() {
        val summary = SpotifyTransferSummary(
            playlists = 2,
            importedTracks = 120,
            failed = listOf(
                SpotifyFailedTrack("Mix", "Perdida", "Artista X"),
                SpotifyFailedTrack("Me gusta", "Otra", "Artista Y")
            )
        )

        val text = summary.exportText()

        assertTrue(text.contains("Transferidas: 120 canciones en 2 playlists"))
        assertTrue(text.contains("No encontradas: 2"))
        assertTrue(text.contains("Perdida — Artista X  [Mix]"))
        assertTrue(text.contains("Otra — Artista Y  [Me gusta]"))
    }

    @Test
    fun `summary without failures has no bullet list`() {
        val summary = SpotifyTransferSummary(playlists = 1, importedTracks = 3)
        assertFalse(summary.exportText().contains("•"))
        assertEquals(0, summary.failedCount)
    }
}
