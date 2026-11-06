package com.cypher.music.playback

/**
 * Visual state of a track row. A row is "current" while its track is selected
 * (also when paused) but only "actively playing" when audio is really running,
 * which is what gates the animated equalizer bars.
 */
object TrackRowPlayback {
    fun isCurrent(rowTrackId: String, currentTrackId: String?): Boolean =
        currentTrackId != null && rowTrackId == currentTrackId

    fun isActivelyPlaying(rowTrackId: String, currentTrackId: String?, isPlaying: Boolean): Boolean =
        isPlaying && isCurrent(rowTrackId, currentTrackId)
}
