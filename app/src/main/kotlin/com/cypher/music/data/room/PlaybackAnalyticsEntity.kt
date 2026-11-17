package com.cypher.music.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_analytics",
    indices = [
        Index(value = ["listenDurationMs"], orders = [Index.Order.DESC]),
        Index(value = ["artist"]),
        Index(value = ["lastListenedAt"], orders = [Index.Order.DESC])
    ]
)
class PlaybackAnalyticsEntity(
    @PrimaryKey var trackId: String = "",
    var title: String? = null,
    var artist: String? = null,
    var artworkUrl: String? = null,
    var listenDurationMs: Long = 0L,
    var playCount: Int = 0,
    var lastListenedAt: Long = 0L
)
