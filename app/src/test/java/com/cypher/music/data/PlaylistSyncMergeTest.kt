package com.cypher.music.data

import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSyncMergeTest {

    private fun track(id: String) = Track(
        id = id,
        title = "Canción $id",
        artist = "Artista",
        sourceType = AudioSourceType.YOUTUBE
    )

    @Test
    fun `remote order is kept and local additions survive`() {
        val remote = listOf(track("a"), track("b"), track("c"))
        val existing = listOf(track("c"), track("local1"), track("a"))

        val merged = PlaylistSync.mergeHybrid(remote, existing)

        assertEquals(listOf("a", "b", "c", "local1"), merged.map { it.id })
    }

    @Test
    fun `duplicates are removed on both sides`() {
        val remote = listOf(track("a"), track("a"), track("b"))
        val existing = listOf(track("local"), track("local"))

        val merged = PlaylistSync.mergeHybrid(remote, existing)

        assertEquals(listOf("a", "b", "local"), merged.map { it.id })
    }

    @Test
    fun `a mirror that matches the remote has no extras`() {
        val remote = listOf(track("a"), track("b"))
        val existing = listOf(track("a"), track("b"))

        assertEquals(remote.map { it.id }, PlaylistSync.mergeHybrid(remote, existing).map { it.id })
    }

    @Test
    fun `an empty remote page keeps what the user added`() {
        val merged = PlaylistSync.mergeHybrid(emptyList(), listOf(track("local")))

        assertEquals(listOf("local"), merged.map { it.id })
    }

    @Test
    fun `sync state maps the completeness of the import`() {
        assertEquals(PlaylistSync.COMPLETE, PlaylistSync.stateFor(true))
        assertEquals(PlaylistSync.INCOMPLETE, PlaylistSync.stateFor(false))

        val synced = Playlist(
            id = "p1",
            name = "Sincronizada",
            tracks = emptyList(),
            syncState = PlaylistSync.INCOMPLETE
        )
        assertTrue(synced.isSynced)
        assertTrue(synced.isSyncIncomplete)

        val local = Playlist(id = "p2", name = "Local", tracks = emptyList())
        assertFalse(local.isSynced)
    }
}
