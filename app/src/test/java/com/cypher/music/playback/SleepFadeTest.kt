package com.cypher.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepFadeTest {

    @Test
    fun `no fade before the window opens`() {
        assertEquals(1f, SleepFade.volumeFraction(31), 0.0001f)
        assertEquals(1f, SleepFade.volumeFraction(600), 0.0001f)
        assertEquals(1f, SleepFade.volumeFraction(SleepFade.FADE_SECONDS), 0.0001f)
        assertFalse(SleepFade.isFading(31))
    }

    @Test
    fun `ramp is linear inside the last 30 seconds`() {
        assertEquals(1f, SleepFade.volumeFraction(30), 0.0001f)
        assertEquals(0.5f, SleepFade.volumeFraction(15), 0.0001f)
        assertEquals(0.1f, SleepFade.volumeFraction(3), 0.0001f)
        assertEquals(0.0333f, SleepFade.volumeFraction(1), 0.0001f)
    }

    @Test
    fun `volume is silent when the timer fires`() {
        assertEquals(0f, SleepFade.volumeFraction(0), 0.0001f)
        assertEquals(0f, SleepFade.volumeFraction(-5), 0.0001f)
    }

    @Test
    fun `ramp never increases while the timer runs down`() {
        var previous = 1f
        for (remaining in 30 downTo 1) {
            val fraction = SleepFade.volumeFraction(remaining.toLong())
            assertTrue("volume rose at ${remaining}s", fraction <= previous)
            previous = fraction
        }
        // One second left is the quietest step before the timer silences the player.
        assertEquals(1f / 30f, previous, 0.0001f)
    }

    @Test
    fun `window is configurable and can be disabled`() {
        assertTrue(SleepFade.isFading(5, fadeSeconds = 10))
        assertFalse(SleepFade.isFading(5, fadeSeconds = 0))
        assertEquals(1f, SleepFade.volumeFraction(5, fadeSeconds = 0), 0.0001f)
        assertEquals(0.5f, SleepFade.volumeFraction(5, fadeSeconds = 10), 0.0001f)
    }
}
