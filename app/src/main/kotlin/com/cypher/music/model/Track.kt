package com.cypher.music.model

enum class AudioSourceType {
    LOCAL,
    YOUTUBE
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Sencillo",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val streamUrl: String = "",
    val sourceType: AudioSourceType = AudioSourceType.LOCAL,
    val path: String? = null,
    val isLiked: Boolean = false,
    val isAvailable: Boolean = true,
    val isDownloaded: Boolean = false,
    // Real genre reported by the media store. Null when the platform has no tag,
    // so Wrapped can state honestly that there is no genre data.
    val genre: String? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
