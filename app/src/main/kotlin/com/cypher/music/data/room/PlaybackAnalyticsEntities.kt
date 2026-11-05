package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "playback_analytics",
    indices = [
        Index(value = ["listenDurationMs"], orders = [Index.Order.DESC]),
        Index(value = ["artist"]),
        Index(value = ["lastListenedAt"], orders = [Index.Order.DESC])
    ]
)
data class PlaybackAnalyticsEntity(
    @PrimaryKey
    var trackId: String = "",
    var title: String = "",
    var artist: String = "",
    var artworkUrl: String? = null,
    var listenDurationMs: Long = 0L,
    var playCount: Int = 0,
    var lastListenedAt: Long = System.currentTimeMillis()
)

@Dao
interface PlaybackAnalyticsDao {
    class ArtistStatTuple(
        var artist: String = "",
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    class TrackStatTuple(
        var trackId: String = "",
        var title: String = "",
        var artist: String = "",
        var artworkUrl: String? = null,
        var totalTimeMs: Long = 0L,
        var playCount: Int = 0
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: PlaybackAnalyticsEntity)

    @Query(
        "INSERT INTO playback_analytics (trackId, title, artist, artworkUrl, listenDurationMs, playCount, lastListenedAt) " +
        "VALUES (:trackId, :title, :artist, :artworkUrl, :durationMs, CASE WHEN :countPlay THEN 1 ELSE 0 END, :timestamp) " +
        "ON CONFLICT(trackId) DO UPDATE SET " +
        "listenDurationMs = listenDurationMs + :durationMs, " +
        "playCount = playCount + CASE WHEN :countPlay THEN 1 ELSE 0 END, " +
        "lastListenedAt = :timestamp, " +
        "title = :title, " +
        "artist = :artist, " +
        "artworkUrl = COALESCE(:artworkUrl, artworkUrl)"
    )
    fun recordPlayback(
        trackId: String,
        title: String,
        artist: String,
        artworkUrl: String?,
        durationMs: Long,
        timestamp: Long,
        countPlay: Boolean
    )

    @Query("SELECT * FROM playback_analytics ORDER BY listenDurationMs DESC")
    fun getAllAnalytics(): List<PlaybackAnalyticsEntity>

    @Query(
        "SELECT artist, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
        "FROM playback_analytics GROUP BY artist ORDER BY totalTimeMs DESC LIMIT :limit"
    )
    fun getTopArtists(limit: Int): List<ArtistStatTuple>

    @Query(
        "SELECT trackId, title, artist, artworkUrl, listenDurationMs AS totalTimeMs, playCount " +
        "FROM playback_analytics ORDER BY listenDurationMs DESC LIMIT :limit"
    )
    fun getTopTracks(limit: Int): List<TrackStatTuple>

    @Query("SELECT COALESCE(SUM(listenDurationMs), 0) FROM playback_analytics")
    fun getTotalListeningMs(): Long

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM playback_analytics")
    fun getTotalPlays(): Int

    @Query("DELETE FROM playback_analytics")
    fun clearAll()

    @Query("DELETE FROM playback_analytics WHERE trackId LIKE 'local_demo_%' OR title LIKE '%Claro de Luna%' OR artist LIKE '%Beethoven%' OR artist LIKE '%Satie%' OR artist LIKE '%Chopin%'")
    fun deleteDemoAnalytics()
}
