package com.cypher.music.ui.main.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cypher.music.data.PlaylistRepository
import com.cypher.music.network.YouTubeAccountManager
import com.cypher.music.network.YouTubeAccountSession
import com.cypher.music.network.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YouTubeViewModel(
    application: Application,
    val accountManager: YouTubeAccountManager = YouTubeAccountManager(application),
    val youTubeRepository: YouTubeRepository = YouTubeRepository()
) : AndroidViewModel(application) {

    val session: StateFlow<YouTubeAccountSession> = accountManager.session

    private val _isLoginOpen = MutableStateFlow(false)
    val isLoginOpen: StateFlow<Boolean> = _isLoginOpen.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatusMsg = MutableStateFlow<String?>(null)
    val syncStatusMsg: StateFlow<String?> = _syncStatusMsg.asStateFlow()

    fun setUserId(userId: String) {
        accountManager.setUserId(userId)
    }

    fun openLogin() {
        _isLoginOpen.value = true
    }

    fun closeLogin() {
        _isLoginOpen.value = false
    }

    fun handleAuthCookies(cookies: String) {
        accountManager.saveCookies(cookies)
        closeLogin()
    }

    fun unlink() {
        accountManager.logout()
        _syncStatusMsg.value = null
    }

    fun syncPlaylists(playlistRepository: PlaylistRepository, onComplete: () -> Unit = {}) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMsg.value = "Conectando con YouTube Music..."
            var incomplete = 0
            try {
                val likedTracks = youTubeRepository.fetchLikedMusic(accountManager) { processed, total ->
                    _syncStatusMsg.value = if (total != null && total > 0) {
                        "Tus Me Gusta: $processed / $total canciones..."
                    } else {
                        "Tus Me Gusta: $processed canciones..."
                    }
                }
                if (likedTracks.isNotEmpty()) {
                    _syncStatusMsg.value = "Guardando ${likedTracks.size} canciones de 'Tus Me Gusta'..."
                    playlistRepository.syncPlaylist(
                        name = "Tus Me Gusta (YouTube)",
                        description = "Sincronizado desde YouTube Music",
                        tracks = likedTracks,
                        sourceId = "VLLM"
                    )
                }

                val remotePlaylists = youTubeRepository.fetchUserPlaylists(accountManager)
                for ((idx, pl) in remotePlaylists.withIndex()) {
                    _syncStatusMsg.value = "Importando playlist ${idx + 1} de ${remotePlaylists.size}: ${pl.title}..."
                    val result = youTubeRepository.fetchPlaylistTracksPaged(pl.id, accountManager) { processed, total ->
                        _syncStatusMsg.value = if (total != null && total > 0) {
                            "«${pl.title}»: $processed / $total canciones..."
                        } else {
                            "«${pl.title}»: $processed canciones..."
                        }
                    }
                    if (result.items.isEmpty()) {
                        incomplete++
                        continue
                    }
                    playlistRepository.syncPlaylist(
                        name = pl.title,
                        description = pl.description.ifEmpty { "Sincronizado desde YouTube Music" },
                        coverUri = pl.coverUrl,
                        tracks = result.items,
                        sourceId = pl.id
                    )
                    if (!result.complete) incomplete++
                }

                _syncStatusMsg.value = if (incomplete == 0) {
                    "Sincronización completada con éxito"
                } else {
                    "Sincronización incompleta: $incomplete playlist(s) no se completaron."
                }
            } catch (e: Exception) {
                _syncStatusMsg.value = "Error al sincronizar: ${e.message}"
            } finally {
                _isSyncing.value = false
                onComplete()
            }
        }
    }
}
