package com.cypher.music.network.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The InnerTube fallback is the last thing standing between a broken yt-dlp and no
 * playback at all, so its format-selection rules are pinned here instead of being trusted
 * to whatever YouTube returns next.
 */
class InnertubeStreamParserTest {

    @Test
    fun `prefers the audio adaptive format over a video one`() {
        val body = """
            {"streamingData":{"adaptiveFormats":[
              {"mimeType":"video/mp4; codecs=\"avc1.64002a\"","url":"https://video.example/v"},
              {"mimeType":"audio/webm; codecs=\"opus\"","url":"https://audio.example/a"}
            ]}}
        """.trimIndent()

        assertEquals("https://audio.example/a", InnertubeStreamParser.audioUrl(body))
    }

    @Test
    fun `skips formats without a direct url`() {
        // SABR-locked responses omit url and expose a cipher instead; they are unusable here.
        val body = """
            {"streamingData":{"adaptiveFormats":[
              {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\""},
              {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","url":"https://audio.example/ok"}
            ]}}
        """.trimIndent()

        assertEquals("https://audio.example/ok", InnertubeStreamParser.audioUrl(body))
    }

    @Test
    fun `falls back to the legacy formats array`() {
        val body = """
            {"streamingData":{"formats":[{"url":"https://legacy.example/muxed"}]}}
        """.trimIndent()

        assertEquals("https://legacy.example/muxed", InnertubeStreamParser.audioUrl(body))
    }

    @Test
    fun `returns null when YouTube reports no streaming data`() {
        val body = """{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}"""

        assertNull(InnertubeStreamParser.audioUrl(body))
    }

    @Test
    fun `returns null instead of throwing on a body that is not JSON`() {
        assertNull(InnertubeStreamParser.audioUrl("<html>consent</html>"))
        assertNull(InnertubeStreamParser.audioUrl(""))
    }
}
