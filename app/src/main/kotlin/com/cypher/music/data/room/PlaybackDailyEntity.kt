package com.cypher.music.data.room

import androidx.room.Entity
import androidx.room.Index

/**
 * Aggregated listening time per calendar day and track.
 * The all-time table cannot answer "what happened in 2026", so every tick is also
 * folded into this daily rollup. Day is an ISO-8601 local date (yyyy-MM-dd).
 *
 * [album] and [genre] are nullable on purpose: YouTube streams and untagged files have
 * no album/genre, and Wrapped must be able to say "no data" instead of inventing one.
 */
@Entity(
    tableName = "playback_daily",
    primaryKeys = ["day", "trackId"],
    indices = [Index("day"), Index("trackId")]
)
class PlaybackDailyEntity(
    var day: String = "",
    var trackId: String = "",
    var title: String = "",
    var artist: String = "",
    var artworkUrl: String? = null,
    var album: String? = null,
    var genre: String? = null,
    var listenDurationMs: Long = 0L,
    var playCount: Int = 0,
    var lastListenedAt: Long = 0L
)
