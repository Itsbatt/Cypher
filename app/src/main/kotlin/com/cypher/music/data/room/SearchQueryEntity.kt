package com.cypher.music.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_queries")
class SearchQueryEntity(
    @PrimaryKey var queryText: String,
    var timestamp: Long
)
