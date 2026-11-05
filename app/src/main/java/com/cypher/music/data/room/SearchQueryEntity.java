package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_queries")
public class SearchQueryEntity {
    @PrimaryKey
    @NonNull
    public String queryText;

    public long timestamp;

    public SearchQueryEntity(@NonNull String queryText, long timestamp) {
        this.queryText = queryText;
        this.timestamp = timestamp;
    }
}
