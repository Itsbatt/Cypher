package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTrack(TrackEntity track);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTracks(List<TrackEntity> tracks);

    @Update
    void updateTrack(TrackEntity track);

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    TrackEntity getTrackById(String id);

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY updatedAt DESC")
    List<TrackEntity> getFavoriteTracks();

    @Query("SELECT id FROM tracks WHERE isLiked = 1")
    List<String> getFavoriteTrackIds();

    @Query("UPDATE tracks SET isLiked = :isLiked, updatedAt = :updatedAt WHERE id = :id")
    void setFavorite(String id, boolean isLiked, long updatedAt);

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY updatedAt DESC")
    List<TrackEntity> getDownloadedTracks();

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, downloadedUri = :downloadedUri, updatedAt = :updatedAt WHERE id = :id")
    void setDownloaded(String id, boolean isDownloaded, String downloadedUri, long updatedAt);

    @Query("DELETE FROM tracks WHERE id = :id")
    void deleteTrack(String id);

    @Query("DELETE FROM tracks WHERE id LIKE 'local_demo_%' OR title LIKE '%Claro de Luna%' OR artist LIKE '%Beethoven%' OR artist LIKE '%Satie%' OR artist LIKE '%Chopin%'")
    void deleteDemoTracks();

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    List<TrackEntity> searchTracks(String query);
}
