package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tracks")
public class TrackEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String title;
    public String artist;
    public String album;
    public long durationMs;
    public String artworkUrl;
    public String streamUrl;
    public String sourceType; // "LOCAL" or "YOUTUBE"
    public String path;
    public boolean isLiked;
    public boolean isDownloaded;
    public String downloadedUri;
    public long updatedAt;

    public TrackEntity() {
        this.id = "";
    }

    @androidx.room.Ignore
    public TrackEntity(@NonNull String id, String title, String artist, String album, long durationMs,
                       String artworkUrl, String streamUrl, String sourceType, String path,
                       boolean isLiked, boolean isDownloaded, String downloadedUri, long updatedAt) {
        this.id = id;
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.album = album != null ? album : "Sencillo";
        this.durationMs = durationMs;
        this.artworkUrl = artworkUrl;
        this.streamUrl = streamUrl != null ? streamUrl : "";
        this.sourceType = sourceType != null ? sourceType : "LOCAL";
        this.path = path;
        this.isLiked = isLiked;
        this.isDownloaded = isDownloaded;
        this.downloadedUri = downloadedUri;
        this.updatedAt = updatedAt;
    }
}
