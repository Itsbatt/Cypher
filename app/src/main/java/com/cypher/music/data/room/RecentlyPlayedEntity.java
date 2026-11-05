package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "recently_played",
    indices = {@Index(value = {"timestamp"}, orders = {Index.Order.DESC})}
)
public class RecentlyPlayedEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String itemType; // TRACK, ALBUM, PLAYLIST
    public String title;
    public String subtitle;
    public String artworkUrl;
    public String streamUrl;
    public String sourceType;
    public String extraId;
    public long timestamp;

    public RecentlyPlayedEntity() {
        this.id = "";
        this.itemType = "TRACK";
        this.title = "";
        this.subtitle = "";
        this.sourceType = "LOCAL";
    }

    @Ignore
    public RecentlyPlayedEntity(@NonNull String id, String itemType, String title, String subtitle,
                                String artworkUrl, String streamUrl, String sourceType,
                                String extraId, long timestamp) {
        this.id = id;
        this.itemType = itemType != null ? itemType : "TRACK";
        this.title = title != null ? title : "";
        this.subtitle = subtitle != null ? subtitle : "";
        this.artworkUrl = artworkUrl;
        this.streamUrl = streamUrl != null ? streamUrl : "";
        this.sourceType = sourceType != null ? sourceType : "LOCAL";
        this.extraId = extraId;
        this.timestamp = timestamp;
    }
}
