package com.cypher.music.data

import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = true,
    val tracks: List<Track> = emptyList()
) {
    val trackCount: Int get() = tracks.size
    val formattedTrackCount: String get() = trackCount.toTrackCountString()
    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }

    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            return if (hours > 0) {
                val remMin = minutes % 60
                "${hours} h ${remMin} min"
            } else {
                "${minutes} min"
            }
        }

    /**
     * Returns up to 4 distinct artworks from the playlist tracks to generate a cover mosaic
     * if no custom cover was specified.
     */
    val mosaicArtworks: List<String>
        get() = tracks.mapNotNull { it.artworkUrl }.filter { it.isNotBlank() }.distinct().take(4)
}

fun Int.toTrackCountString(): String = if (this == 1) "1 canción" else "$this canciones"

data class UnifiedSearchResults(
    val localTracks: List<Track> = emptyList(),
    val localPlaylists: List<Playlist> = emptyList(),
    val globalTracks: List<Track> = emptyList()
) {
    val hasLocalMatches: Boolean get() = localTracks.isNotEmpty() || localPlaylists.isNotEmpty()
    val isEmpty: Boolean get() = localTracks.isEmpty() && localPlaylists.isEmpty() && globalTracks.isEmpty()
}

enum class RecentlyPlayedType {
    TRACK,
    ALBUM,
    PLAYLIST
}

data class RecentlyPlayedItem(
    val id: String,
    val type: RecentlyPlayedType,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val streamUrl: String = "",
    val sourceType: AudioSourceType = AudioSourceType.LOCAL,
    val extraId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class BackupImportSummary(
    val playlistsCount: Int = 0,
    val tracksCount: Int = 0,
    val favoritesCount: Int = 0,
    val analyticsCount: Int = 0,
    val success: Boolean = true,
    val errorMessage: String? = null
)
