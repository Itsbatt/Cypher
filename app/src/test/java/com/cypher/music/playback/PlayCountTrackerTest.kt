package com.cypher.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayCountTrackerTest {

    private fun PlayCountTracker.feed(trackId: String, seconds: Int, durationMs: Long = 180_000L) {
        repeat(seconds) {
            onTick(trackId, positionMs = it * 1000L, durationMs = durationMs, elapsedMs = 1000L)
        }
    }

    @Test
    fun `skipping in the first second is not a play`() {
        val tracker = PlayCountTracker()
        tracker.startSession("a")
        assertFalse(tracker.onTick("a", 0L, 180_000L, 900L))
        assertFalse(tracker.hasCounted())
    }

    @Test
    fun `thirty audible seconds count exactly once`() {
        val tracker = PlayCountTracker()
        tracker.startSession("a")
        tracker.feed("a", 29)
        assertFalse(tracker.hasCounted())
        assertTrue(tracker.onTick("a", 29_000L, 180_000L, 1_000L))
        assertFalse(tracker.onTick("a", 30_000L, 180_000L, 1_000L))
    }

    @Test
    fun `short tracks count at half their duration`() {
        val tracker = PlayCountTracker()
        tracker.startSession("short")
        tracker.feed("short", seconds = 9, durationMs = 20_000L)
        assertFalse(tracker.hasCounted())
        assertTrue(tracker.onTick("short", 9_000L, 20_000L, 1_000L))
    }

    @Test
    fun `repeat starts a fresh session and counts again`() {
        val tracker = PlayCountTracker()
        tracker.startSession("a")
        tracker.feed("a", 30)
        assertTrue(tracker.hasCounted())
        tracker.startSession("a")
        tracker.feed("a", 29)
        assertFalse(tracker.hasCounted())
        assertTrue(tracker.onTick("a", 29_000L, 180_000L, 1_000L))
    }

    @Test
    fun `switching tracks resets the session`() {
        val tracker = PlayCountTracker()
        tracker.startSession("a")
        tracker.feed("a", 20)
        tracker.startSession("b")
        tracker.feed("b", 29)
        assertFalse(tracker.hasCounted())
    }
}
