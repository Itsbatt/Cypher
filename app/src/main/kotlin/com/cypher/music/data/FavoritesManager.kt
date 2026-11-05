package com.cypher.music.data

import android.content.Context
import com.cypher.music.data.room.CypherDatabase
import com.cypher.music.data.room.toDomain
import com.cypher.music.data.room.toEntity
import com.cypher.music.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoritesManager(
    context: Context,
    private val scope: CoroutineScope
) {
    private val roomDb = CypherDatabase.getInstance(context)

    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    private val _favoriteTracks = MutableStateFlow<List<Track>>(emptyList())
    val favoriteTracks: StateFlow<List<Track>> = _favoriteTracks.asStateFlow()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        scope.launch(Dispatchers.IO) {
            try {
                val list = roomDb.trackDao().getFavoriteTracks().map { it.toDomain() }
                val idSet = roomDb.trackDao().getFavoriteTrackIds().toSet()
                _favoriteTracks.value = list
                _favoriteTrackIds.value = idSet
            } catch (e: Exception) {
                // Ignore or log
            }
        }
    }

    fun isFavorite(trackId: String): Boolean {
        return _favoriteTrackIds.value.contains(trackId)
    }

    fun toggleFavorite(track: Track): Boolean {
        val currentIds = _favoriteTrackIds.value
        val exists = currentIds.contains(track.id)
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existingTrack = roomDb.trackDao().getTrackById(track.id)
            if (existingTrack == null) {
                roomDb.trackDao().insertTrack(track.copy(isLiked = !exists).toEntity())
            } else {
                roomDb.trackDao().setFavorite(track.id, !exists, now)
            }
            loadFavorites()
        }
        return !exists
    }
}
