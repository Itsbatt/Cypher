package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class PlaylistEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String name;
    public String description;
    public String coverUri;
    public long createdAt;
    public long updatedAt;
    public boolean isPrivate;

    public PlaylistEntity() {
        this.id = "";
        this.name = "Nueva Playlist";
        this.description = "";
    }

    @Ignore
    public PlaylistEntity(@NonNull String id, String name, String description,
                          String coverUri, long createdAt, long updatedAt, boolean isPrivate) {
        this.id = id;
        this.name = name != null ? name : "Nueva Playlist";
        this.description = description != null ? description : "";
        this.coverUri = coverUri;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isPrivate = isPrivate;
    }
}
