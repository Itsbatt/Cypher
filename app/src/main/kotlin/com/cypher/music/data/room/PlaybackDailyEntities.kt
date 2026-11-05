package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query

@Entity(
    tableName = "playback_daily",
    primaryKeys = ["day", "trackId"],
    indices = [
        Index(value = ["day"]),
        Index(value = ["trackId"])
    ]
)
data class PlaybackDailyEntity(
    var day: String = "",
    var trackId: String = "",
    var title: String = "",
    var artist: String = "",
    var artworkUrl: String? = null,
    var listenDurationMs: Long = 0L,
    var playCount: Int = 0,
    var lastListenedAt: Long = System.currentTimeMillis()
)

@Dao
interface PlaybackDailyDao {
    class TrackTuple(
        var trackId: String = "",
        var title: String = "",
        var artist: String = "",
        var artworkUrl: String? = null,
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    class ArtistTuple(
        var artist: String = "",
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    class DayTuple(
        var day: String = "",
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    @Query(
        "INSERT INTO playback_daily (day, trackId, title, artist, artworkUrl, listenDurationMs, playCount, lastListenedAt) " +
        "VALUES (:day, :trackId, :title, :artist, :artworkUrl, :durationMs, CASE WHEN :countPlay THEN 1 ELSE 0 END, :timestamp) " +
        "ON CONFLICT(day, trackId) DO UPDATE SET " +
        "listenDurationMs = listenDurationMs + :durationMs, " +
        "playCount = playCount + CASE WHEN :countPlay THEN 1 ELSE 0 END, " +
        "lastListenedAt = :timestamp, " +
        "title = :title, " +
        "artist = :artist, " +
        "artworkUrl = COALESCE(:artworkUrl, artworkUrl)"
    )
    fun recordDailyPlayback(
        day: String,
        trackId: String,
        title: String,
        artist: String,
        artworkUrl: String?,
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
        "SELECT artist, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
        "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
        "GROUP BY artist ORDER BY totalTimeMs DESC LIMIT :limit"
    )
    fun getTopArtists(startDay: String, endDay: String, limit: Int): List<ArtistTuple>

    @Query(
        "SELECT day, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
        "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
        "GROUP BY day ORDER BY totalTimeMs DESC LIMIT 1"
    )
    fun getPeakDay(startDay: String, endDay: String): DayTuple?

    @Query("DELETE FROM playback_daily")
    fun clearAll()
}
