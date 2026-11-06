package com.cypher.music.data

import android.content.Context
import androidx.room.withTransaction
import android.util.Log
import com.cypher.music.data.room.CypherDatabase
import com.cypher.music.data.room.PlaylistTrackCrossRef
import com.cypher.music.data.room.toDomain
import com.cypher.music.data.room.toEntity
import com.cypher.music.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PlaylistRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PlaylistRepository"
        const val YOUTUBE_SYNC_DESCRIPTION = "Sincronizado desde YouTube Music"
        private const val SYNC_DESCRIPTION_PREFIX = "Sincronizad"
    }

    val roomDb: CypherDatabase = CypherDatabase.getInstance(context)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<RecentlyPlayedItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<RecentlyPlayedItem>> = _recentlyPlayed.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Query Room using @Relation (PlaylistWithTracks)
                val playlistsWithTracks = roomDb.playlistDao().getAllPlaylistsWithTracks()
                val domainPlaylists = playlistsWithTracks.map { it.toDomain() }

                // 2. Query Room for recently played items
                val recents = roomDb.recentlyPlayedDao().getRecentlyPlayed(24).map { it.toDomain() }

                _playlists.value = domainPlaylists
                _recentlyPlayed.value = recents
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlists from Room: ${e.message}", e)
            }
        }
    }

    suspend fun createPlaylist(
        name: String,
        description: String = "",
        coverUri: String? = null,
        initialTracks: List<Track> = emptyList()
    ): Playlist = withContext(Dispatchers.IO) {
        val newId = "playlist_${UUID.randomUUID()}"
        val playlist = Playlist(
            id = newId,
            name = name.trim().ifEmpty { "Nueva playlist" },
            description = description.trim(),
            coverUri = coverUri,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPrivate = true,
            tracks = initialTracks
        )

        try {
            roomDb.playlistDao().insertPlaylist(playlist.toEntity())
            if (initialTracks.isNotEmpty()) {
                roomDb.trackDao().insertTracks(initialTracks.map { it.toEntity() })
                val refs = initialTracks.mapIndexed { idx, track ->
                    PlaylistTrackCrossRef(playlist.id, track.id, idx, System.currentTimeMillis())
                }
                roomDb.playlistDao().insertCrossRefs(refs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Room insertPlaylist error: ${e.message}", e)
        }

        loadData()
        playlist
    }

    /**
     * Creates or updates the playlist produced by the YouTube Music sync.
     *
     * Matching on name + sync description keeps the existing playlist id across re-syncs,
     * so syncing twice never duplicates a playlist. Tracks are de-duplicated by id,
     * written in a single transaction and keep the remote order.
     */
    suspend fun syncPlaylist(
        name: String,
        description: String = YOUTUBE_SYNC_DESCRIPTION,
        coverUri: String? = null,
        tracks: List<Track>,
        sourceId: String? = null
    ): Playlist = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty { "Nueva playlist" }
        val cleanDescription = description.trim().ifEmpty { YOUTUBE_SYNC_DESCRIPTION }
        val uniqueTracks = tracks.distinctBy { it.id }
        val now = System.currentTimeMillis()

        val existing = sourceId?.let { roomDb.playlistDao().getPlaylistBySourceId(it) }
            ?: roomDb.playlistDao().getAllPlaylists().firstOrNull {
                it.name == cleanName && isSameSyncOrigin(it.description, cleanDescription)
            }

        val playlist = Playlist(
            id = existing?.id ?: "playlist_${UUID.randomUUID()}",
            name = cleanName,
            description = cleanDescription,
            coverUri = coverUri ?: existing?.coverUri,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isPrivate = existing?.isPrivate ?: true,
            tracks = uniqueTracks
        )

        // Throws on failure so the sync can report the playlist as incomplete instead of
        // marking a half-written import as successful.
        roomDb.withTransaction {
            roomDb.playlistDao().insertPlaylist(playlist.toEntity().apply {
                this.sourceId = sourceId ?: existing?.sourceId
            })
            roomDb.playlistDao().clearPlaylistTracks(playlist.id)
            if (uniqueTracks.isNotEmpty()) {
                roomDb.trackDao().insertTracks(uniqueTracks.map { it.toEntity() })
                val refs = uniqueTracks.mapIndexed { index, track ->
                    PlaylistTrackCrossRef(playlist.id, track.id, index, now)
                }
                roomDb.playlistDao().insertCrossRefs(refs)
            }
        }

        loadData()
        playlist
    }

    private fun isSameSyncOrigin(existingDescription: String?, incoming: String): Boolean {
        if (existingDescription == incoming) return true
        return existingDescription != null &&
            existingDescription.startsWith(SYNC_DESCRIPTION_PREFIX) &&
            incoming.startsWith(SYNC_DESCRIPTION_PREFIX)
    }

    suspend fun updatePlaylistDetails(
        playlistId: String,
        name: String,
        description: String,
        coverUri: String?
    ) = withContext(Dispatchers.IO) {
        try {
            val existing = roomDb.playlistDao().getPlaylistById(playlistId)
            if (existing != null) {
                existing.name = name.trim()
                existing.description = description.trim()
                existing.coverUri = coverUri
                existing.updatedAt = System.currentTimeMillis()
                roomDb.playlistDao().updatePlaylist(existing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Room updatePlaylist error: ${e.message}", e)
        }
        loadData()
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        try {
            roomDb.playlistDao().deletePlaylist(playlistId)
            roomDb.playlistDao().clearPlaylistTracks(playlistId)
        } catch (e: Exception) {
            Log.e(TAG, "Room deletePlaylist error: ${e.message}", e)
        }
        loadData()
    }

    suspend fun addTracksToPlaylist(playlistId: String, tracks: List<Track>) = withContext(Dispatchers.IO) {
        try {
            roomDb.trackDao().insertTracks(tracks.map { it.toEntity() })
            val existingCount = roomDb.playlistDao().getTrackCount(playlistId)
            val refs = tracks.mapIndexed { idx, track ->
                PlaylistTrackCrossRef(playlistId, track.id, existingCount + idx, System.currentTimeMillis())
            }
            roomDb.playlistDao().insertCrossRefs(refs)
        } catch (e: Exception) {
            Log.e(TAG, "Room addTracksToPlaylist error: ${e.message}", e)
        }
        loadData()
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        try {
            roomDb.playlistDao().removeTrackFromPlaylist(playlistId, trackId)
        } catch (e: Exception) {
            Log.e(TAG, "Room removeTrackFromPlaylist error: ${e.message}", e)
        }
        loadData()
    }

    suspend fun reorderTracks(playlistId: String, reorderedTracks: List<Track>) = withContext(Dispatchers.IO) {
        try {
            roomDb.playlistDao().clearPlaylistTracks(playlistId)
            val refs = reorderedTracks.mapIndexed { idx, track ->
                PlaylistTrackCrossRef(playlistId, track.id, idx, System.currentTimeMillis())
            }
            roomDb.playlistDao().insertCrossRefs(refs)
        } catch (e: Exception) {
            Log.e(TAG, "Room reorderTracks error: ${e.message}", e)
        }
        loadData()
    }

    suspend fun getPlaylistById(playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        try {
            val relation = roomDb.playlistDao().getPlaylistWithTracks(playlistId)
            if (relation != null) {
                return@withContext relation.toDomain()
            }
            val entity = roomDb.playlistDao().getPlaylistById(playlistId)
            if (entity != null) {
                val tracks = roomDb.playlistDao().getTracksForPlaylist(playlistId).map { it.toDomain() }
                return@withContext entity.toDomain(tracks)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Room getPlaylistById error: ${e.message}", e)
        }
        null
    }

    suspend fun searchLocal(query: String): Pair<List<Playlist>, List<Track>> = withContext(Dispatchers.IO) {
        try {
            val playlists = roomDb.playlistDao().searchPlaylists(query).map { entity ->
                val tracks = roomDb.playlistDao().getTracksForPlaylist(entity.id).map { it.toDomain() }
                entity.toDomain(tracks)
            }
            val tracks = roomDb.trackDao().searchTracks(query).map { it.toDomain() }
            Pair(playlists, tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Room searchLocal error: ${e.message}", e)
            Pair(emptyList(), emptyList())
        }
    }

    fun recordRecentlyPlayed(item: RecentlyPlayedItem) {
        scope.launch(Dispatchers.IO) {
            try {
                roomDb.recentlyPlayedDao().insertRecentlyPlayed(item.toEntity())
                val recents = roomDb.recentlyPlayedDao().getRecentlyPlayed(24).map { it.toDomain() }
                _recentlyPlayed.value = recents
            } catch (e: Exception) {
                Log.e(TAG, "Error recording recently played in Room: ${e.message}", e)
            }
        }
    }
}
