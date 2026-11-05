package com.cypher.music.ui.main.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cypher.music.data.Playlist
import com.cypher.music.data.PlaylistRepository
import com.cypher.music.data.SharedPlaylistPayload
import com.cypher.music.network.PlaylistShareResult
import com.cypher.music.network.PlaylistShareService
import com.cypher.music.utils.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShareViewModel(
    application: Application,
    val playlistRepository: PlaylistRepository = PlaylistRepository(application, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
) : AndroidViewModel(application) {

    private val _playlistToShare = MutableStateFlow<Playlist?>(null)
    val playlistToShare: StateFlow<Playlist?> = _playlistToShare.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _isGeneratingQr = MutableStateFlow(false)
    val isGeneratingQr: StateFlow<Boolean> = _isGeneratingQr.asStateFlow()

    private val _isScannerOpen = MutableStateFlow(false)
    val isScannerOpen: StateFlow<Boolean> = _isScannerOpen.asStateFlow()

    private val _pendingImportPayload = MutableStateFlow<SharedPlaylistPayload?>(null)
    val pendingImportPayload: StateFlow<SharedPlaylistPayload?> = _pendingImportPayload.asStateFlow()

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _shareResult = MutableStateFlow<PlaylistShareResult?>(null)
    val shareResult: StateFlow<PlaylistShareResult?> = _shareResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Serializes playlist structure (local and YouTube) and generates an offline QR Bitmap.
     */
    fun generateQrForPlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.Default) {
            _isGeneratingQr.value = true
            try {
                val payload = SharedPlaylistPayload.fromPlaylist(playlist)
                val qrData = payload.toP2pQrPayload()
                val bitmap = QrCodeGenerator.generateQrBitmap(
                    content = qrData,
                    sizePx = 700,
                    foregroundColor = android.graphics.Color.WHITE,
                    backgroundColor = android.graphics.Color.BLACK
                )
                _qrBitmap.value = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isGeneratingQr.value = false
            }
        }
    }

    fun openSharePlaylist(playlist: Playlist) {
        _playlistToShare.value = playlist
        _errorMessage.value = null
        _shareResult.value = null
        generateQrForPlaylist(playlist)
        viewModelScope.launch {
            _isSharing.value = true
            val result = PlaylistShareService.sharePlaylist(playlist)
            _isSharing.value = false
            result.onSuccess {
                _shareResult.value = it
            }.onFailure {
                _errorMessage.value = it.message ?: "Error al compartir playlist"
            }
        }
    }

    fun closeSharePlaylist() {
        _playlistToShare.value = null
        _qrBitmap.value = null
        _shareResult.value = null
        _errorMessage.value = null
    }

    fun openScanner() {
        _isScannerOpen.value = true
    }

    fun closeScanner() {
        _isScannerOpen.value = false
    }

    fun setPendingImportPayload(payload: SharedPlaylistPayload?) {
        _pendingImportPayload.value = payload
    }

    /**
     * Imports a playlist locally without requiring an internet connection.
     */
    fun importSharedPlaylist(
        playlist: Playlist,
        userId: String = "personal",
        onSuccess: (Playlist) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.createPlaylist(
                name = playlist.name,
                description = playlist.description,
                coverUri = playlist.coverUri,
                initialTracks = playlist.tracks
            )
            playlistRepository.loadData()
            _pendingImportPayload.value = null
            onSuccess(playlist)
        }
    }

    /**
     * Decodes and imports a raw QR code string instantly without internet.
     */
    fun importFromRawQr(
        rawCode: String,
        userId: String = "personal",
        onSuccess: (Playlist) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Instant offline P2P decoding
            val offlinePayload = SharedPlaylistPayload.fromP2pQrPayload(rawCode.trim())
            if (offlinePayload != null) {
                val playlist = offlinePayload.toPlaylist()
                playlistRepository.createPlaylist(
                    name = playlist.name,
                    description = playlist.description,
                    coverUri = playlist.coverUri,
                    initialTracks = playlist.tracks
                )
                playlistRepository.loadData()
                onSuccess(playlist)
                return@launch
            }

            // 2. Fallback to online shortcode resolution if not P2P
            val result = PlaylistShareService.resolvePlaylist(rawCode)
            result.onSuccess { payload ->
                val playlist = payload.toPlaylist()
                playlistRepository.createPlaylist(
                    name = playlist.name,
                    description = playlist.description,
                    coverUri = playlist.coverUri,
                    initialTracks = playlist.tracks
                )
                playlistRepository.loadData()
                onSuccess(playlist)
            }.onFailure { err ->
                onError(err.message ?: "Código QR de playlist inválido")
            }
        }
    }

    fun fetchAndPrepareImport(
        codeOrPayload: String,
        onSuccess: (SharedPlaylistPayload) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = PlaylistShareService.resolvePlaylist(codeOrPayload)
            result.onSuccess { payload ->
                _pendingImportPayload.value = payload
                onSuccess(payload)
            }.onFailure { err ->
                _errorMessage.value = err.message
                onError(err.message ?: "Código de playlist inválido")
            }
        }
    }
}
