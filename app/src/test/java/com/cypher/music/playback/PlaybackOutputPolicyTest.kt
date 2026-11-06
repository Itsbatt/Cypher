package com.cypher.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOutputPolicyTest {

    @Test
    fun `local files play through exoplayer`() {
        assertTrue(PlaybackOutputPolicy.usesExoPlayer(isLocalSource = true, isOfflineDownload = false))
    }

    @Test
    fun `downloaded youtube tracks play through exoplayer`() {
        assertTrue(PlaybackOutputPolicy.usesExoPlayer(isLocalSource = false, isOfflineDownload = true))
    }

    @Test
    fun `streaming youtube tracks stay on the iframe`() {
        assertFalse(PlaybackOutputPolicy.usesExoPlayer(isLocalSource = false, isOfflineDownload = false))
    }

    @Test
    fun `local track that is also downloaded stays on exoplayer`() {
        assertTrue(PlaybackOutputPolicy.usesExoPlayer(isLocalSource = true, isOfflineDownload = true))
    }
}
