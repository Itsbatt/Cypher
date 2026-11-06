package com.cypher.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRowPlaybackTest {

    @Test
    fun `only the selected track is current`() {
        assertTrue(TrackRowPlayback.isCurrent("yt_a", "yt_a"))
        assertFalse(TrackRowPlayback.isCurrent("yt_b", "yt_a"))
    }

    @Test
    fun `no current track when nothing is selected`() {
        assertFalse(TrackRowPlayback.isCurrent("yt_a", null))
    }

    @Test
    fun `paused current track is not actively playing`() {
        assertFalse(TrackRowPlayback.isActivelyPlaying("yt_a", "yt_a", isPlaying = false))
    }

    @Test
    fun `bars only for the track that is playing`() {
        assertTrue(TrackRowPlayback.isActivelyPlaying("yt_a", "yt_a", isPlaying = true))
        assertFalse(TrackRowPlayback.isActivelyPlaying("yt_b", "yt_a", isPlaying = true))
    }
}
