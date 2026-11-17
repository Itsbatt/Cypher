package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentlyPlayedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecentlyPlayed(item: RecentlyPlayedEntity)

    @Query("SELECT * FROM recently_played ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): List<RecentlyPlayedEntity>

    @Query("DELETE FROM recently_played WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM recently_played WHERE id LIKE 'local_demo_%' OR extraId LIKE 'local_demo_%' OR title LIKE '%Claro de Luna%' OR title LIKE '%Gymnopédie%' OR title LIKE '%Nocturne Op. 9%'")
    fun deleteDemoRecentlyPlayed()

    @Query("DELETE FROM recently_played")
    fun clearAll()
}
