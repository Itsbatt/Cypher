package com.cypher.music.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recently_played",
    indices = [Index(value = ["timestamp"], orders = [Index.Order.DESC])]
)
class RecentlyPlayedEntity(
    @PrimaryKey var id: String = "",
    /** TRACK, ALBUM, PLAYLIST. */
    var itemType: String? = "TRACK",
    var title: String? = null,
    var subtitle: String? = null,
    var artworkUrl: String? = null,
    var streamUrl: String? = null,
    var sourceType: String? = "LOCAL",
    var extraId: String? = null,
    var timestamp: Long = 0L
)
