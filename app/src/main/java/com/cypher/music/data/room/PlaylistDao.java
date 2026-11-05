package com.cypher.music.data.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlaylist(PlaylistEntity playlist);

    @Update
    void updatePlaylist(PlaylistEntity playlist);

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    void deletePlaylist(String playlistId);

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    List<PlaylistEntity> getAllPlaylists();

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    PlaylistEntity getPlaylistById(String playlistId);

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    PlaylistWithTracks getPlaylistWithTracks(String playlistId);

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    List<PlaylistWithTracks> getAllPlaylistsWithTracks();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrossRef(PlaylistTrackCrossRef ref);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrossRefs(List<PlaylistTrackCrossRef> refs);

    @Query("DELETE FROM playlist_tracks_crossref WHERE playlistId = :playlistId")
    void clearPlaylistTracks(String playlistId);

    @Query("DELETE FROM playlist_tracks_crossref WHERE playlistId = :playlistId AND trackId = :trackId")
    void removeTrackFromPlaylist(String playlistId, String trackId);

    @Query("DELETE FROM playlist_tracks_crossref WHERE trackId LIKE 'local_demo_%'")
    void deleteDemoPlaylistCrossRefs();

    @Query("SELECT t.* FROM tracks t " +
           "INNER JOIN playlist_tracks_crossref c ON t.id = c.trackId " +
           "WHERE c.playlistId = :playlistId " +
           "ORDER BY c.position ASC")
    List<TrackEntity> getTracksForPlaylist(String playlistId);

    @Query("SELECT * FROM playlists WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    List<PlaylistEntity> searchPlaylists(String query);

    @Query("SELECT COUNT(*) FROM playlist_tracks_crossref WHERE playlistId = :playlistId")
    int getTrackCount(String playlistId);
}
