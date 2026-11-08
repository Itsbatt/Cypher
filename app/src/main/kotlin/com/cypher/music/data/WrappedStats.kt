package com.cypher.music.data

import java.time.LocalDate

data class WrappedTrackStat(
    val rank: Int,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val playCount: Int,
    val totalTimeMs: Long
)

data class WrappedArtistStat(
    val rank: Int,
    val name: String,
    val playCount: Int,
    val totalTimeMs: Long,
    /** Share of the year's listening time, 0f..1f. Zero when there is no time recorded. */
    val share: Float = 0f
)

data class WrappedAlbumStat(
    val rank: Int,
    val name: String,
    val artworkUrl: String?,
    val playCount: Int,
    val totalTimeMs: Long
)

data class WrappedGenreStat(
    val rank: Int,
    val name: String,
    val playCount: Int,
    val totalTimeMs: Long,
    val share: Float = 0f
)

data class WrappedPeakDay(
    val day: LocalDate?,
    val totalTimeMs: Long
)

data class WrappedStats(
    val year: Int = LocalDate.now().year,
    val totalListeningMs: Long = 0L,
    val totalPlays: Int = 0,
    val topTracks: List<WrappedTrackStat> = emptyList(),
    val topArtists: List<WrappedArtistStat> = emptyList(),
    val topAlbums: List<WrappedAlbumStat> = emptyList(),
    val topGenres: List<WrappedGenreStat> = emptyList(),
    val mostRepeatedTrack: WrappedTrackStat? = null,
    val peakDay: WrappedPeakDay? = null
) {
    val totalMinutes: Long get() = totalListeningMs / 60_000L

    val hasData: Boolean get() = totalListeningMs > 0L || topTracks.isNotEmpty()

    val topArtist: WrappedArtistStat? get() = topArtists.firstOrNull()

    val topAlbum: WrappedAlbumStat? get() = topAlbums.firstOrNull()

    /** True when the provider never reported genre tags, so the UI can say so. */
    val hasGenreData: Boolean get() = topGenres.isNotEmpty()
}
