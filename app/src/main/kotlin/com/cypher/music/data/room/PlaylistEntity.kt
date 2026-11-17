package com.cypher.music.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists", indices = [Index("sourceId")])
class PlaylistEntity(
    @PrimaryKey var id: String = "",
    var name: String? = "Nueva Playlist",
    var description: String? = "",
    var coverUri: String? = null,
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var isPrivate: Boolean = false,
    /** YouTube Music playlist id (VLPL...) for synced playlists; null for local ones. */
    var sourceId: String? = null,
    /** Epoch millis of the last sync attempt; 0 for never-synced local playlists. */
    var lastSyncedAt: Long = 0L,
    /** "complete" or "incomplete" for mirrored playlists; null for local ones. */
    var syncState: String? = null
)
