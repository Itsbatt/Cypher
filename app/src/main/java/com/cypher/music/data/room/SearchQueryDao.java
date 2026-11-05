package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SearchQueryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertQuery(SearchQueryEntity entity);

    @Query("SELECT queryText FROM search_queries ORDER BY timestamp DESC")
    List<String> getRecentQueries();

    @Query("DELETE FROM search_queries WHERE queryText = :queryText")
    void deleteQuery(String queryText);

    @Query("DELETE FROM search_queries")
    void clearAll();
}
