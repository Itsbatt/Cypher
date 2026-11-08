package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlaybackDailyDao {

    @Query("INSERT INTO playback_daily (day, trackId, title, artist, artworkUrl, album, genre, listenDurationMs, playCount, lastListenedAt) " +
           "VALUES (:day, :trackId, :title, :artist, :artworkUrl, :album, :genre, :durationMs, CASE WHEN :countPlay THEN 1 ELSE 0 END, :timestamp) " +
           "ON CONFLICT(day, trackId) DO UPDATE SET " +
           "listenDurationMs = listenDurationMs + :durationMs, " +
           "playCount = playCount + CASE WHEN :countPlay THEN 1 ELSE 0 END, " +
           "lastListenedAt = :timestamp, " +
           "title = :title, " +
           "artist = :artist, " +
           "artworkUrl = COALESCE(:artworkUrl, artworkUrl), " +
           "album = COALESCE(:album, album), " +
           "genre = COALESCE(:genre, genre)")
    void recordDailyPlayback(String day, String trackId, String title, String artist,
                             String artworkUrl, String album, String genre, long durationMs,
                             long timestamp, boolean countPlay);

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

    @Query("SELECT trackId, title, artist, artworkUrl, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
           "GROUP BY trackId ORDER BY SUM(playCount) DESC, SUM(listenDurationMs) DESC LIMIT 1")
    TrackTuple getMostRepeatedTrack(String startDay, String endDay);

    @Query("SELECT artist, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
           "GROUP BY artist ORDER BY totalTimeMs DESC LIMIT :limit")
    List<ArtistTuple> getTopArtists(String startDay, String endDay, int limit);

    @Query("SELECT album, MAX(artworkUrl) AS artworkUrl, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
           "AND album IS NOT NULL AND TRIM(album) <> '' " +
           "GROUP BY album ORDER BY totalTimeMs DESC LIMIT :limit")
    List<AlbumTuple> getTopAlbums(String startDay, String endDay, int limit);

    @Query("SELECT genre, SUM(listenDurationMs) AS totalTimeMs, SUM(playCount) AS playCount " +
           "FROM playback_daily WHERE day >= :startDay AND day <= :endDay " +
           "AND genre IS NOT NULL AND TRIM(genre) <> '' " +
           "GROUP BY genre ORDER BY totalTimeMs DESC LIMIT :limit")
    List<GenreTuple> getTopGenres(String startDay, String endDay, int limit);

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

    class AlbumTuple {
        public String album;
        public String artworkUrl;
        public long totalTimeMs;
        public int playCount;
    }

    class GenreTuple {
        public String genre;
        public long totalTimeMs;
        public int playCount;
    }

    class DayTuple {
        public String day;
        public long totalTimeMs;
        public int playCount;
    }
}
