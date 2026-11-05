package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "playlist_tracks_crossref",
    primaryKeys = {"playlistId", "trackId", "position"},
    indices = {@Index("playlistId"), @Index("trackId")}
)
public class PlaylistTrackCrossRef {
    @NonNull
    public String playlistId;
    @NonNull
    public String trackId;
    public int position;
    public long addedAt;

    public PlaylistTrackCrossRef() {
        this.playlistId = "";
        this.trackId = "";
    }

    @androidx.room.Ignore
    public PlaylistTrackCrossRef(@NonNull String playlistId, @NonNull String trackId, int position, long addedAt) {
        this.playlistId = playlistId;
        this.trackId = trackId;
        this.position = position;
        this.addedAt = addedAt;
    }
}
