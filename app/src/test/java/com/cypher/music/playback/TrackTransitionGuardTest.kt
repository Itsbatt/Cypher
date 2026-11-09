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
    fun `pause before the requested video is loaded is ignored`() {
        // Regression: skipping from a downloaded song to a stream used to leave the
        // new track paused because the IFrame had not reported its video id yet.
        assertTrue(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = null, requestedVideoId = "newVideo"))
        assertTrue(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "", requestedVideoId = "newVideo"))
    }

    @Test
    fun `pause of the requested video is honored`() {
        assertFalse(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "newVideo", requestedVideoId = "newVideo"))
    }

    @Test
    fun `a pause the user asked for is never swallowed`() {
        assertFalse(
            TrackTransitionGuard.shouldIgnorePause("oldVideo", "newVideo", userPaused = true)
        )
        assertFalse(
            TrackTransitionGuard.shouldIgnorePause(null, "newVideo", userPaused = true)
        )
        assertFalse(
            TrackTransitionGuard.shouldIgnorePause("newVideo", "newVideo", userPaused = true)
        )
    }

    @Test
    fun `unknown requested id never swallows a pause`() {
        assertFalse(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "newVideo", requestedVideoId = null))
        assertFalse(TrackTransitionGuard.shouldIgnorePause(loadedVideoId = "", requestedVideoId = ""))
    }
}
