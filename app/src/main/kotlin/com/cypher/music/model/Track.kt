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
    val isDownloaded: Boolean = false
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
