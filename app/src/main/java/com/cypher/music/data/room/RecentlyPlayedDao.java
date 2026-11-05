package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecentlyPlayedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecentlyPlayed(RecentlyPlayedEntity item);

    @Query("SELECT * FROM recently_played ORDER BY timestamp DESC LIMIT :limit")
    List<RecentlyPlayedEntity> getRecentlyPlayed(int limit);

    @Query("DELETE FROM recently_played WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM recently_played WHERE id LIKE 'local_demo_%' OR extraId LIKE 'local_demo_%' OR title LIKE '%Claro de Luna%' OR title LIKE '%Gymnopédie%' OR title LIKE '%Nocturne Op. 9%'")
    void deleteDemoRecentlyPlayed();

    @Query("DELETE FROM recently_played")
    void clearAll();
}
