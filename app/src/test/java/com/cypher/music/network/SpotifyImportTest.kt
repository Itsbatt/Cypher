package com.cypher.music.network

import com.cypher.music.data.SpotifyImporter
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyImportTest {

    @Test
    fun `parses share links uris and raw ids`() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyImportService.parsePlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc")
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyImportService.parsePlaylistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyImportService.parsePlaylistId("spotify:user:spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyImportService.parsePlaylistId("37i9dQZF1DXcBWIGoYBM5M")
        )
    }

    @Test
    fun `rejects unrelated input`() {
        assertNull(SpotifyImportService.parsePlaylistId(""))
        assertNull(SpotifyImportService.parsePlaylistId("https://youtube.com/watch?v=abc"))
        assertNull(SpotifyImportService.parsePlaylistId("hola"))
    }

    @Test
    fun `reads playlist metadata and tracks from embed payload`() {
        val html = """
            <html><head></head><body>
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{
              "name":"Mix de prueba",
              "subtitle":"Spotify",
              "coverArt":{"sources":[{"url":"https://i.scdn.co/image/cover"}]},
              "trackList":[
                {"title":"Stop The Wedding!","subtitle":"Ashe","duration":198840,"uri":"spotify:track:1"},
                {"title":"Otra","subtitle":"Artista B","duration":1000}
              ]
            }}}}}}</script>
            </body></html>
        """.trimIndent()

        val playlist = SpotifyImportService.parseEmbed(html, "abc123")

        assertNotNull(playlist)
        playlist!!
        assertEquals("Mix de prueba", playlist.name)
        assertEquals("abc123", playlist.id)
        assertEquals("https://i.scdn.co/image/cover", playlist.coverUrl)
        assertNull(playlist.owner)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Ashe", playlist.tracks[0].artist)
        assertEquals(198840L, playlist.tracks[0].durationMs)
        assertEquals("spotify:track:1", playlist.tracks[0].spotifyUri)
    }

    @Test
    fun `returns null when the embed payload has no playlist`() {
        assertNull(SpotifyImportService.parseEmbed("<html>sin datos</html>", "id"))
    }

    @Test
    fun `keeps the matching candidate well above an unrelated one`() {
        val reference = SpotifyTrackRef("Stop The Wedding!", "Ashe", 198840, null)
        val match = track("yt_stop", "Stop The Wedding!", "Ashe")
        val unrelated = track("yt_other", "Otra canción distinta", "Otro artista")

        val matchScore = SpotifyImporter.score(reference, match)
        val unrelatedScore = SpotifyImporter.score(reference, unrelated)

        assertTrue("match should clear the 0.45 threshold", matchScore >= 0.45)
        assertTrue("unrelated should stay below the threshold", unrelatedScore < 0.45)
        assertTrue(matchScore > unrelatedScore)
    }

    @Test
    fun `ignores youtube decorations when comparing`() {
        val reference = SpotifyTrackRef("Niño", "Milo J", 180000, null)
        val decorated = track("yt_nino", "Niño (Official Video)", "Milo J - Topic")
        assertTrue(SpotifyImporter.score(reference, decorated) >= 0.45)
    }

    private fun track(id: String, title: String, artist: String) = Track(
        id = id,
        title = title,
        artist = artist,
        durationMs = 198_000,
        sourceType = AudioSourceType.YOUTUBE
    )
}
