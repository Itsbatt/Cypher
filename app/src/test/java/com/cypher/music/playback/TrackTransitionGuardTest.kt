package com.cypher.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTransitionGuardTest {

    @Test
    fun `pause reported for the previous video is ignored`() {
        assertTrue(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "oldVideo", requestedVideoId = "newVideo"))
    }

    @Test
    fun `pause of the requested video is honored`() {
        assertFalse(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "newVideo", requestedVideoId = "newVideo"))
    }

    @Test
    fun `unknown ids never swallow a pause`() {
        assertFalse(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = null, requestedVideoId = "newVideo"))
        assertFalse(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "newVideo", requestedVideoId = null))
        assertFalse(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "", requestedVideoId = ""))
    }
}
