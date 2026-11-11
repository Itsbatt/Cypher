package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE accountKey = :accountKey LIMIT 1")
    SyncStateEntity get(String accountKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncStateEntity state);

    @Query("DELETE FROM sync_state WHERE accountKey = :accountKey")
    void delete(String accountKey);
}
