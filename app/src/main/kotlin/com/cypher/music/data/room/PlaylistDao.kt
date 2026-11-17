package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM playlists WHERE sourceId = :sourceId LIMIT 1")
    fun getPlaylistBySourceId(sourceId: String?): PlaylistEntity?

    @Query("UPDATE playlists SET syncState = :state, lastSyncedAt = :syncedAt WHERE id = :playlistId")
    fun setSyncState(playlistId: String, state: String?, syncedAt: Long)

    @Query(
        "SELECT t.* FROM tracks t " +
            "INNER JOIN playlist_tracks_crossref c ON t.id = c.trackId " +
            "WHERE c.playlistId = :playlistId ORDER BY c.position ASC"
    )
    fun getOrderedTracks(playlistId: String): List<TrackEntity>

    /**
     * The ids actually stored for a playlist, even when the track row is missing.
     * [getOrderedTracks] drops those because it joins against `tracks`.
     */
    @Query("SELECT trackId FROM playlist_tracks_crossref WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getOrderedTrackIds(playlistId: String): List<String>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistWithTracks(playlistId: String): PlaylistWithTracks?

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylistsWithTracks(): List<PlaylistWithTracks>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCrossRef(ref: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks_crossref WHERE playlistId = :playlistId")
    fun clearPlaylistTracks(playlistId: String)

    @Query("DELETE FROM playlist_tracks_crossref WHERE playlistId = :playlistId AND trackId = :trackId")
    fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks_crossref WHERE trackId LIKE 'local_demo_%'")
    fun deleteDemoPlaylistCrossRefs()

    @Query(
        "SELECT t.* FROM tracks t " +
            "INNER JOIN playlist_tracks_crossref c ON t.id = c.trackId " +
            "WHERE c.playlistId = :playlistId " +
            "ORDER BY c.position ASC"
    )
    fun getTracksForPlaylist(playlistId: String): List<TrackEntity>

    @Query("SELECT * FROM playlists WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchPlaylists(query: String): List<PlaylistEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks_crossref WHERE playlistId = :playlistId")
    fun getTrackCount(playlistId: String): Int
}
