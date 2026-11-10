package com.cypher.music.playback

/**
 * Volume ramp of the countdown sleep timer. Pure on purpose: the curve is unit
 * tested, and the player applies it through the same master-volume path as the
 * fade-in, so it works identically for streams, downloads and local files.
 */
object SleepFade {
    /** How long the ramp lasts before the timer pauses playback. */
    const val FADE_SECONDS = 30L

    /**
     * Master-volume multiplier for a given remaining time: 1.0 outside the fade
     * window, a linear descent to 0 at the moment the timer fires.
     */
    fun volumeFraction(remainingSeconds: Long, fadeSeconds: Long = FADE_SECONDS): Float {
        if (fadeSeconds <= 0L) return 1f
        if (remainingSeconds <= 0L) return 0f
        if (remainingSeconds >= fadeSeconds) return 1f
        return (remainingSeconds.toFloat() / fadeSeconds.toFloat()).coerceIn(0f, 1f)
    }

    /** True while the timer is inside its fade window. */
    fun isFading(remainingSeconds: Long, fadeSeconds: Long = FADE_SECONDS): Boolean =
        volumeFraction(remainingSeconds, fadeSeconds) < 1f
}
