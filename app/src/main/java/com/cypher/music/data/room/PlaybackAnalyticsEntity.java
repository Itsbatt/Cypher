package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "playback_analytics",
    indices = {
        @Index(value = {"listenDurationMs"}, orders = {Index.Order.DESC}),
        @Index(value = {"artist"}),
        @Index(value = {"lastListenedAt"}, orders = {Index.Order.DESC})
    }
)
public class PlaybackAnalyticsEntity {
    @PrimaryKey
    @NonNull
    public String trackId;
    public String title;
    public String artist;
    public String artworkUrl;
    public long listenDurationMs;
    public int playCount;
    public long lastListenedAt;

    public PlaybackAnalyticsEntity() {
        this.trackId = "";
        this.title = "";
        this.artist = "";
    }

    @Ignore
    public PlaybackAnalyticsEntity(@NonNull String trackId, String title, String artist,
                                  String artworkUrl, long listenDurationMs, int playCount,
                                  long lastListenedAt) {
        this.trackId = trackId;
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.artworkUrl = artworkUrl;
        this.listenDurationMs = listenDurationMs;
        this.playCount = playCount;
        this.lastListenedAt = lastListenedAt;
    }
}
