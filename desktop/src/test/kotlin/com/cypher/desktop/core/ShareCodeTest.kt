package com.cypher.desktop.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareCodeTest {

    private val payload = ShareCode.Payload(
        name = "Noche en casa",
        description = "Para escuchar sin prisa",
        tracks = listOf(
            ShareCode.SharedTrack("Sincera Te", "Milo J", "Album Uno", 150_000, "yt_abc123", "LOCAL"),
            ShareCode.SharedTrack("Cae el Sol", "Airbag", "", 172_000, "yt_def456", "YOUTUBE")
        )
    )

    @Test
    fun `code survives a round trip`() {
        val code = ShareCode.encode(payload)
        assertTrue(code.startsWith("cypher://p2p?d="), "the QR needs the full link")
        val decoded = ShareCode.decode(code)
        assertEquals(payload.name, decoded.name)
        assertEquals(payload.description, decoded.description)
        assertEquals(2, decoded.tracks.size)
        assertEquals("yt_abc123", decoded.tracks.first().videoId)
        assertEquals(150_000, decoded.tracks.first().durationMs)
    }

    @Test
    fun `pasting the payload without the prefix also works`() {
        val code = ShareCode.encode(payload)
        val bare = code.removePrefix("cypher://p2p?d=")
        assertEquals("Noche en casa", ShareCode.decode(bare).name)
    }

    @Test
    fun `import matches songs already in the library`() {
        val data = LibraryData(
            remoteTracks = listOf(
                RemoteTrack(id = "yt_def456", videoId = "def456", title = "Cae el Sol", artist = "Airbag")
            )
        )
        val playlist = ShareCode.toPlaylist(payload, data)
        assertEquals(listOf("yt_def456"), playlist.trackIds)
        assertEquals("SHARED", playlist.origin)
        assertEquals(2, payload.tracks.size)
    }

    @Test
    fun `broken input is rejected instead of crashing`() {
        val error = runCatching { ShareCode.decode("cypher://p2p?d=no-es-un-codigo") }
        assertTrue(error.isFailure)
    }
}
