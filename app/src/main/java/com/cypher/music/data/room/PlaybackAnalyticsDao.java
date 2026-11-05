package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlaybackAnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PlaybackAnalyticsEntity entity);

    @Query("INSERT INTO playback_analytics (trackId, title, artist, artworkUrl, listenDurationMs, playCount, lastListenedAt) " +
           "VALUES (:trackId, :title, :artist, :artworkUrl, :durationMs, 1, :timestamp) " +
           "ON CONFLICT(trackId) DO UPDATE SET " +
           "listenDurationMs = listenDurationMs + :durationMs, " +
           "playCount = playCount + 1, " +
           "lastListenedAt = :timestamp, " +
           "title = :title, " +
           "artist = :artist, " +
           "artworkUrl = COALESCE(:artworkUrl, artworkUrl)")
    void recordListeningDuration(String trackId, String title, String artist, String artworkUrl, long durationMs, long timestamp);

    @Query("SELECT * FROM playback_analytics ORDER BY listenDurationMs DESC")
    List<PlaybackAnalyticsEntity> getAllAnalytics();

    @Query("SELECT artist, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_analytics GROUP BY artist ORDER BY totalTimeMs DESC LIMIT :limit")
    List<ArtistStatTuple> getTopArtists(int limit);

    @Query("SELECT trackId, title, artist, artworkUrl, listenDurationMs AS totalTimeMs, playCount " +
           "FROM playback_analytics ORDER BY listenDurationMs DESC LIMIT :limit")
    List<TrackStatTuple> getTopTracks(int limit);

    @Query("SELECT COALESCE(SUM(listenDurationMs), 0) FROM playback_analytics")
    long getTotalListeningMs();

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM playback_analytics")
    int getTotalPlays();

    @Query("DELETE FROM playback_analytics")
    void clearAll();

    @Query("DELETE FROM playback_analytics WHERE trackId LIKE 'local_demo_%' OR title LIKE '%Claro de Luna%' OR artist LIKE '%Beethoven%' OR artist LIKE '%Satie%' OR artist LIKE '%Chopin%'")
    void deleteDemoAnalytics();

    class ArtistStatTuple {
        public String artist;
        public long totalTimeMs;
        public int playCount;
    }

    class TrackStatTuple {
        public String trackId;
        public String title;
        public String artist;
        public String artworkUrl;
        public long totalTimeMs;
        public int playCount;
    }
}
