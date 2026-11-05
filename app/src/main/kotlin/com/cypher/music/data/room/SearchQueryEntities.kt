package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "search_queries")
data class SearchQueryEntity(
    @PrimaryKey
    var queryText: String = "",
    var timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SearchQueryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuery(entity: SearchQueryEntity)

    @Query("SELECT queryText FROM search_queries ORDER BY timestamp DESC")
    fun getRecentQueries(): List<String>

    @Query("DELETE FROM search_queries WHERE queryText = :queryText")
    fun deleteQuery(queryText: String)

    @Query("DELETE FROM search_queries")
    fun clearAll()
}
