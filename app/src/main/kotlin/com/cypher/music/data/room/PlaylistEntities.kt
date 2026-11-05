package com.cypher.music.data.room

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    var id: String = "",
    var name: String = "Nueva Playlist",
    var description: String = "",
    var coverUri: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isPrivate: Boolean = false
)

@Entity(
    tableName = "playlist_tracks_crossref",
    primaryKeys = ["playlistId", "trackId", "position"],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackCrossRef(
    var playlistId: String = "",
    var trackId: String = "",
    var position: Int = 0,
    var addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithTracks(
    @Embedded
    var playlist: PlaylistEntity = PlaylistEntity(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    var tracks: List<TrackEntity> = emptyList()
)

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    fun deletePlaylist(playlistId: String)

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

    @Query("SELECT t.* FROM tracks t INNER JOIN playlist_tracks_crossref c ON t.id = c.trackId WHERE c.playlistId = :playlistId ORDER BY c.position ASC")
    fun getTracksForPlaylist(playlistId: String): List<TrackEntity>

    @Query("SELECT * FROM playlists WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchPlaylists(query: String): List<PlaylistEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks_crossref WHERE playlistId = :playlistId")
    fun getTrackCount(playlistId: String): Int
}
