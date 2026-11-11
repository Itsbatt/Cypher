package com.cypher.music.data.room;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Per-account sync bookkeeping: the incremental pull cursor and the timestamps the
 * account section shows. Keyed by the Google account id so switching accounts on
 * the same device never reuses another account's cursor.
 */
@Entity(tableName = "sync_state")
public class SyncStateEntity {
    @PrimaryKey
    @NonNull
    public String accountKey;

    /** Opaque cursor returned by the backend; null means "from the beginning". */
    public String cursor;

    public long lastSyncedAt;
    public long lastAttemptAt;

    public SyncStateEntity() {
        this.accountKey = "";
    }

    @androidx.room.Ignore
    public SyncStateEntity(@NonNull String accountKey, String cursor, long lastSyncedAt, long lastAttemptAt) {
        this.accountKey = accountKey;
        this.cursor = cursor;
        this.lastSyncedAt = lastSyncedAt;
        this.lastAttemptAt = lastAttemptAt;
    }
}
