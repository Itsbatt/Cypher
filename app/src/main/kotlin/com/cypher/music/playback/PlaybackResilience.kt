package com.cypher.music.playback

/**
 * Pure playback policies extracted from [MusicPlayerManager] so they can be
 * unit tested on the JVM without an Android device.
 */

/** A stream URL plus the instant it was resolved, used to detect stale pre-caches. */
data class CachedStreamUrl(
    val url: String,
    val resolvedAtMs: Long
)

/**
 * YouTube stream URLs carry an expiry. Pre-caching minutes before the end of a
 * track is only useful while the URL is fresh, and a 403 must trigger a bounded
 * refresh instead of an endless retry loop.
 */
object StreamUrlPolicy {

    /** Conservative ceiling: refresh anything older than two hours. */
    const val DEFAULT_MAX_AGE_MS = 2 * 60 * 60 * 1000L

    /** Total resolution attempts (initial + one retry) before giving up. */
    const val MAX_RESOLUTION_ATTEMPTS = 2

    fun isFresh(
        resolvedAtMs: Long,
        nowMs: Long,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ): Boolean {
        if (resolvedAtMs <= 0L) return false
        if (nowMs < resolvedAtMs) return false
        return nowMs - resolvedAtMs <= maxAgeMs
    }

    /**
     * @param attempt zero-based attempt that just failed.
     * @return true when another resolution attempt is allowed.
     */
    fun shouldRetry(attempt: Int): Boolean = attempt < MAX_RESOLUTION_ATTEMPTS

    /** Small linear backoff before a refresh, kept testable and dependency-free. */
    fun retryDelayMs(attempt: Int): Long = 400L * (attempt + 1).coerceAtLeast(1)
}

/**
 * Replaces the old fixed 3000 ms transition window: a PAUSED event is only
 * ignored while the IFrame is still reporting the previous video.
 *
 * The IFrame reports PAUSED twice around a track change: when the previous
 * video is torn down and again in the gap before the requested video is
 * loaded ([loadedVideoId] is null there). Accepting either one as a real pause
 * is what left a streaming song stopped right after skipping from an
 * offline/downloaded one, so only a pause that reports the requested video, or
 * one the user asked for, counts.
 */
object TrackTransitionGuard {
    fun shouldIgnorePause(
        loadedVideoId: String?,
        requestedVideoId: String?,
        userPaused: Boolean = false
    ): Boolean {
        if (userPaused) return false
        if (requestedVideoId.isNullOrBlank()) return false
        if (loadedVideoId.isNullOrBlank()) return true
        return loadedVideoId != requestedVideoId
    }
}

/**
 * The buffering watchdog may force playback while the engine is loading, but it
 * must never override a pause the user requested during that load.
 */
object ResumePolicy {
    fun shouldForcePlay(
        userPausedDuringLoad: Boolean,
        isBuffering: Boolean,
        isPlaying: Boolean
    ): Boolean = !userPausedDuringLoad && (isBuffering || !isPlaying)
}
