package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * Aggregated listening time per calendar day and track.
 * The all-time table cannot answer "what happened in 2026", so every tick is also
 * folded into this daily rollup. Day is an ISO-8601 local date (yyyy-MM-dd).
 */
@Entity(
    tableName = "playback_daily",
    primaryKeys = {"day", "trackId"},
    indices = {
        @Index(value = {"day"}),
        @Index(value = {"trackId"})
    }
)
public class PlaybackDailyEntity {
    @NonNull
    public String day;
    @NonNull
    public String trackId;
    @NonNull
    public String title;
    @NonNull
    public String artist;
    public String artworkUrl;
    public long listenDurationMs;
    public int playCount;
    public long lastListenedAt;

    public PlaybackDailyEntity() {
        this.day = "";
        this.trackId = "";
        this.title = "";
        this.artist = "";
    }

    @androidx.room.Ignore
    public PlaybackDailyEntity(@NonNull String day, @NonNull String trackId, String title, String artist,
                               String artworkUrl, long listenDurationMs, int playCount,
                               long lastListenedAt) {
        this.day = day;
        this.trackId = trackId;
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.artworkUrl = artworkUrl;
        this.listenDurationMs = listenDurationMs;
        this.playCount = playCount;
        this.lastListenedAt = lastListenedAt;
    }
}
