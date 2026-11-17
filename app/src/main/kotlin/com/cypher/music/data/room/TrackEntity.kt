package com.cypher.music.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A song known to the library. Nullability mirrors the exported schema v9 on purpose:
 * [title], [artist], [album], [streamUrl] and [sourceType] are nullable columns, so the
 * Kotlin type stays `String?` and the mappers add the display defaults.
 */
@Entity(tableName = "tracks")
class TrackEntity(
    @PrimaryKey var id: String = "",
    var title: String? = null,
    var artist: String? = null,
    var album: String? = null,
    var durationMs: Long = 0L,
    var artworkUrl: String? = null,
    var streamUrl: String? = null,
    /** "LOCAL" or "YOUTUBE". */
    var sourceType: String? = null,
    var path: String? = null,
    var isLiked: Boolean = false,
    var isDownloaded: Boolean = false,
    var downloadedUri: String? = null,
    var updatedAt: Long = 0L
)
