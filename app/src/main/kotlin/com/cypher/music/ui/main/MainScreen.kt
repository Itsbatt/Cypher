package com.cypher.music.ui.main

import android.util.Log
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cypher.music.R
import com.cypher.music.network.YouTubeDns
import com.cypher.music.ui.components.AddToPlaylistSheet
import com.cypher.music.ui.components.LiquidGlassSettingsSheet
import com.cypher.music.ui.components.CypherEqualizerSheet
import com.cypher.music.ui.components.CypherSleepTimerSheet
import com.cypher.music.ui.components.SharePlaylistDialog
import com.cypher.music.ui.components.QrScannerSheet
import com.cypher.music.ui.components.PlaylistImportPreviewDialog
import com.cypher.music.ui.components.SpotifyImportSheet
import com.cypher.music.ui.components.SpotifySyncSheet
import com.cypher.music.ui.components.UpdateBanner
import com.cypher.music.ui.components.CypherMiniPlayer
import com.cypher.music.ui.components.CypherNavBar
import com.cypher.music.ui.components.CypherNowPlayingModal
import com.cypher.music.ui.components.CypherTab
import com.cypher.music.ui.components.CreatePlaylistDialog
import com.cypher.music.ui.components.YouTubeLoginDialog
import com.cypher.music.ui.components.UsernameDialog
import com.cypher.music.ui.screens.LibraryScreen
import com.cypher.music.ui.screens.ListenNowScreen
import com.cypher.music.ui.screens.SearchScreen
import com.cypher.music.ui.screens.SettingsScreen
import com.cypher.music.ui.theme.AmbientArtworkColors
import com.cypher.music.ui.theme.CypherDeepPlum
import com.cypher.music.ui.theme.CypherLavender
import com.cypher.music.ui.theme.CypherLavenderDark
import com.cypher.music.ui.theme.PaletteExtractor
import com.cypher.music.ui.theme.ThemeResolver
import com.cypher.music.ui.utils.AppHaptics
import com.cypher.music.update.UpdateViewModel
import com.cypher.music.ui.wrapped.WrappedScreen
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

private fun findInternalWebView(view: View): WebView? {
    if (view is WebView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = findInternalWebView(view.getChildAt(i))
            if (child != null) return child
        }
    }
    return null
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    Log.d("AppleMusicPlayer", "MainScreen entered composition")
    val context = LocalContext.current

    val currentUser by viewModel.currentUser.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()

    val currentTab by viewModel.currentTab.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val themePrefs by viewModel.themePreferences.collectAsState()

    val localTracks by viewModel.localTracks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val unifiedResults by viewModel.unifiedSearchResults.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()

    val favoriteTracks by viewModel.favoriteTracks.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val volumeFraction by viewModel.volumeFraction.collectAsState()
    val librarySection by viewModel.librarySection.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()

    val playlists by viewModel.playlists.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val dynamicRecommendations by viewModel.dynamicRecommendations.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val isCreatePlaylistOpen by viewModel.isCreatePlaylistOpen.collectAsState()
    val pendingPlaylistTrack by viewModel.pendingPlaylistTrack.collectAsState()
    val trackForPlaylistSheet by viewModel.trackForPlaylistSheet.collectAsState()

    val userListeningStats by viewModel.userListeningStats.collectAsState()
    val isWrappedOpen by viewModel.isWrappedOpen.collectAsState()
    val wrappedStats by viewModel.wrappedStats.collectAsState()
    val ytSession by viewModel.youtubeAccountSession.collectAsState()
    val isYouTubeLoginOpen by viewModel.isYouTubeLoginOpen.collectAsState()
    val isSyncingYouTube by viewModel.isSyncingYouTube.collectAsState()
    val syncStatusMsg by viewModel.syncStatusMsg.collectAsState()
    val equalizerState by viewModel.equalizerState.collectAsState()
    val isEqualizerSheetOpen by viewModel.isEqualizerSheetOpen.collectAsState()
    val isLoudnessNormalizationEnabled by viewModel.isLoudnessNormalizationEnabled.collectAsState()
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val isSleepTimerSheetOpen by viewModel.isSleepTimerSheetOpen.collectAsState()
    val playlistToShare by viewModel.playlistToShare.collectAsState()
    val isScannerOpen by viewModel.isScannerOpen.collectAsState()
    val isLiquidGlassSettingsOpen by viewModel.isLiquidGlassSettingsOpen.collectAsState()
    val pendingImportPayload by viewModel.pendingImportPayload.collectAsState()
    val spotifyImport by viewModel.spotifyImport.collectAsState()
    val spotifyTransfer by viewModel.spotifyTransfer.collectAsState()

    val spotifyFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importSpotifyExport(it) }
    }
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsState()

    // Initialize Haptic Engine for micro-ticks & controls
    LaunchedEffect(Unit) {
        AppHaptics.init(context)
    }

    // Dynamic Artwork Palette extraction for ThemeResolver
    var artworkPalette by remember { mutableStateOf<AmbientArtworkColors?>(null) }
    val currentArtworkUrl = playerState.currentTrack?.artworkUrl

    LaunchedEffect(currentArtworkUrl) {
        if (!currentArtworkUrl.isNullOrBlank()) {
            artworkPalette = PaletteExtractor.extractColors(context, currentArtworkUrl)
        } else {
            artworkPalette = null
        }
    }

    // Resolve unified theme colors based on user preferences and current artwork
    val themeColors = remember(themePrefs, artworkPalette) {
        ThemeResolver.resolve(themePrefs, artworkPalette)
    }

    // Dynamic reactive bottom padding: adapts to navigation bar, dock, and miniplayer
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockHeight = 64.dp
    val miniPlayerHeight = 62.dp
    val isPlayerActive = playerState.currentTrack != null
    val bottomContentPadding = navBarBottomInset + dockHeight + (if (isPlayerActive) miniPlayerHeight + 10.dp else 0.dp) + 24.dp

    // Navigation drill-down helpers
    fun navigateToArtist(artist: String) {
        viewModel.selectTab(CypherTab.LIBRARY)
        viewModel.setLibrarySection("artistas")
        viewModel.selectArtist(artist)
    }

    fun navigateToAlbum(album: String) {
        viewModel.selectTab(CypherTab.LIBRARY)
        viewModel.setLibrarySection("albumes")
        viewModel.selectAlbum(album)
    }

    // System Back Gesture handling with nested levels
    BackHandler(enabled = isSettingsOpen || isNowPlayingExpanded || selectedPlaylist != null || librarySection != null || selectedArtist != null || selectedAlbum != null) {
        when {
            isSettingsOpen -> viewModel.setSettingsOpen(false)
            isNowPlayingExpanded -> viewModel.setNowPlayingExpanded(false)
            selectedPlaylist != null -> viewModel.selectPlaylist(null)
            selectedAlbum != null -> viewModel.selectAlbum(null)
            selectedArtist != null -> viewModel.selectArtist(null)
            librarySection != null -> viewModel.setLibrarySection(null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        // YouTube Audio Engine: Positioned within screen coordinates with minimal alpha
        AndroidView(
            factory = { ctx ->
                Log.d("AppleMusicPlayer", "AndroidView factory creating YouTubePlayerView from XML...")
                val view = LayoutInflater.from(ctx).inflate(R.layout.youtube_player_layout, null, false) as YouTubePlayerView
                view.apply {
                    enableBackgroundPlayback(true)
                    isClickable = false
                    isFocusable = false

                    val okProxyClient = OkHttpClient.Builder()
                        .dns(YouTubeDns())
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()

                    val internalWebView = findInternalWebView(this)
                    internalWebView?.settings?.let { s ->
                        s.mediaPlaybackRequiresUserGesture = false
                        s.javaScriptEnabled = true
                        s.domStorageEnabled = true
                    }

                    internalWebView?.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val uri = request?.url ?: return null
                            val urlStr = uri.toString()

                            if (urlStr.contains("youtube.com/iframe_api")) {
                                val js = """
                                    var scriptUrl = 'https://www.youtube-nocookie.com/s/player/11a45ffb/www-widgetapi.vflset/www-widgetapi.js';
                                    try {
                                        var ttPolicy = window.trustedTypes.createPolicy("youtube-widget-api", { createScriptURL: function(x) { return x; } });
                                        scriptUrl = ttPolicy.createScriptURL(scriptUrl);
                                    } catch(e) {}
                                    var YT; if (!window["YT"]) YT = { loading: 0, loaded: 0 };
                                    var YTConfig; if (!window["YTConfig"]) YTConfig = { "host": "https://www.youtube-nocookie.com" };
                                    if (!YT.loading) {
                                        YT.loading = 1;
                                        (function() {
                                             var l = [];
                                             YT.ready = function(f) { if (YT.loaded) f(); else l.push(f); };
                                             window.onYTReady = function() { YT.loaded = 1; var i = 0; for (; i < l.length; i++) try { l[i](); } catch(e) {} };
                                             YT.setConfig = function(c) { for (var k in c) if (c.hasOwnProperty(k)) YTConfig[k] = c[k]; };
                                             var a = document.createElement("script");
                                             a.type = "text/javascript";
                                             a.id = "www-widgetapi-script";
                                             a.src = scriptUrl;
                                             a.async = true;
                                             var b = document.getElementsByTagName("script")[0];
                                             b.parentNode.insertBefore(a, b);
                                        })();
                                    }
                                """.trimIndent()
                                return WebResourceResponse(
                                    "application/javascript",
                                    "utf-8",
                                    ByteArrayInputStream(js.toByteArray(Charsets.UTF_8))
                                )
                            }

                            if (uri.host == "www.youtube.com" || uri.host == "m.youtube.com") {
                                try {
                                    val reqBuilder = Request.Builder().url(urlStr)
                                    request.requestHeaders?.forEach { (k, v) -> reqBuilder.header(k, v) }
                                    val resp = okProxyClient.newCall(reqBuilder.build()).execute()
                                    val contentType = resp.header("content-type", "text/html") ?: "text/html"
                                    val mimeType = contentType.substringBefore(";").trim()
                                    val encoding = if (contentType.contains("charset=")) contentType.substringAfter("charset=").trim() else "utf-8"
                                    return WebResourceResponse(
                                        mimeType,
                                        encoding,
                                        resp.code,
                                        resp.message.ifEmpty { "OK" },
                                        resp.headers.toMap(),
                                        resp.body?.byteStream()
                                    )
                                } catch (e: Exception) {
                                    Log.e("AppleMusicPlayer", "Failed to proxy request: $urlStr", e)
                                }
                            }

                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    internalWebView?.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("AppleMusicPlayer_JS", "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                            return true
                        }
                    }

                    val playerOptions = IFramePlayerOptions.Builder(ctx)
                        .controls(0)
                        .autoplay(1)
                        .rel(0)
                        .origin("https://www.youtube-nocookie.com")
                        .privacyEnhancedMode(true)
                        .build()

                    initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            Log.d("AppleMusicPlayer", "MainScreen: YouTubePlayer onReady successfully fired!")
                            viewModel.attachYouTubePlayer(youTubePlayer)
                        }

                        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                            Log.d("AppleMusicPlayer", "MainScreen: onStateChange -> $state")
                        }

                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            Log.e("AppleMusicPlayer", "MainScreen: YouTubePlayer onError -> $error")
                        }
                    }, false, playerOptions)
                }
            },
            modifier = Modifier
                .size(1.dp, 1.dp)
                .alpha(0.005f)
        )

        // Tab Content: 3 Primary Sections (Inicio, Biblioteca, Buscar)
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            when (currentTab) {
                CypherTab.LISTEN_NOW -> {
                    ListenNowScreen(
                        user = currentUser,
                        dynamicSections = dynamicRecommendations,
                        localTracks = localTracks,
                        playlists = playlists,
                        recentlyPlayed = recentlyPlayed,
                        currentTrackId = playerState.currentTrack?.id,
                        favoriteTrackIds = favoriteTrackIds,
                        listeningStats = userListeningStats,
                        themePrefs = themePrefs,
                        themeColors = themeColors,
                        bottomPadding = bottomContentPadding,
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        onCreatePlaylist = { viewModel.openCreatePlaylistDialog() },
                        onSelectPlaylist = { pl ->
                            viewModel.selectTab(CypherTab.LIBRARY)
                            viewModel.selectPlaylist(pl)
                        },
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onPlayRecentlyPlayed = { item -> viewModel.playRecentlyPlayed(item) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.enqueueNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistSheet(it) },
                        onViewArtist = { navigateToArtist(it) },
                        onViewAlbum = { navigateToAlbum(it) }
                    )
                }
                CypherTab.LIBRARY -> {
                    LibraryScreen(
                        localTracks = localTracks,
                        favoriteTracks = favoriteTracks,
                        favoriteTrackIds = favoriteTrackIds,
                        downloadedTracks = downloadedTracks,
                        playlists = playlists,
                        currentTrackId = playerState.currentTrack?.id,
                        activeSection = librarySection,
                        selectedArtist = selectedArtist,
                        selectedAlbum = selectedAlbum,
                        selectedPlaylist = selectedPlaylist,
                        themePrefs = themePrefs,
                        themeColors = themeColors,
                        bottomPadding = bottomContentPadding,
                        onSectionChange = { viewModel.setLibrarySection(it) },
                        onArtistSelect = { viewModel.selectArtist(it) },
                        onAlbumSelect = { viewModel.selectAlbum(it) },
                        onPlaylistSelect = { viewModel.selectPlaylist(it) },
                        onCreatePlaylist = { viewModel.openCreatePlaylistDialog() },
                        onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                        onPlayNext = { viewModel.enqueueNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistSheet(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRemoveTrackFromPlaylist = { plId, trkId -> viewModel.removeTrackFromPlaylist(plId, trkId) },
                        onReorderPlaylistTracks = { plId, trks -> viewModel.reorderPlaylistTracks(plId, trks) },
                        onUpdatePlaylistDetails = { plId, name, desc -> viewModel.updatePlaylistDetails(plId, name, desc) },
                        onDeletePlaylist = { plId -> viewModel.deletePlaylist(plId) },
                        onRefreshStorage = { viewModel.refreshLocalStorage() },
                        onOpenImportScanner = { viewModel.openScanner() },
                        onSharePlaylist = { viewModel.openSharePlaylist(it) },
                        onDownloadPlaylist = { pl ->
                            Toast.makeText(context, "Descargando «${pl.name}» (${pl.tracks.size} canciones)...", Toast.LENGTH_SHORT).show()
                            viewModel.downloadPlaylist(pl) { count ->
                                Toast.makeText(context, "Completado: $count canciones descargadas de «${pl.name}»", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeletePlaylistDownloads = { pl ->
                            viewModel.deletePlaylistDownloads(pl) {
                                Toast.makeText(context, "Descargas eliminadas de «${pl.name}»", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRetryPlaylistSync = { playlistId -> viewModel.retryPlaylistSync(playlistId) }
                    )
                }
                CypherTab.SEARCH -> {
                    SearchScreen(
                        searchQuery = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        searchResults = searchResults,
                        unifiedResults = unifiedResults,
                        isSearching = isSearching,
                        categories = viewModel.browseCategories,
                        searchCategories = viewModel.searchCategories,
                        recentSearches = recentSearches,
                        onDeleteHistoryItem = { viewModel.removeSearchHistory(it) },
                        onClearHistory = { viewModel.clearSearchHistory() },
                        onExecuteSearch = { query ->
                            viewModel.addSearchHistory(query)
                            viewModel.onSearchQueryChange(query)
                        },
                        currentTrackId = playerState.currentTrack?.id,
                        favoriteTrackIds = favoriteTrackIds,
                        themePrefs = themePrefs,
                        themeColors = themeColors,
                        bottomPadding = bottomContentPadding,
                        onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                        onPlaylistClick = { pl ->
                            viewModel.selectPlaylist(pl)
                            viewModel.selectTab(CypherTab.LIBRARY)
                        },
                        onCategoryClick = { viewModel.onCategorySelect(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.enqueueNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistSheet(it) },
                        onViewArtist = { navigateToArtist(it) },
                        onViewAlbum = { navigateToAlbum(it) }
                    )
                }
            }
        }

        // Bottom Controls: Floating MiniPlayer + Solid Capsule Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Floating Mini Player (shown when a track is active)
            if (playerState.currentTrack != null) {
                CypherMiniPlayer(
                    playerState = playerState,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.playNext() },
                    onPrevious = { viewModel.playPrevious() },
                    onExpand = { viewModel.setNowPlayingExpanded(true) },
                    themePrefs = themePrefs,
                    accentColor = themeColors.accent,
                    currentPositionProvider = { viewModel.playbackProgress.value }
                )
            }

            // Solid Floating Capsule Navigation Dock (Inicio, Biblioteca, Buscar)
            CypherNavBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    if (currentTab == tab && tab == CypherTab.LIBRARY) {
                        viewModel.setLibrarySection(null)
                        viewModel.selectPlaylist(null)
                    }
                    viewModel.selectTab(tab)
                },
                themePrefs = themePrefs,
                accentColor = themeColors.accent
            )
        }

        // Fullscreen Now Playing Sheet
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playerState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            CypherNowPlayingModal(
                playerState = playerState,
                isFavorite = viewModel.isFavorite(playerState.currentTrack?.id ?: ""),
                volumeFraction = volumeFraction,
                playbackProgress = playbackProgress,
                currentPositionMs = playbackPositionMs,
                onDismiss = { viewModel.setNowPlayingExpanded(false) },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                onSeek = { viewModel.seekToProgress(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onSetVolume = { viewModel.setVolume(it) },
                onSelectQueueIndex = { viewModel.jumpToQueueIndex(it) },
                onRemoveQueueIndex = { viewModel.removeFromQueue(it) },
                onViewArtist = { artist ->
                    viewModel.setNowPlayingExpanded(false)
                    navigateToArtist(artist)
                },
                onViewAlbum = { album ->
                    viewModel.setNowPlayingExpanded(false)
                    navigateToAlbum(album)
                },
                onAddToPlaylist = { viewModel.openAddToPlaylistSheet(it) },
                onOpenSleepTimer = { viewModel.setSleepTimerSheetOpen(true) },
                onOpenEqualizer = { viewModel.setEqualizerSheetOpen(true) },
                onDownload = { track ->
                    Toast.makeText(context, "Descargando \"${track.title}\" para escuchar sin conexión...", Toast.LENGTH_SHORT).show()
                    viewModel.downloadTrack(track) { success ->
                        if (success) {
                            Toast.makeText(context, "Descargado: \"${track.title}\"", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error al descargar \"${track.title}\"", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDeleteDownload = { track ->
                    viewModel.deleteDownloadedTrack(track.id) {
                        Toast.makeText(context, "Descarga eliminada: \"${track.title}\"", Toast.LENGTH_SHORT).show()
                    }
                },
                themePrefs = themePrefs,
                accentColor = themeColors.accent
            )
        }

        // Settings Screen
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            SettingsScreen(
                user = currentUser,
                listeningStats = userListeningStats,
                ytSession = ytSession,
                isSyncingYouTube = isSyncingYouTube,
                syncStatusMsg = syncStatusMsg,
                themePrefs = themePrefs,
                themeColors = themeColors,
                bottomPadding = bottomContentPadding,
                onUpdatePrefs = { viewModel.updateThemePreferences(it) },
                onResetDefaults = { viewModel.resetThemePreferences() },
                onUpdateUsername = { viewModel.updateUsername(it) },
                onOpenYouTubeLogin = { viewModel.openYouTubeLoginDialog() },
                onSyncYouTube = { viewModel.syncYouTubeMusic() },
                onUnlinkYouTube = { viewModel.unlinkYouTubeAccount() },
                onOpenSpotifyImport = { viewModel.openSpotifyImport() },
                onImportSpotifyFile = { spotifyFilePicker.launch(arrayOf("*/*")) },
                onOpenEqualizer = { viewModel.setEqualizerSheetOpen(true) },
                isLoudnessNormalizationEnabled = isLoudnessNormalizationEnabled,
                onToggleLoudnessNormalization = { viewModel.setLoudnessNormalizationEnabled(it) },
                onOpenSleepTimer = { viewModel.setSleepTimerSheetOpen(true) },
                onOpenLiquidGlassSettings = { viewModel.openLiquidGlassSettings() },
                onOpenWrapped = { viewModel.openWrapped() },
                sleepTimerMode = sleepTimerMode,
                sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                onExportBackup = { viewModel.exportDatabaseBackup() },
                onImportBackup = { viewModel.importDatabaseBackup(it) },
                onBack = { viewModel.setSettingsOpen(false) }
            )
        }

        // YouTube Music Login Dialog
        if (isYouTubeLoginOpen) {
            YouTubeLoginDialog(
                accountManager = viewModel.youtubeAccountManager,
                accentColor = themeColors.accent,
                onDismiss = { viewModel.closeYouTubeLoginDialog() },
                onLoginSuccess = {
                    viewModel.closeYouTubeLoginDialog()
                    viewModel.syncYouTubeMusic()
                }
            )
        }

        // Create Playlist Dialog
        if (isCreatePlaylistOpen) {
            CreatePlaylistDialog(
                initialTrack = pendingPlaylistTrack,
                accentColor = themeColors.accent,
                onDismiss = { viewModel.closeCreatePlaylistDialog() },
                onConfirm = { name, desc, track ->
                    viewModel.createPlaylist(name, desc, null, track)
                }
            )
        }

        // Add to Playlist Sheet
        trackForPlaylistSheet?.let { track ->
            AddToPlaylistSheet(
                track = track,
                playlists = playlists,
                accentColor = themeColors.accent,
                onDismiss = { viewModel.closeAddToPlaylistSheet() },
                onSelectPlaylist = { pl -> viewModel.addTrackToPlaylist(pl, track) },
                onCreateNewPlaylist = {
                    viewModel.closeAddToPlaylistSheet()
                    viewModel.openCreatePlaylistDialog(track)
                }
            )
        }

        // Native DSP Equalizer Sheet
        if (isEqualizerSheetOpen) {
            CypherEqualizerSheet(
                state = equalizerState,
                themeColors = themeColors,
                isLoudnessNormalizationEnabled = isLoudnessNormalizationEnabled,
                onToggleLoudnessNormalization = { viewModel.setLoudnessNormalizationEnabled(it) },
                onToggleEnabled = { viewModel.setEqualizerEnabled(it) },
                onSelectPreset = { viewModel.applyEqualizerPreset(it) },
                onSetBandLevel = { idx, lvl -> viewModel.setEqualizerBandLevel(idx, lvl) },
                onToggleBassBoost = { viewModel.setBassBoostEnabled(it) },
                onSetBassBoostStrength = { viewModel.setBassBoostStrength(it) },
                onDismiss = { viewModel.setEqualizerSheetOpen(false) }
            )
        }

        // Sleep Timer Sheet
        if (isSleepTimerSheetOpen) {
            CypherSleepTimerSheet(
                currentMode = sleepTimerMode,
                remainingSeconds = sleepTimerRemainingSeconds,
                themeColors = themeColors,
                onSelectMode = { viewModel.setSleepTimer(it) },
                onDismiss = { viewModel.setSleepTimerSheetOpen(false) }
            )
        }

        // Share Playlist QR & Code Dialog
        playlistToShare?.let { pl ->
            SharePlaylistDialog(
                playlist = pl,
                themeColors = themeColors,
                onDismiss = { viewModel.closeSharePlaylist() }
            )
        }

        // QR Scanner & Shortcode Entry Sheet
        if (isScannerOpen) {
            QrScannerSheet(
                themeColors = themeColors,
                onPayloadResolved = { viewModel.setPendingImportPayload(it) },
                onDismiss = { viewModel.closeScanner() }
            )
        }

        // Playlist Import Preview Confirmation Dialog
        pendingImportPayload?.let { payload ->
            PlaylistImportPreviewDialog(
                payload = payload,
                themeColors = themeColors,
                onConfirmImport = { viewModel.importSharedPlaylist(it) },
                onDismiss = { viewModel.setPendingImportPayload(null) }
            )
        }

        // Import from Spotify: paste a public playlist link and keep its songs here.
        if (spotifyImport.isOpen) {
            SpotifyImportSheet(
                state = spotifyImport,
                themeColors = themeColors,
                onLinkChange = { viewModel.setSpotifyLink(it) },
                onStart = { viewModel.startSpotifyImport() },
                onConfirm = { viewModel.confirmSpotifyImport() },
                onDismiss = { viewModel.closeSpotifyImport() }
            )
        }

        // Spotify account transfer: connect, choose playlists, see progress and failures.
        if (spotifyTransfer.isOpen) {
            SpotifySyncSheet(
                state = spotifyTransfer,
                themeColors = themeColors,
                onTogglePlaylist = { viewModel.toggleSpotifyPlaylist(it) },
                onSelectAll = { viewModel.selectAllSpotifyPlaylists(it) },
                onStartTransfer = { viewModel.startSpotifyTransfer() },
                onCancelTransfer = { viewModel.cancelSpotifyTransfer() },
                onCopyFailed = {
                    val text = spotifyTransfer.summary?.exportText().orEmpty()
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("Canciones no encontradas", text)
                    )
                    Toast.makeText(context, "Lista copiada", Toast.LENGTH_SHORT).show()
                },
                onShareFailed = {
                    val text = spotifyTransfer.summary?.exportText().orEmpty()
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(share, "Compartir canciones no encontradas"))
                },
                onOpenLinkImport = {
                    viewModel.closeSpotifySync()
                    viewModel.openSpotifyImport()
                },
                onDismiss = { viewModel.closeSpotifySync() }
            )
        }

        // Discreet GitHub release notice, floating above the dock.
        if (updateState.visible && !isSettingsOpen && !isWrappedOpen &&
            !currentUser?.displayName.isNullOrBlank()
        ) {
            UpdateBanner(
                state = updateState,
                accentColor = themeColors.accent,
                onUpdate = { updateViewModel.downloadAndInstall() },
                onDismiss = { updateViewModel.dismiss() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = bottomContentPadding)
            )
        }

        // Liquid Glass Shader Personalization Sheet
        if (isLiquidGlassSettingsOpen) {
            LiquidGlassSettingsSheet(
                config = themePrefs.liquidGlassConfig,
                themeColors = themeColors,
                onConfigChange = { viewModel.updateLiquidGlassConfig(it) },
                onDismiss = { viewModel.closeLiquidGlassSettings() }
            )
        }

        // First-launch onboarding: asks for the local name once, then never again.
        if (currentUser?.displayName.isNullOrBlank()) {
            UsernameDialog(
                currentName = "",
                themeColors = themeColors,
                isOnboarding = true,
                onConfirm = { viewModel.updateUsername(it) },
                onDismiss = {}
            )
        }

        // Cypher Wrapped: full-screen stories recap (notification deep link or Settings).
        if (isWrappedOpen) {
            WrappedScreen(
                stats = wrappedStats,
                year = wrappedStats.year,
                themePrefs = themePrefs,
                artworkColors = artworkPalette ?: AmbientArtworkColors(
                    dominant = CypherLavenderDark,
                    secondary = CypherDeepPlum,
                    accent = CypherLavender
                ),
                onClose = { viewModel.closeWrapped() }
            )
        }
    }
}
