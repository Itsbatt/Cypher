package com.cypher.music.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun record(
        id: String,
        updatedAt: Long,
        deleted: Boolean = false,
        payload: Map<String, String> = emptyMap()
    ) = RemoteRecord(SyncEntityKind.PLAYLIST, id, updatedAt, deleted, payload)

    @Test
    fun `the newest record wins`() {
        val local = record("p1", updatedAt = 100)
        val remote = record("p1", updatedAt = 200)
        assertEquals(remote, SyncMerge.resolve(local, remote))
        assertEquals(local, SyncMerge.resolve(local, record("p1", updatedAt = 50)))
    }

    @Test
    fun `a live record beats a tombstone on a tie`() {
        val local = record("p1", updatedAt = 500)
        val remote = record("p1", updatedAt = 500, deleted = true)
        assertEquals(local, SyncMerge.resolve(local, remote))
    }

    @Test
    fun `playlist tracks are united keeping cloud order first`() {
        val merged = SyncMerge.mergePlaylistTracks(
            remoteOrder = listOf("a", "b", "c"),
            localOrder = listOf("b", "d", "a", "e")
        )
        assertEquals(listOf("a", "b", "c", "d", "e"), merged)
    }

    @Test
    fun `daily stats never inflate and are idempotent`() {
        val local = record(
            "2026-11-10|t1", updatedAt = 100,
            payload = mapOf(
                "listenDurationMs" to "600000",
                "playCount" to "3",
                "lastListenedAt" to "100",
                "album" to ""
            )
        )
        val remote = record(
            "2026-11-10|t1", updatedAt = 200,
            payload = mapOf(
                "listenDurationMs" to "400000",
                "playCount" to "5",
                "lastListenedAt" to "200",
                "album" to "Tus Vueltas"
            )
        )
        val once = SyncMerge.mergeDailyStats(local, remote)
        val twice = SyncMerge.mergeDailyStats(once, remote)
        assertEquals("600000", once.payload["listenDurationMs"])
        assertEquals("5", once.payload["playCount"])
        assertEquals("200", once.payload["lastListenedAt"])
        assertEquals("Tus Vueltas", once.payload["album"])
        assertEquals(once.payload, twice.payload)
    }

    @Test
    fun `a local row is pushed only when it is newer`() {
        val remote = record("p1", updatedAt = 300)
        assertTrue(SyncMerge.needsPush(record("p1", updatedAt = 400), remote))
        assertFalse(SyncMerge.needsPush(record("p1", updatedAt = 300), remote))
        assertFalse(SyncMerge.needsPush(record("p1", updatedAt = 200), remote))
        assertTrue(SyncMerge.needsPush(record("p1", updatedAt = 10), null))
    }
}
