package com.cypher.music.ui.main.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cypher.music.data.FavoritesManager
import com.cypher.music.data.LocalMusicRepository
import com.cypher.music.data.MediaCollection
import com.cypher.music.data.MusicRepository
import com.cypher.music.data.Playlist
import com.cypher.music.data.PlaylistRepository
import com.cypher.music.data.RecentlyPlayedItem
import com.cypher.music.data.SearchCategory
import com.cypher.music.domain.usecase.GetMediaUseCase
import com.cypher.music.model.Track
import com.cypher.music.network.YouTubeRepository
import com.cypher.music.playback.MediaCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    application: Application,
    val playlistRepository: PlaylistRepository = PlaylistRepository(application, CoroutineScope(Dispatchers.IO)),
    val favoritesManager: FavoritesManager = FavoritesManager(application, CoroutineScope(Dispatchers.IO)),
    val musicRepository: MusicRepository = MusicRepository(application),
    val getMediaUseCase: GetMediaUseCase = GetMediaUseCase(
        LocalMusicRepository(application),
        YouTubeRepository()
    )
) : AndroidViewModel(application) {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
    val recentlyPlayed: StateFlow<List<RecentlyPlayedItem>> = playlistRepository.recentlyPlayed
    val favoriteTracks: StateFlow<List<Track>> = favoritesManager.favoriteTracks
    val favoriteTrackIds: StateFlow<Set<String>> = favoritesManager.favoriteTrackIds

    // Media flows orchestrated through GetMediaUseCase
    val localTracks: StateFlow<List<Track>> = getMediaUseCase.localTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val featuredTracks: StateFlow<List<Track>> = getMediaUseCase.featuredTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editorialCollections: List<MediaCollection>
        get() = musicRepository.getEditorialPlaylists()

    val browseCategories: List<SearchCategory>
        get() = musicRepository.getBrowseCategories()

    val searchCategories: List<SearchCategory>
        get() = browseCategories

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _librarySection = MutableStateFlow<String?>(null)
    val librarySection: StateFlow<String?> = _librarySection.asStateFlow()

    private val _selectedArtist = MutableStateFlow<String?>(null)
    val selectedArtist: StateFlow<String?> = _selectedArtist.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum.asStateFlow()

    private val _isCreatePlaylistOpen = MutableStateFlow(false)
    val isCreatePlaylistOpen: StateFlow<Boolean> = _isCreatePlaylistOpen.asStateFlow()

    private val _pendingPlaylistTrack = MutableStateFlow<Track?>(null)
    val pendingPlaylistTrack: StateFlow<Track?> = _pendingPlaylistTrack.asStateFlow()

    private val _trackForPlaylistSheet = MutableStateFlow<Track?>(null)
    val trackForPlaylistSheet: StateFlow<Track?> = _trackForPlaylistSheet.asStateFlow()

    private val _downloadedTracks = MutableStateFlow<List<Track>>(emptyList())
    val downloadedTracks: StateFlow<List<Track>> = _downloadedTracks.asStateFlow()

    init {
        refreshMediaData()
        refreshDownloadedTracks()
    }

    fun refreshDownloadedTracks() {
        viewModelScope.launch {
            _downloadedTracks.value = MediaCacheManager.getDownloadedTracks(getApplication())
        }
    }

    fun downloadTrack(track: Track, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val file = MediaCacheManager.downloadTrackOffline(getApplication(), track)
            refreshDownloadedTracks()
            onComplete?.invoke(file != null)
        }
    }

    fun deleteDownloadedTrack(trackId: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = MediaCacheManager.deleteOfflineTrack(getApplication(), trackId)
            refreshDownloadedTracks()
            onComplete?.invoke(success)
        }
    }

    fun downloadPlaylist(
        playlist: Playlist,
        onProgress: ((current: Int, total: Int, trackTitle: String) -> Unit)? = null,
        onComplete: ((Int) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val count = MediaCacheManager.downloadPlaylistOffline(
                getApplication(),
                playlist,
                onProgress = { cur, tot, title ->
                    onProgress?.invoke(cur, tot, title)
                }
            )
            refreshDownloadedTracks()
            onComplete?.invoke(count)
        }
    }

    fun deletePlaylistDownloads(playlist: Playlist, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            MediaCacheManager.deletePlaylistDownloads(getApplication(), playlist)
            refreshDownloadedTracks()
            onComplete?.invoke()
        }
    }

    fun refreshMediaData() {
        viewModelScope.launch {
            getMediaUseCase.refresh()
            musicRepository.refreshData()
        }
    }

    fun setUserId(userId: String) {
        _selectedPlaylist.value = null
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun setLibrarySection(section: String?) {
        _librarySection.value = section
        if (section == null) {
            _selectedArtist.value = null
            _selectedAlbum.value = null
            _selectedPlaylist.value = null
        }
    }

    fun selectArtist(artist: String?) {
        _selectedArtist.value = artist
    }

    fun selectAlbum(album: String?) {
        _selectedAlbum.value = album
    }

    fun openCreatePlaylistDialog(initialTrack: Track? = null) {
        _pendingPlaylistTrack.value = initialTrack
        _isCreatePlaylistOpen.value = true
    }

    fun closeCreatePlaylistDialog() {
        _isCreatePlaylistOpen.value = false
        _pendingPlaylistTrack.value = null
    }

    fun openAddToPlaylistSheet(track: Track) {
        _trackForPlaylistSheet.value = track
    }

    fun closeAddToPlaylistSheet() {
        _trackForPlaylistSheet.value = null
    }

    fun createPlaylist(name: String, description: String = "", coverUri: String? = null, initialTrack: Track? = null) {
        viewModelScope.launch {
            val initialList = if (initialTrack != null) listOf(initialTrack) else emptyList()
            val newPlaylist = playlistRepository.createPlaylist(name, description, coverUri, initialList)
            closeCreatePlaylistDialog()
            _selectedPlaylist.value = newPlaylist
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch {
            playlistRepository.addTracksToPlaylist(playlist.id, listOf(track))
            closeAddToPlaylistSheet()
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = playlistRepository.getPlaylistById(playlist.id)
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = playlistRepository.getPlaylistById(playlistId)
            }
        }
    }

    fun reorderPlaylistTracks(playlistId: String, reorderedTracks: List<Track>) {
        viewModelScope.launch {
            playlistRepository.reorderTracks(playlistId, reorderedTracks)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = playlistRepository.getPlaylistById(playlistId)
            }
        }
    }

    fun updatePlaylistDetails(playlistId: String, name: String, description: String, coverUri: String? = null) {
        viewModelScope.launch {
            playlistRepository.updatePlaylistDetails(playlistId, name, description, coverUri)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = playlistRepository.getPlaylistById(playlistId)
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun toggleFavorite(track: Track): Boolean {
        return favoritesManager.toggleFavorite(track)
    }

    fun isFavorite(trackId: String): Boolean {
        return favoritesManager.isFavorite(trackId)
    }

    fun recordRecentlyPlayed(item: RecentlyPlayedItem) {
        playlistRepository.recordRecentlyPlayed(item)
    }
}
