package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SyncOutboxDao {

    @Query("SELECT * FROM sync_outbox ORDER BY queuedAt ASC LIMIT :limit")
    List<SyncOutboxEntity> pending(int limit);

    @Query("SELECT COUNT(*) FROM sync_outbox")
    int pendingCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncOutboxEntity row);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<SyncOutboxEntity> rows);

    @Query("DELETE FROM sync_outbox WHERE entityKey = :entityKey")
    void delete(String entityKey);

    @Query("DELETE FROM sync_outbox WHERE entityKey IN (:entityKeys)")
    void deleteAll(List<String> entityKeys);

    @Query("UPDATE sync_outbox SET attempts = attempts + 1, lastAttemptAt = :at, lastError = :error WHERE entityKey = :entityKey")
    void markFailed(String entityKey, long at, String error);

    @Query("DELETE FROM sync_outbox")
    void clear();
}
