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
    /**
     * Cover actually used by the UI. When the provider omits the thumbnail (InnerTube
     * does this for some shelves) the real YouTube frame is derived from the track
     * id, so Now Playing never renders an empty square.
     */
    val displayArtworkUrl: String?
        get() {
            artworkUrl?.takeIf { it.isNotBlank() }?.let { return it }
            if (sourceType != AudioSourceType.YOUTUBE) return null
            val source = streamUrl.ifBlank { id }.removePrefix("yt_")
            val videoId = source
                .substringAfterLast("v=")
                .substringAfterLast("/")
                .substringBefore("?")
                .substringBefore("&")
                .trim()
            if (videoId.length < 6) return null
            return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
