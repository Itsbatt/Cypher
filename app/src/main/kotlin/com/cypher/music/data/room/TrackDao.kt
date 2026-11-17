package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTracks(tracks: List<TrackEntity>)

    @Update
    fun updateTrack(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY updatedAt DESC")
    fun getFavoriteTracks(): List<TrackEntity>

    @Query("SELECT id FROM tracks WHERE isLiked = 1")
    fun getFavoriteTrackIds(): List<String>

    @Query("UPDATE tracks SET isLiked = :isLiked, updatedAt = :updatedAt WHERE id = :id")
    fun setFavorite(id: String, isLiked: Boolean, updatedAt: Long)

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY updatedAt DESC")
    fun getDownloadedTracks(): List<TrackEntity>

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, downloadedUri = :downloadedUri, updatedAt = :updatedAt WHERE id = :id")
    fun setDownloaded(id: String, isDownloaded: Boolean, downloadedUri: String?, updatedAt: Long)

    @Query("DELETE FROM tracks WHERE id = :id")
    fun deleteTrack(id: String)

    @Query("DELETE FROM tracks WHERE id LIKE 'local_demo_%' OR title LIKE '%Claro de Luna%' OR artist LIKE '%Beethoven%' OR artist LIKE '%Satie%' OR artist LIKE '%Chopin%'")
    fun deleteDemoTracks()

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchTracks(query: String): List<TrackEntity>
}
