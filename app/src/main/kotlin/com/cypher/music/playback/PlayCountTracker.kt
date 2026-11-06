package com.cypher.music.playback

/**
 * Decides when a play session should be counted.
 *
 * Rules (documented for the UI and the release notes):
 * - A session starts when [MusicPlayerManager.playTrack] loads a track.
 * - A play counts once the track has been audible for 30 s, or for half of its
 *   duration when the track is shorter than one minute.
 * - Skipping within the first second, or before the threshold, never counts.
 * - Pausing and resuming keeps the same session: it can only count once.
 * - Repeating a track (repeat-one or tapping it again) starts a new session, so
 *   every intentional replay counts separately.
 */
class PlayCountTracker(
    private val minAudibleMs: Long = DEFAULT_MIN_AUDIBLE_MS,
    private val minFractionOfTrack: Float = DEFAULT_MIN_FRACTION
) {
    companion object {
        const val DEFAULT_MIN_AUDIBLE_MS = 30_000L
        const val DEFAULT_MIN_FRACTION = 0.5f
    }

    private var sessionTrackId: String? = null
    private var audibleMs: Long = 0L
    private var counted = false

    fun startSession(trackId: String) {
        sessionTrackId = trackId
        audibleMs = 0L
        counted = false
    }

    /**
     * @return true exactly once per session, when this tick crosses the threshold.
     */
    fun onTick(trackId: String, positionMs: Long, durationMs: Long, elapsedMs: Long): Boolean {
        if (trackId != sessionTrackId) startSession(trackId)
        if (counted) return false
        audibleMs += elapsedMs.coerceAtLeast(0L)
        if (audibleMs < thresholdFor(durationMs)) return false
        counted = true
        return true
    }

    fun audibleMsInSession(): Long = audibleMs

    fun hasCounted(): Boolean = counted

    private fun thresholdFor(durationMs: Long): Long =
        if (durationMs > 0L) {
            minOf(minAudibleMs, (durationMs * minFractionOfTrack).toLong().coerceAtLeast(1L))
        } else {
            minAudibleMs
        }
}
