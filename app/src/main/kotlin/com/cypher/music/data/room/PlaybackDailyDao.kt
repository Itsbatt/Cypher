package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackDailyDao {

    @Query(
        "INSERT INTO playback_daily (day, trackId, title, artist, artworkUrl, album, genre, listenDurationMs, playCount, lastListenedAt) " +
            "VALUES (:day, :trackId, :title, :artist, :artworkUrl, :album, :genre, :durationMs, CASE WHEN :countPlay THEN 1 ELSE 0 END, :timestamp) " +
            "ON CONFLICT(day, trackId) DO UPDATE SET " +
            "listenDurationMs = listenDurationMs + :durationMs, " +
            "playCount = playCount + CASE WHEN :countPlay THEN 1 ELSE 0 END, " +
            "lastListenedAt = :timestamp, " +
            "title = :title, " +
            "artist = :artist, " +
            "artworkUrl = COALESCE(:artworkUrl, artworkUrl), " +
            "album = COALESCE(:album, album), " +
            "genre = COALESCE(:genre, genre)"
    )
    fun recordDailyPlayback(
        day: String,
        trackId: String,
        title: String,
        artist: String,
        artworkUrl: String?,
        album: String?,
        genre: String?,
        durationMs: Long,
        timestamp: Long,
        countPlay: Boolean
    )

    @Query("SELECT COALESCE(SUM(listenDurationMs), 0) FROM playback_daily WHERE day >= :startDay AND day <= :endDay")
    fun getTotalListeningMs(startDay: String, endDay: String): Long

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM playback_daily WHERE day >= :startDay AND day <= :endDay")
    fun getTotalPlays(startDay: String, endDay: String): Int

    @Query(
        "SELECT trackId, title, artist, artworkUrl, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
            "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
            "GROUP BY trackId ORDER BY totalTimeMs DESC LIMIT :limit"
    )
    fun getTopTracks(startDay: String, endDay: String, limit: Int): List<TrackTuple>

    @Query(
        "SELECT trackId, title, artist, artworkUrl, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
            "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
            "GROUP BY trackId ORDER BY SUM(playCount) DESC, SUM(listenDurationMs) DESC LIMIT 1"
    )
    fun getMostRepeatedTrack(startDay: String, endDay: String): TrackTuple?

    @Query(
        "SELECT artist, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
            "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
            "GROUP BY artist ORDER BY totalTimeMs DESC LIMIT :limit"
    )
    fun getTopArtists(startDay: String, endDay: String, limit: Int): List<ArtistTuple>

    @Query(
        "SELECT album, MAX(artworkUrl) AS artworkUrl, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
            "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
            "AND album IS NOT NULL AND TRIM(album) <> '' " +
            "GROUP BY album ORDER BY totalTimeMs DESC LIMIT :limit"
    )
    fun getTopAlbums(startDay: String, endDay: String, limit: Int): List<AlbumTuple>

    @Query(
        "SELECT genre, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
            "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
            "AND genre IS NOT NULL AND TRIM(genre) <> '' " +
            "GROUP BY genre ORDER BY totalTimeMs DESC LIMIT :limit"
    )
    fun getTopGenres(startDay: String, endDay: String, limit: Int): List<GenreTuple>

    @Query(
        "SELECT day, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
            "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
            "GROUP BY day ORDER BY totalTimeMs DESC LIMIT 1"
    )
    fun getPeakDay(startDay: String, endDay: String): DayTuple?

    @Query("DELETE FROM playback_daily")
    fun clearAll()

    /** Every rollup row, used to seed a copy of an existing library. */
    @Query("SELECT * FROM playback_daily")
    fun getAllRows(): List<PlaybackDailyEntity>

    /**
     * Replaces a whole row with an already merged value. Import/merge must not add deltas
     * again, so this bypasses the accumulating [recordDailyPlayback] upsert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRow(row: PlaybackDailyEntity)

    data class TrackTuple(
        var trackId: String? = null,
        var title: String? = null,
        var artist: String? = null,
        var artworkUrl: String? = null,
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    data class ArtistTuple(
        var artist: String? = null,
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    data class AlbumTuple(
        var album: String? = null,
        var artworkUrl: String? = null,
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    data class GenreTuple(
        var genre: String? = null,
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    data class DayTuple(
        var day: String? = null,
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )
}
