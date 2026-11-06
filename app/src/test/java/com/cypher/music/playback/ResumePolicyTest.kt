package com.cypher.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePolicyTest {

    @Test
    fun `watchdog may force play while loading`() {
        assertTrue(ResumePolicy.shouldForcePlay(userPausedDuringLoad = false, isBuffering = true, isPlaying = false))
    }

    @Test
    fun `user pause wins over a slow load`() {
        assertFalse(ResumePolicy.shouldForcePlay(userPausedDuringLoad = true, isBuffering = true, isPlaying = false))
        assertFalse(ResumePolicy.shouldForcePlay(userPausedDuringLoad = true, isBuffering = false, isPlaying = false))
    }

    @Test
    fun `playing state needs no intervention`() {
        assertFalse(ResumePolicy.shouldForcePlay(userPausedDuringLoad = false, isBuffering = false, isPlaying = true))
    }
}
