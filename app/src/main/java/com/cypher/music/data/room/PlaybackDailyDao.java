package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlaybackDailyDao {

    @Query("INSERT INTO playback_daily (day, trackId, title, artist, artworkUrl, listenDurationMs, playCount, lastListenedAt) " +
           "VALUES (:day, :trackId, :title, :artist, :artworkUrl, :durationMs, CASE WHEN :countPlay THEN 1 ELSE 0 END, :timestamp) " +
           "ON CONFLICT(day, trackId) DO UPDATE SET " +
           "listenDurationMs = listenDurationMs + :durationMs, " +
           "playCount = playCount + CASE WHEN :countPlay THEN 1 ELSE 0 END, " +
           "lastListenedAt = :timestamp, " +
           "title = :title, " +
           "artist = :artist, " +
           "artworkUrl = COALESCE(:artworkUrl, artworkUrl)")
    void recordDailyPlayback(String day, String trackId, String title, String artist,
                             String artworkUrl, long durationMs, long timestamp, boolean countPlay);

    @Query("SELECT COALESCE(SUM(listenDurationMs), 0) FROM playback_daily " +
           "WHERE day >= :startDay AND day <= :endDay")
    long getTotalListeningMs(String startDay, String endDay);

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM playback_daily " +
           "WHERE day >= :startDay AND day <= :endDay")
    int getTotalPlays(String startDay, String endDay);

    @Query("SELECT trackId, title, artist, artworkUrl, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
           "GROUP BY trackId ORDER BY totalTimeMs DESC LIMIT :limit")
    List<TrackTuple> getTopTracks(String startDay, String endDay, int limit);

    @Query("SELECT artist, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
           "GROUP BY artist ORDER BY totalTimeMs DESC LIMIT :limit")
    List<ArtistTuple> getTopArtists(String startDay, String endDay, int limit);

    @Query("SELECT day, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
           "GROUP BY day ORDER BY totalTimeMs DESC LIMIT 1")
    DayTuple getPeakDay(String startDay, String endDay);

    @Query("DELETE FROM playback_daily")
    void clearAll();

    class TrackTuple {
        public String trackId;
        public String title;
        public String artist;
        public String artworkUrl;
        public long totalTimeMs;
        public int playCount;
    }

    class ArtistTuple {
        public String artist;
        public long totalTimeMs;
        public int playCount;
    }

    class DayTuple {
        public String day;
        public long totalTimeMs;
        public int playCount;
    }
}
