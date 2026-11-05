package com.cypher.music.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cypher.music.data.ArtistNormalizer
import com.cypher.music.data.FavoritesManager
import com.cypher.music.data.MediaCollection
import com.cypher.music.data.MusicRepository
import com.cypher.music.data.Playlist
import com.cypher.music.data.PlaylistRepository
import com.cypher.music.data.RecentlyPlayedItem
import com.cypher.music.data.RecentlyPlayedType
import com.cypher.music.data.RecommendationSection
import com.cypher.music.data.ThemePreferences
import com.cypher.music.data.ThemePreferencesManager
import com.cypher.music.data.UnifiedSearchResults
import com.cypher.music.data.User
import com.cypher.music.data.UserListeningStats
import com.cypher.music.data.room.toStat
import com.cypher.music.model.Track
import com.cypher.music.network.YouTubeAccountManager
import com.cypher.music.network.YouTubeAccountSession
import com.cypher.music.network.YouTubeRepository
import com.cypher.music.playback.AudioVolumeManager
import com.cypher.music.playback.MusicPlayerManager
import com.cypher.music.playback.EqualizerState
import com.cypher.music.playback.SleepTimerMode
import com.cypher.music.data.BackupImportSummary
import com.cypher.music.data.SharedPlaylistPayload
import com.cypher.music.playback.PlayerState
import com.cypher.music.ui.components.CypherTab
import com.cypher.music.ui.main.viewmodels.PlayerViewModel
import com.cypher.music.ui.main.viewmodels.LibraryViewModel
import com.cypher.music.ui.main.viewmodels.YouTubeViewModel
import com.cypher.music.ui.main.viewmodels.ShareViewModel
import com.cypher.music.data.LiquidGlassConfig
import com.cypher.music.data.LyricsSettingsManager
import com.cypher.music.data.SearchCategory
import com.cypher.music.data.SearchHistoryManager
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val youTubeRepository = YouTubeRepository()
    private val musicRepository = MusicRepository(application, youTubeRepository)
    private val playerManager = MusicPlayerManager.getInstance(application)
    private val playlistRepository = PlaylistRepository(application, viewModelScope)
    private val favoritesManager = FavoritesManager(application, viewModelScope)
    private val volumeManager = AudioVolumeManager(application)
    private val themePreferencesManager = ThemePreferencesManager(application)
    val youtubeAccountManager = YouTubeAccountManager(application)

    // Decoupled Domain-Specific ViewModels (SOLID architecture)
    val playerViewModel: PlayerViewModel by lazy { PlayerViewModel(application, playerManager, volumeManager) }
    val libraryViewModel: LibraryViewModel by lazy { LibraryViewModel(application, playlistRepository, favoritesManager, musicRepository) }
    val youtubeViewModel: YouTubeViewModel by lazy { YouTubeViewModel(application, youtubeAccountManager, youTubeRepository) }
    val shareViewModel: ShareViewModel by lazy { ShareViewModel(application, playlistRepository) }
    val lyricsSettingsManager: LyricsSettingsManager by lazy { LyricsSettingsManager(application) }

    // Single Personal User Profile (Cypher Personal App)
    private val personalUser = User("personal", "Cypher User", 0xFF9D7BB0L)
    val currentUser: StateFlow<User?> = MutableStateFlow<User?>(personalUser).asStateFlow()
    val allUsers: StateFlow<List<User>> = MutableStateFlow(listOf(personalUser)).asStateFlow()
    val youtubeAccountSession: StateFlow<YouTubeAccountSession> = youtubeAccountManager.session

    val playerState: StateFlow<PlayerState> = playerManager.state
    val playbackProgress: StateFlow<Float> = playerManager.playbackProgress
    val playbackPositionMs: StateFlow<Long> = playerManager.playbackPositionMs
    val favoriteTracks: StateFlow<List<Track>> = favoritesManager.favoriteTracks
    val favoriteTrackIds: StateFlow<Set<String>> = favoritesManager.favoriteTrackIds
    val downloadedTracks: StateFlow<List<Track>> = libraryViewModel.downloadedTracks
    val volumeFraction: StateFlow<Float> = volumeManager.volumeFraction
    val themePreferences: StateFlow<ThemePreferences> = themePreferencesManager.preferences
    val liquidGlassConfig: StateFlow<LiquidGlassConfig> = themePreferences
        .map { it.liquidGlassConfig }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiquidGlassConfig())

    private val _isLiquidGlassSettingsOpen = MutableStateFlow(false)
    val isLiquidGlassSettingsOpen: StateFlow<Boolean> = _isLiquidGlassSettingsOpen.asStateFlow()
    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
    val recentlyPlayed: StateFlow<List<RecentlyPlayedItem>> = playlistRepository.recentlyPlayed

    // Playback Analytics & User Listening Stats
    private val _userListeningStats = MutableStateFlow(UserListeningStats())
    val userListeningStats: StateFlow<UserListeningStats> = _userListeningStats.asStateFlow()

    // Real Dynamic Recommendations
    private val _dynamicRecommendations = MutableStateFlow<List<RecommendationSection>>(emptyList())
    val dynamicRecommendations: StateFlow<List<RecommendationSection>> = _dynamicRecommendations.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    // YouTube Music Login & Sync State
    private val _isYouTubeLoginOpen = MutableStateFlow(false)
    val isYouTubeLoginOpen: StateFlow<Boolean> = _isYouTubeLoginOpen.asStateFlow()

    private val _isSyncingYouTube = MutableStateFlow(false)
    val isSyncingYouTube: StateFlow<Boolean> = _isSyncingYouTube.asStateFlow()

    private val _syncStatusMsg = MutableStateFlow<String?>(null)
    val syncStatusMsg: StateFlow<String?> = _syncStatusMsg.asStateFlow()

    // Playlist Dialog State for creating a playlist
    private val _pendingPlaylistTrack = MutableStateFlow<Track?>(null)
    val pendingPlaylistTrack: StateFlow<Track?> = _pendingPlaylistTrack.asStateFlow()

    private val _isCreatePlaylistOpen = MutableStateFlow(false)
    val isCreatePlaylistOpen: StateFlow<Boolean> = _isCreatePlaylistOpen.asStateFlow()

    // Add to Playlist Sheet State
    private val _trackForPlaylistSheet = MutableStateFlow<Track?>(null)
    val trackForPlaylistSheet: StateFlow<Track?> = _trackForPlaylistSheet.asStateFlow()

    // Selected Playlist for Drill-Down
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _currentTab = MutableStateFlow(CypherTab.LISTEN_NOW)
    val currentTab: StateFlow<CypherTab> = _currentTab.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    // QR Code and Short Code Sharing & Import State
    private val _playlistToShare = MutableStateFlow<Playlist?>(null)
    val playlistToShare: StateFlow<Playlist?> = _playlistToShare.asStateFlow()

    private val _isScannerOpen = MutableStateFlow(false)
    val isScannerOpen: StateFlow<Boolean> = _isScannerOpen.asStateFlow()

    private val _pendingImportPayload = MutableStateFlow<SharedPlaylistPayload?>(null)
    val pendingImportPayload: StateFlow<SharedPlaylistPayload?> = _pendingImportPayload.asStateFlow()

    // Active Library Sub-navigation
    private val _librarySection = MutableStateFlow<String?>(null)
    val librarySection: StateFlow<String?> = _librarySection.asStateFlow()

    private val _selectedArtist = MutableStateFlow<String?>(null)
    val selectedArtist: StateFlow<String?> = _selectedArtist.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum.asStateFlow()

    val localTracks: StateFlow<List<Track>> = musicRepository.localTracks
    val featuredTracks: StateFlow<List<Track>> = musicRepository.featuredTracks

    val editorialCollections: List<MediaCollection>
        get() = musicRepository.getEditorialPlaylists()

    val browseCategories: List<SearchCategory>
        get() = musicRepository.getBrowseCategories()

    val searchCategories: List<SearchCategory>
        get() = browseCategories

    val searchHistoryManager = SearchHistoryManager(application)
    val recentSearches: StateFlow<List<String>> = searchHistoryManager.recentSearches

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _unifiedSearchResults = MutableStateFlow(UnifiedSearchResults())
    val unifiedSearchResults: StateFlow<UnifiedSearchResults> = _unifiedSearchResults.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null
    private var recommendationJob: Job? = null

    init {
        // Clean any legacy demo tracks from Room database
        viewModelScope.launch(Dispatchers.IO) {
            try {
                playlistRepository.roomDb.trackDao().deleteDemoTracks()
                playlistRepository.roomDb.recentlyPlayedDao().deleteDemoRecentlyPlayed()
                playlistRepository.roomDb.playbackAnalyticsDao().deleteDemoAnalytics()
                playlistRepository.roomDb.playlistDao().deleteDemoPlaylistCrossRefs()
                playlistRepository.loadData()
                refreshListeningStats()
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Error cleaning demo tracks", e)
            }
        }

        viewModelScope.launch {
            musicRepository.refreshData()
        }

        // Wire effective listening time analytics (strictly ms when player is PLAYING)
        playerManager.onListeningTimeAccumulated = { track, elapsedMs, _ ->
            viewModelScope.launch(Dispatchers.IO) {
                playlistRepository.roomDb.playbackAnalyticsDao().recordListeningDuration(
                    track.id,
                    track.title,
                    track.artist,
                    track.artworkUrl,
                    elapsedMs,
                    System.currentTimeMillis()
                )
                refreshListeningStats()
            }
        }

        refreshListeningStats()

        // Trigger dynamic recommendations when recently played changes
        viewModelScope.launch {
            playlistRepository.recentlyPlayed.collect {
                refreshDynamicRecommendations()
            }
        }

        // React to crossfade setting updates
        viewModelScope.launch {
            themePreferencesManager.preferences.collect { prefs ->
                playerManager.setCrossfadeDuration(prefs.crossfadeSeconds)
            }
        }
    }

    fun refreshListeningStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = playlistRepository.roomDb.playbackAnalyticsDao()
                val totalMs = dao.totalListeningMs
                val totalPlays = dao.totalPlays
                val topArtists = dao.getTopArtists(10).map { it.toStat() }
                val topTracks = dao.getTopTracks(10).map { it.toStat() }
                _userListeningStats.value = UserListeningStats(totalMs, totalPlays, topArtists, topTracks)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun loginUser(username: String): Boolean = true

    fun registerUser(username: String, displayName: String, avatarColor: Long): User? = personalUser

    fun logoutUser() {}

    fun setSettingsOpen(open: Boolean) {
        _isSettingsOpen.value = open
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

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
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

    fun updateThemePreferences(transform: (ThemePreferences) -> ThemePreferences) {
        themePreferencesManager.updatePreferences(transform)
    }

    fun resetThemePreferences() {
        themePreferencesManager.resetToDefaults()
    }

    fun updateLiquidGlassConfig(newConfig: LiquidGlassConfig) {
        updateThemePreferences { it.copy(liquidGlassConfig = newConfig) }
    }

    fun openLiquidGlassSettings() {
        _isLiquidGlassSettingsOpen.value = true
    }

    fun closeLiquidGlassSettings() {
        _isLiquidGlassSettingsOpen.value = false
    }

    fun attachYouTubePlayer(ytPlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer) {
        playerManager.attachYouTubePlayer(ytPlayer)
    }

    fun selectTab(tab: CypherTab) {
        _currentTab.value = tab
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
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

    fun playFromUri(uri: android.net.Uri, title: String) {
        val track = Track(
            id = "intent_${System.currentTimeMillis()}",
            title = title,
            artist = "Archivo local",
            album = "Almacenamiento",
            durationMs = 0L,
            streamUrl = uri.toString(),
            sourceType = com.cypher.music.model.AudioSourceType.LOCAL
        )
        playTrack(track, listOf(track))
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        playerManager.playTrack(track, queue)
        playlistRepository.recordRecentlyPlayed(
            RecentlyPlayedItem(
                id = "track_${track.id}",
                type = RecentlyPlayedType.TRACK,
                title = track.title,
                subtitle = track.artist,
                artworkUrl = track.artworkUrl,
                streamUrl = track.streamUrl,
                sourceType = track.sourceType
            )
        )
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        if (playlist.tracks.isEmpty()) return
        val list = if (shuffle) playlist.tracks.shuffled() else playlist.tracks
        playTrack(list.first(), list)
        playlistRepository.recordRecentlyPlayed(
            RecentlyPlayedItem(
                id = "pl_${playlist.id}",
                type = RecentlyPlayedType.PLAYLIST,
                title = playlist.name,
                subtitle = "${playlist.trackCount} canciones",
                artworkUrl = playlist.coverUri ?: playlist.mosaicArtworks.firstOrNull(),
                extraId = playlist.id
            )
        )
    }

    fun playRecentlyPlayed(item: RecentlyPlayedItem) {
        when (item.type) {
            RecentlyPlayedType.PLAYLIST -> {
                val pl = playlists.value.find { it.id == item.extraId }
                if (pl != null && pl.tracks.isNotEmpty()) {
                    playPlaylist(pl)
                }
            }
            RecentlyPlayedType.TRACK -> {
                val track = localTracks.value.find { it.id == item.id.removePrefix("track_") }
                    ?: featuredTracks.value.find { it.id == item.id.removePrefix("track_") }
                    ?: Track(
                        id = item.id.removePrefix("track_"),
                        title = item.title,
                        artist = item.subtitle,
                        artworkUrl = item.artworkUrl,
                        streamUrl = item.streamUrl,
                        sourceType = item.sourceType
                    )
                playTrack(track, listOf(track))
            }
            RecentlyPlayedType.ALBUM -> {
                val albumTracks = localTracks.value.filter { it.album.equals(item.title, ignoreCase = true) }
                if (albumTracks.isNotEmpty()) {
                    playTrack(albumTracks.first(), albumTracks)
                }
            }
        }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun seekToProgress(fraction: Float) {
        playerManager.seekToProgress(fraction)
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun enqueueNext(track: Track) {
        playerManager.enqueueNext(track)
    }

    fun addToQueue(track: Track) {
        playerManager.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerManager.removeFromQueue(index)
    }

    fun jumpToQueueIndex(index: Int) {
        playerManager.jumpToQueueIndex(index)
    }

    fun toggleFavorite(track: Track): Boolean {
        val result = favoritesManager.toggleFavorite(track)
        refreshDynamicRecommendations()
        return result
    }

    fun isFavorite(trackId: String): Boolean {
        return favoritesManager.isFavorite(trackId)
    }

    fun setVolume(fraction: Float) {
        volumeManager.setVolumeFraction(fraction)
    }

    fun downloadTrack(track: Track, onComplete: ((Boolean) -> Unit)? = null) {
        libraryViewModel.downloadTrack(track, onComplete)
    }

    fun deleteDownloadedTrack(trackId: String, onComplete: ((Boolean) -> Unit)? = null) {
        libraryViewModel.deleteDownloadedTrack(trackId, onComplete)
    }

    fun downloadPlaylist(
        playlist: com.cypher.music.data.Playlist,
        onProgress: ((current: Int, total: Int, trackTitle: String) -> Unit)? = null,
        onComplete: ((Int) -> Unit)? = null
    ) {
        libraryViewModel.downloadPlaylist(playlist, onProgress, onComplete)
    }

    fun deletePlaylistDownloads(playlist: com.cypher.music.data.Playlist, onComplete: (() -> Unit)? = null) {
        libraryViewModel.deletePlaylistDownloads(playlist, onComplete)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query

        searchJob?.cancel()
        if (query.isBlank()) {
            _unifiedSearchResults.value = UnifiedSearchResults()
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(300)

            val localQuery = async(Dispatchers.IO) {
                val (matchedPlaylists, dbTracks) = playlistRepository.searchLocal(query)
                val mediaStoreTracks = musicRepository.localTracks.value.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
                }
                val combinedLocal = (dbTracks + mediaStoreTracks).distinctBy { it.id }
                Pair(matchedPlaylists, combinedLocal)
            }

            val remoteQuery = async(Dispatchers.IO) {
                try {
                    youTubeRepository.searchTracks(query)
                } catch (e: Exception) {
                    emptyList<Track>()
                }
            }

            val (localPlaylists, localTracks) = localQuery.await()
            val globalTracks = remoteQuery.await()

            val unified = UnifiedSearchResults(
                localTracks = localTracks,
                localPlaylists = localPlaylists,
                globalTracks = globalTracks
            )
            _unifiedSearchResults.value = unified
            _searchResults.value = localTracks + globalTracks
            _isSearching.value = false
        }
    }

    fun addSearchHistory(query: String) {
        searchHistoryManager.addQuery(query)
    }

    fun removeSearchHistory(query: String) {
        searchHistoryManager.removeQuery(query)
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearAll()
    }

    fun onCategorySelect(category: SearchCategory) {
        _searchQuery.value = category.title
        _currentTab.value = CypherTab.SEARCH
        addSearchHistory(category.title)
        onSearchQueryChange(category.title)
    }

    fun refreshLocalStorage() {
        viewModelScope.launch {
            musicRepository.refreshData()
            playlistRepository.loadData()
            favoritesManager.loadFavorites()
            refreshDynamicRecommendations()
        }
    }

    // YouTube Music Account & Synchronization
    fun openYouTubeLoginDialog() {
        _isYouTubeLoginOpen.value = true
    }

    fun closeYouTubeLoginDialog() {
        _isYouTubeLoginOpen.value = false
    }

    fun unlinkYouTubeAccount() {
        youtubeAccountManager.logout()
        _syncStatusMsg.value = "Cuenta de YouTube Music desvinculada."
    }

    fun clearSyncStatusMsg() {
        _syncStatusMsg.value = null
    }

    fun syncYouTubeMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingYouTube.value = true
            _syncStatusMsg.value = "Conectando y sincronizando con YouTube Music..."
            try {
                Log.d("AppleMusicPlayer", "syncYouTubeMusic: Starting sync")

                // 1. Liked Music
                val likedTracks = youTubeRepository.fetchLikedMusic(youtubeAccountManager)
                Log.d("AppleMusicPlayer", "syncYouTubeMusic: Liked tracks retrieved: ${likedTracks.size}")
                var favCount = 0
                for (track in likedTracks) {
                    if (!favoritesManager.isFavorite(track.id)) {
                        favoritesManager.toggleFavorite(track)
                        favCount++
                    }
                }

                // 2. Remote Playlists
                val remotePlaylists = youTubeRepository.fetchUserPlaylists(youtubeAccountManager)
                Log.d("AppleMusicPlayer", "syncYouTubeMusic: Remote playlists retrieved: ${remotePlaylists.size}")
                for (rPl in remotePlaylists) {
                    Log.d("AppleMusicPlayer", "syncYouTubeMusic: Syncing playlist '${rPl.title}' with ${rPl.tracks.size} tracks")
                    playlistRepository.createPlaylist(
                        name = rPl.title,
                        description = rPl.description,
                        coverUri = rPl.coverUrl,
                        initialTracks = rPl.tracks
                    )
                }

                youtubeAccountManager.updateSyncTimestamp()
                playlistRepository.loadData()
                favoritesManager.loadFavorites()
                refreshDynamicRecommendations()

                _syncStatusMsg.value = "¡Sincronización completada! $favCount canciones y ${remotePlaylists.size} playlists sincronizadas."
            } catch (e: Exception) {
                _syncStatusMsg.value = "Error al sincronizar: ${e.message}"
            } finally {
                _isSyncingYouTube.value = false
            }
        }
    }

    // Dynamic Real Recommendation Engine with Artist Normalization & Smart YouTube Search
    fun refreshDynamicRecommendations() {
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch(Dispatchers.IO) {
            val sections = mutableListOf<RecommendationSection>()

            // 1. Extract normalized top artists from listening analytics
            val topAnalyticsArtists = _userListeningStats.value.topArtists
                .map { ArtistNormalizer.normalize(it.artist) }
                .filter { it.isNotBlank() }

            // 2. Extract normalized artists from recently played
            val recents = playlistRepository.recentlyPlayed.value
            val recentArtists = recents
                .map { ArtistNormalizer.normalize(it.subtitle) }
                .filter { it.isNotBlank() && !it.contains("cancion", ignoreCase = true) }
                .distinct()

            // 3. Extract normalized artists from favorite tracks
            val favorites = favoritesManager.favoriteTracks.value
            val favoriteArtists = favorites
                .map { ArtistNormalizer.normalize(it.artist) }
                .filter { it.isNotBlank() }
                .distinct()

            val targetArtists = (topAnalyticsArtists + recentArtists + favoriteArtists)
                .distinct()
                .take(4)

            for (artist in targetArtists) {
                val localArtistTracks = localTracks.value.filter { ArtistNormalizer.areSameArtist(it.artist, artist) }
                val onlineArtistTracks = try {
                    youTubeRepository.searchTracks(artist)
                        .filter { ArtistNormalizer.areSameArtist(it.artist, artist) || it.title.contains(artist, ignoreCase = true) }
                } catch (e: Exception) {
                    emptyList()
                }

                val combined = (localArtistTracks + onlineArtistTracks).distinctBy { it.id }.take(8)
                if (combined.isNotEmpty()) {
                    sections.add(
                        RecommendationSection(
                            id = "rec_artist_$artist",
                            title = "Porque escuchaste a $artist",
                            subtitle = "Recomendaciones reales basadas en tus gustos",
                            tracks = combined
                        )
                    )
                }
            }

            // 4. Favorites section if user has favorites
            if (favorites.isNotEmpty()) {
                sections.add(
                    RecommendationSection(
                        id = "rec_favorites",
                        title = "Tus canciones favoritas",
                        subtitle = "Música destacada de tu colección",
                        tracks = favorites.take(10)
                    )
                )
            }

            // 5. Smart Recommendations based on user's top genres/artists
            if (targetArtists.isNotEmpty()) {
                val smartTracks = try {
                    youTubeRepository.getSmartRecommendations(targetArtists, listOf("Pop", "Urbano"))
                } catch (e: Exception) {
                    emptyList()
                }
                if (smartTracks.isNotEmpty()) {
                    sections.add(
                        RecommendationSection(
                            id = "rec_smart_mix",
                            title = "Mix personalizado para ti",
                            subtitle = "Basado en tus artistas más reproducidos",
                            tracks = smartTracks.take(8)
                        )
                    )
                }
            }

            // 6. Fallback for brand new accounts
            if (sections.isEmpty()) {
                val locals = localTracks.value
                if (locals.isNotEmpty()) {
                    sections.add(
                        RecommendationSection(
                            id = "rec_library",
                            title = "De tu biblioteca",
                            subtitle = "Tus archivos locales",
                            tracks = locals.take(10)
                        )
                    )
                } else {
                    val trending = try {
                        youTubeRepository.searchTracks("éxitos de hoy")
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (trending.isNotEmpty()) {
                        sections.add(
                            RecommendationSection(
                                id = "rec_trending",
                                title = "Tendencias recomendadas",
                                subtitle = "Lo más escuchado ahora",
                                tracks = trending.take(10)
                            )
                        )
                    }
                }
            }

            _dynamicRecommendations.value = sections
        }
    }

    // Equalizer & AudioFX
    val equalizerState: StateFlow<EqualizerState> = playerManager.equalizerManager.state

    private val _isEqualizerSheetOpen = MutableStateFlow(false)
    val isEqualizerSheetOpen: StateFlow<Boolean> = _isEqualizerSheetOpen.asStateFlow()

    fun setEqualizerSheetOpen(open: Boolean) {
        _isEqualizerSheetOpen.value = open
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playerManager.equalizerManager.setEnabled(enabled)
    }

    fun applyEqualizerPreset(presetIndex: Short) {
        playerManager.equalizerManager.applyPreset(presetIndex)
    }

    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) {
        playerManager.equalizerManager.setBandLevel(bandIndex, levelMb)
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        playerManager.equalizerManager.setBassBoostEnabled(enabled)
    }

    fun setBassBoostStrength(strength: Short) {
        playerManager.equalizerManager.setBassBoostStrength(strength)
    }

    // Sleep Timer
    val sleepTimerMode: StateFlow<SleepTimerMode> = playerManager.sleepTimerMode
    val sleepTimerRemainingSeconds: StateFlow<Long?> = playerManager.sleepTimerRemainingSeconds

    private val _isSleepTimerSheetOpen = MutableStateFlow(false)
    val isSleepTimerSheetOpen: StateFlow<Boolean> = _isSleepTimerSheetOpen.asStateFlow()

    fun setSleepTimerSheetOpen(open: Boolean) {
        _isSleepTimerSheetOpen.value = open
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        playerManager.setSleepTimer(mode)
    }

    // Backup & Restore Database
    fun exportDatabaseBackup(): String {
        return try {
            val playlists = playlistRepository.roomDb.playlistDao().allPlaylistsWithTracks
            val favs = playlistRepository.roomDb.trackDao().favoriteTracks

            val root = org.json.JSONObject()
            root.put("version", 3)
            root.put("appName", "Cypher")
            root.put("timestamp", System.currentTimeMillis())

            val plArray = org.json.JSONArray()
            for (p in playlists) {
                val pObj = org.json.JSONObject()
                pObj.put("id", p.playlist.id)
                pObj.put("name", p.playlist.name)
                pObj.put("description", p.playlist.description)
                pObj.put("coverUri", p.playlist.coverUri ?: "")
                pObj.put("createdAt", p.playlist.createdAt)
                pObj.put("updatedAt", p.playlist.updatedAt)
                pObj.put("isPrivate", p.playlist.isPrivate)
                val tArray = org.json.JSONArray()
                for (t in p.tracks) {
                    val tObj = org.json.JSONObject()
                    tObj.put("id", t.id)
                    tObj.put("title", t.title)
                    tObj.put("artist", t.artist)
                    tObj.put("album", t.album)
                    tObj.put("durationMs", t.durationMs)
                    tObj.put("artworkUrl", t.artworkUrl ?: "")
                    tObj.put("streamUrl", t.streamUrl)
                    tObj.put("sourceType", t.sourceType)
                    tArray.put(tObj)
                }
                pObj.put("tracks", tArray)
                plArray.put(pObj)
            }
            root.put("playlists", plArray)

            val favArray = org.json.JSONArray()
            for (f in favs) {
                val fObj = org.json.JSONObject()
                fObj.put("id", f.id)
                fObj.put("title", f.title)
                fObj.put("artist", f.artist)
                fObj.put("album", f.album)
                fObj.put("durationMs", f.durationMs)
                fObj.put("artworkUrl", f.artworkUrl ?: "")
                fObj.put("streamUrl", f.streamUrl)
                fObj.put("sourceType", f.sourceType)
                favArray.put(fObj)
            }
            root.put("favorites", favArray)

            root.toString(2)
        } catch (e: Exception) {
            "{}"
        }
    }

    fun importDatabaseBackup(jsonStr: String): BackupImportSummary {
        return try {
            val root = org.json.JSONObject(jsonStr)
            val plArray = root.optJSONArray("playlists") ?: org.json.JSONArray()
            val favArray = root.optJSONArray("favorites") ?: org.json.JSONArray()

            for (i in 0 until plArray.length()) {
                val pObj = plArray.getJSONObject(i)
                val name = pObj.optString("name", "Playlist Importada")
                val desc = pObj.optString("description", "")
                val cover = pObj.optString("coverUri", null)
                val tArray = pObj.optJSONArray("tracks") ?: org.json.JSONArray()
                val tracks = mutableListOf<Track>()
                for (j in 0 until tArray.length()) {
                    val tObj = tArray.getJSONObject(j)
                    tracks.add(
                        Track(
                            id = tObj.optString("id", java.util.UUID.randomUUID().toString()),
                            title = tObj.optString("title", "Pista"),
                            artist = tObj.optString("artist", "Artista"),
                            album = tObj.optString("album", "Álbum"),
                            durationMs = tObj.optLong("durationMs", 0L),
                            artworkUrl = tObj.optString("artworkUrl", null).ifEmpty { null },
                            streamUrl = tObj.optString("streamUrl", ""),
                            sourceType = runCatching { com.cypher.music.model.AudioSourceType.valueOf(tObj.optString("sourceType", "LOCAL")) }.getOrDefault(com.cypher.music.model.AudioSourceType.LOCAL)
                        )
                    )
                }
                viewModelScope.launch {
                    playlistRepository.createPlaylist(name, desc, cover, tracks)
                }
            }

            for (i in 0 until favArray.length()) {
                val fObj = favArray.getJSONObject(i)
                val track = Track(
                    id = fObj.optString("id", java.util.UUID.randomUUID().toString()),
                    title = fObj.optString("title", "Pista"),
                    artist = fObj.optString("artist", "Artista"),
                    album = fObj.optString("album", "Álbum"),
                    durationMs = fObj.optLong("durationMs", 0L),
                    artworkUrl = fObj.optString("artworkUrl", null).ifEmpty { null },
                    streamUrl = fObj.optString("streamUrl", ""),
                    sourceType = runCatching { com.cypher.music.model.AudioSourceType.valueOf(fObj.optString("sourceType", "LOCAL")) }.getOrDefault(com.cypher.music.model.AudioSourceType.LOCAL)
                )
                if (!favoritesManager.isFavorite(track.id)) {
                    favoritesManager.toggleFavorite(track)
                }
            }

            playlistRepository.loadData()
            refreshListeningStats()
            refreshDynamicRecommendations()

            BackupImportSummary(
                playlistsCount = plArray.length(),
                tracksCount = 0,
                favoritesCount = favArray.length(),
                analyticsCount = 0,
                success = true
            )
        } catch (e: Exception) {
            BackupImportSummary(success = false, errorMessage = e.message)
        }
    }

    // QR Code and Short Code Sharing & Import
    fun openSharePlaylist(playlist: Playlist) {
        _playlistToShare.value = playlist
    }

    fun closeSharePlaylist() {
        _playlistToShare.value = null
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

    fun importSharedPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(
                name = playlist.name,
                description = playlist.description,
                coverUri = playlist.coverUri,
                initialTracks = playlist.tracks
            )
            playlistRepository.loadData()
            _selectedPlaylist.value = playlist
            selectTab(CypherTab.LIBRARY)
            _pendingImportPayload.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        volumeManager.release()
        // Do not release singleton playerManager here to preserve background and Android Auto playback
    }
}
