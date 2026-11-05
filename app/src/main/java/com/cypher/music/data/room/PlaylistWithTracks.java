package com.cypher.music.data.room;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.Collections;
import java.util.List;

/**
 * Modern Room Relational Model connecting Playlists and Tracks
 * via PlaylistTrackCrossRef using @Relation and @Junction.
 */
public class PlaylistWithTracks {
    @Embedded
    public PlaylistEntity playlist;

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = @Junction(
            value = PlaylistTrackCrossRef.class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    public List<TrackEntity> tracks;

    public PlaylistWithTracks() {
        this.tracks = Collections.emptyList();
    }
}
