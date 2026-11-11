package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Durable queue of local changes that still have to reach the cloud.
 *
 * Only identity and timing live here: the payload is rebuilt from Room when the
 * batch is pushed, so a track edited twice while offline is uploaded once and in
 * its final state. [deleted] keeps tombstones so a delete travels as well.
 */
@Entity(
    tableName = "sync_outbox",
    indices = {@Index("entityKey"), @Index("queuedAt")}
)
public class SyncOutboxEntity {
    /** "KIND:entityId", stable across devices. */
    @PrimaryKey
    @NonNull
    public String entityKey;

    @NonNull
    public String kind;

    @NonNull
    public String entityId;
    public long updatedAt;
    public boolean deleted;
    public int attempts;
    public long queuedAt;
    public long lastAttemptAt;
    public String lastError;

    public SyncOutboxEntity() {
        this.entityKey = "";
        this.kind = "";
        this.entityId = "";
    }

    @androidx.room.Ignore
    public SyncOutboxEntity(@NonNull String entityKey, String kind, String entityId,
                            long updatedAt, boolean deleted, long queuedAt) {
        this.entityKey = entityKey;
        this.kind = kind != null ? kind : "";
        this.entityId = entityId != null ? entityId : "";
        this.updatedAt = updatedAt;
        this.deleted = deleted;
        this.queuedAt = queuedAt;
    }
}
