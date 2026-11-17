package com.cypher.desktop.core

data class WrappedTrack(
    val rank: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val playCount: Int,
    val totalTimeMs: Long
)

data class WrappedArtist(
    val rank: Int,
    val name: String,
    val playCount: Int,
    val totalTimeMs: Long,
    val share: Float
)

data class WrappedPeakDay(val day: String, val totalTimeMs: Long)

data class WrappedSummary(
    val year: Int,
    val totalListeningMs: Long = 0,
    val totalPlays: Int = 0,
    val uniqueTracks: Int = 0,
    val topTracks: List<WrappedTrack> = emptyList(),
    val topArtists: List<WrappedArtist> = emptyList(),
    val mostRepeated: WrappedTrack? = null,
    val peakDay: WrappedPeakDay? = null
) {
    val hasData: Boolean get() = totalListeningMs > 0 || totalPlays > 0
    val topArtist: WrappedArtist? get() = topArtists.firstOrNull()
    val totalHours: Long get() = totalListeningMs / 3_600_000
}

/**
 * Same shape as Android's `WrappedStats`: top tracks and artists ranked by listening time,
 * artists carrying their share of the year, the most repeated track and the peak day.
 * It only reads what the sync already stores — no estimate, no padding.
 */
object WrappedBuilder {

    fun availableYears(stats: List<DailyStat>): List<Int> =
        stats.mapNotNull { it.day.take(4).toIntOrNull() }.distinct().sortedDescending()

    fun build(data: LibraryData, year: Int): WrappedSummary {
        val rows = data.stats.filter { it.day.startsWith(year.toString()) }
        if (rows.isEmpty()) return WrappedSummary(year = year)

        val totalMs = rows.sumOf { it.listenDurationMs }
        val totalPlays = rows.sumOf { it.playCount }

        val tracks = rows.groupBy { it.trackId }
            .map { (id, list) ->
                val first = list.first()
                TrackRollup(
                    id = id,
                    title = first.title.ifBlank { data.titleOf(id) },
                    artist = first.artist.ifBlank { data.artistOf(id) },
                    plays = list.sumOf { it.playCount },
                    totalMs = list.sumOf { it.listenDurationMs }
                )
            }
            .sortedWith(compareByDescending<TrackRollup> { it.totalMs }.thenByDescending { it.plays })
            .mapIndexed { index, rollup ->
                WrappedTrack(index + 1, rollup.id, rollup.title, rollup.artist, rollup.plays, rollup.totalMs)
            }

        val artists = rows.groupBy { it.artist.ifBlank { "Artista desconocido" } }
            .map { (name, list) ->
                val ms = list.sumOf { it.listenDurationMs }
                ArtistRollup(name, list.sumOf { it.playCount }, ms)
            }
            .sortedWith(compareByDescending<ArtistRollup> { it.totalMs }.thenByDescending { it.plays })
            .mapIndexed { index, rollup ->
                WrappedArtist(
                    rank = index + 1,
                    name = rollup.name,
                    playCount = rollup.plays,
                    totalTimeMs = rollup.totalMs,
                    share = if (totalMs > 0) rollup.totalMs.toFloat() / totalMs else 0f
                )
            }

        val peak = rows.groupBy { it.day }
            .mapValues { (_, list) -> list.sumOf { it.listenDurationMs } }
            .maxByOrNull { it.value }

        return WrappedSummary(
            year = year,
            totalListeningMs = totalMs,
            totalPlays = totalPlays,
            uniqueTracks = rows.map { it.trackId }.distinct().size,
            topTracks = tracks.take(10),
            topArtists = artists.take(10),
            mostRepeated = tracks.maxWithOrNull(compareBy<WrappedTrack> { it.playCount }.thenBy { it.totalTimeMs }),
            peakDay = peak?.let { WrappedPeakDay(it.key, it.value) }
        )
    }

    private data class TrackRollup(val id: String, val title: String, val artist: String, val plays: Int, val totalMs: Long)
    private data class ArtistRollup(val name: String, val plays: Int, val totalMs: Long)
}
