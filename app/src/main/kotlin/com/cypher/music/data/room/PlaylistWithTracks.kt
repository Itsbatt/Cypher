package com.cypher.music.data.room

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Relational model joining playlists and tracks through [PlaylistTrackCrossRef]
 * with @Relation and @Junction.
 */
class PlaylistWithTracks {
    @Embedded
    var playlist: PlaylistEntity = PlaylistEntity()

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    var tracks: List<TrackEntity> = emptyList()
}
