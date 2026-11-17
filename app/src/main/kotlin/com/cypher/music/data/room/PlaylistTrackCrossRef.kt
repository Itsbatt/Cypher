package com.cypher.music.data.room

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_tracks_crossref",
    primaryKeys = ["playlistId", "trackId", "position"],
    indices = [Index("playlistId"), Index("trackId")]
)
class PlaylistTrackCrossRef(
    var playlistId: String = "",
    var trackId: String = "",
    var position: Int = 0,
    var addedAt: Long = 0L
)
