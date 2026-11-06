package com.cypher.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResiliencePolicyTest {

    @Test
    fun `fresh urls are reusable`() {
        val now = 1_000_000L
        assertTrue(StreamUrlPolicy.isFresh(now - 60_000L, now))
        assertTrue(StreamUrlPolicy.isFresh(now, now))
    }

    @Test
    fun `stale urls must be re-resolved`() {
        val now = 10_000_000L
        assertFalse(StreamUrlPolicy.isFresh(now - StreamUrlPolicy.DEFAULT_MAX_AGE_MS - 1, now))
    }

    @Test
    fun `invalid timestamps are never fresh`() {
        assertFalse(StreamUrlPolicy.isFresh(0L, 1_000L))
        // A clock that moved backwards must not trust the cached url.
        assertFalse(StreamUrlPolicy.isFresh(5_000L, 1_000L))
    }

    @Test
    fun `retry is bounded`() {
        assertTrue(StreamUrlPolicy.shouldRetry(0))
        assertTrue(StreamUrlPolicy.shouldRetry(1))
        assertFalse(StreamUrlPolicy.shouldRetry(2))
        assertFalse(StreamUrlPolicy.shouldRetry(3))
    }

    @Test
    fun `retry delay grows with attempts`() {
        assertTrue(StreamUrlPolicy.retryDelayMs(1) > StreamUrlPolicy.retryDelayMs(0))
        assertEquals(400L, StreamUrlPolicy.retryDelayMs(0))
    }
}
