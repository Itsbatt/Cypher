package com.cypher.music.network.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxiedStreamParserTest {

    @Test
    fun `parsePiped selects audio stream with highest bitrate`() {
        val body = """
            {
              "audioStreams": [
                {"url": "https://piped.example/audio_low", "bitrate": 64000, "mimeType": "audio/mp4"},
                {"url": "https://piped.example/audio_high", "bitrate": 160000, "mimeType": "audio/webm"},
                {"url": "https://piped.example/audio_med", "bitrate": 128000, "mimeType": "audio/mp4"}
              ]
            }
        """.trimIndent()

        assertEquals("https://piped.example/audio_high", ProxiedStreamParser.parsePiped(body))
    }

    @Test
    fun `parsePiped skips streams without valid http url`() {
        val body = """
            {
              "audioStreams": [
                {"url": "invalid://not-http", "bitrate": 320000},
                {"url": "", "bitrate": 256000},
                {"url": "https://piped.example/valid", "bitrate": 128000}
              ]
            }
        """.trimIndent()

        assertEquals("https://piped.example/valid", ProxiedStreamParser.parsePiped(body))
    }

    @Test
    fun `parseInvidious selects audio adaptive format with highest bitrate`() {
        val body = """
            {
              "adaptiveFormats": [
                {"type": "video/mp4; codecs=\"avc1\"", "bitrate": "2000000", "url": "https://invidious.example/video"},
                {"type": "audio/webm; codecs=\"opus\"", "bitrate": "160000", "url": "https://invidious.example/audio_high"},
                {"type": "audio/mp4; codecs=\"mp4a\"", "bitrate": "128000", "url": "https://invidious.example/audio_med"}
              ]
            }
        """.trimIndent()

        assertEquals("https://invidious.example/audio_high", ProxiedStreamParser.parseInvidious(body))
    }

    @Test
    fun `parseInvidious falls back to formatStreams when adaptiveFormats has no audio`() {
        val body = """
            {
              "adaptiveFormats": [
                {"type": "video/mp4", "url": "https://invidious.example/video"}
              ],
              "formatStreams": [
                {"url": "https://invidious.example/muxed_audio"}
              ]
            }
        """.trimIndent()

        assertEquals("https://invidious.example/muxed_audio", ProxiedStreamParser.parseInvidious(body))
    }

    @Test
    fun `parse auto detects Piped source`() {
        val body = """{"audioStreams": [{"url": "https://piped.example/a", "bitrate": 128000}]}"""
        val result = ProxiedStreamParser.parse(body)
        assertEquals("https://piped.example/a", result?.first)
        assertEquals(StreamSource.PIPED, result?.second)
    }

    @Test
    fun `parse auto detects Invidious source`() {
        val body = """{"adaptiveFormats": [{"type": "audio/webm", "bitrate": "160000", "url": "https://invidious.example/a"}]}"""
        val result = ProxiedStreamParser.parse(body)
        assertEquals("https://invidious.example/a", result?.first)
        assertEquals(StreamSource.INVIDIOUS, result?.second)
    }

    @Test
    fun `returns null on non-json or empty input`() {
        assertNull(ProxiedStreamParser.parsePiped("<html>502 Bad Gateway</html>"))
        assertNull(ProxiedStreamParser.parseInvidious(""))
        assertNull(ProxiedStreamParser.parse("{}"))
    }
}
