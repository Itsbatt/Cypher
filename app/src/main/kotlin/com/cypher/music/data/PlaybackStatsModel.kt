package com.cypher.music.data

data class TopArtistStat(
    val artist: String,
    val totalTimeMs: Long,
    val playCount: Int
) {
    val formattedTime: String
        get() {
            val totalSeconds = totalTimeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

data class TopTrackStat(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val totalTimeMs: Long,
    val playCount: Int
)

data class UserListeningStats(
    val totalListeningMs: Long = 0L,
    val totalPlays: Int = 0,
    val topArtists: List<TopArtistStat> = emptyList(),
    val topTracks: List<TopTrackStat> = emptyList()
) {
    val formattedTotalTime: String
        get() {
            val totalSeconds = totalListeningMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
}
