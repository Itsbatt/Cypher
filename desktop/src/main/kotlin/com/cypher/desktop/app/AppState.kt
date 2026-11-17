package com.cypher.desktop.app

import com.cypher.desktop.core.DailyStat
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.LyricDoc
import com.cypher.desktop.core.Lyrics
import com.cypher.desktop.core.ImportPlan
import com.cypher.desktop.core.MatchQuality
import com.cypher.desktop.core.normalizeAccent
import com.cypher.desktop.core.MusicHit
import com.cypher.desktop.core.ParseIssue
import com.cypher.desktop.core.PlaylistPlan
import com.cypher.desktop.core.RemoteTrack
import com.cypher.desktop.core.SpotifyCsv
import com.cypher.desktop.core.SpotifyImportApply
import com.cypher.desktop.core.SpotifyMatcher
import com.cypher.desktop.core.SpotifyRow
import com.cypher.desktop.core.SpotifyZip
import com.cypher.desktop.core.YouTubeData
import com.cypher.desktop.core.YouTubeImport
import com.cypher.desktop.core.YouTubePlaylistSummary
import com.cypher.desktop.core.Playlist
import com.cypher.desktop.core.RecentSearch
import com.cypher.desktop.core.Scanner
import com.cypher.desktop.core.ShareCode.SharedTrack
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.Track
import com.cypher.desktop.core.videoKey
import com.cypher.desktop.core.YouTubeMusic
import com.cypher.desktop.player.AudioPlayer
import com.cypher.desktop.player.YouTubeEmbed
import com.cypher.desktop.player.YouTubeWindow
import com.cypher.desktop.importers.YouTubeOAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class Screen { HOME, LIBRARY, FAVORITES, PLAYLISTS, STATS, WRAPPED, DOWNLOADS, ONLINE, IMPORT, SETTINGS }

enum class LibrarySection { ALBUMS, ARTISTS, SONGS }

enum class SortMode(val label: String) {
    TITLE("Título"), ARTIST("Artista"), ALBUM("Álbum"), ADDED("Añadidas"), DURATION("Duración")
}

/**
 * A YouTube song playing inside the embedded browser. It replaces the download path: the
 * video streams itself and only the position comes back to the app.
 */
data class EmbedSession(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)

data class UiState(
    val data: LibraryData = LibraryData(),
    /** The rename dialog is open (first run uses the same one). */
    val profileSetup: Boolean = false,
    /** Result of the last backup import, shown in Ajustes until the next one. */
    val backupNotice: String? = null,
    val screen: Screen = Screen.HOME,
    val selectedPlaylistId: String? = null,
    val query: String = "",
    val scanning: Boolean = false,
    val scanFound: Int = 0,
    val notice: String? = null,
    val librarySection: LibrarySection = LibrarySection.SONGS,
    val albumFilter: String? = null,
    val artistFilter: String? = null,
    val sort: SortMode = SortMode.TITLE,
    val queue: List<String> = emptyList(),
    val queueIndex: Int = -1,
    val downloadCandidates: List<Track> = emptyList(),
    val scanningDownloads: Boolean = false,
    val onlineQuery: String = "",
    val onlineHits: List<MusicHit> = emptyList(),
    val onlineBusy: Boolean = false,
    val onlineStatus: String? = null,
    val ytDlpAvailable: Boolean = false,
    val onlineQueue: List<MusicHit> = emptyList(),
    val onlineIndex: Int = -1,
    val onlineListName: String? = null,
    val onlineLink: String = "",
    val onlineSaving: Boolean = false,
    val onlineSaved: Int = 0,
    val onlineTotal: Int = 0,
    /** Share string shown in the playlist QR dialog; null when it is closed. */
    val playlistShare: String? = null,
    val playlistShareName: String? = null,
    /** The in-app file picker, already configured by whichever screen opened it. */
    val filePicker: com.cypher.desktop.ui.FilePickerConfig? = null,
    /** The expanded library flyout next to the icon rail. Driven by hover, pinned by click. */
    val libraryPanelOpen: Boolean = false,
    /** The search dropdown under the top bar. */
    val searchOpen: Boolean = false,
    /** The info panel on the right; collapsible to give the centre more room. */
    val rightPanelOpen: Boolean = true,
    /** LETRAS / COLA — the two tabs of the info panel. */
    val rightPanelTab: String = "LETRAS",
    /** The immersive cover + lyrics screen, opened from the panel or the player. */
    val playerExpanded: Boolean = false,
    /** YOUTUBE / SPOTIFY in the import screen. */
    val importTab: String = "YOUTUBE",
    val importBusy: Boolean = false,
    val importStatus: String? = null,
    val spotifyPlan: ImportPlan? = null,
    val spotifySelected: Set<String> = emptySet(),
    val youtubePlan: YouTubeImport.Plan? = null,
    /** True when a playlist from the same link already exists locally. */
    val youtubeExisting: Boolean = false,
    /** Playlists of the signed-in Google account, from the official Data API. */
    val youtubeLibrary: List<YouTubePlaylistSummary> = emptyList(),
    val youtubeLibraryBusy: Boolean = false,
    /** Independent Google consent for YouTube read-only. */
    val youtubeConnected: Boolean = false,
    val youtubeConnecting: Boolean = false,
    val youtubeEmail: String? = null,
    /** Non-null while a YouTube song plays through the embedded player. */
    val embed: EmbedSession? = null,
    /** Set when Chromium could not start, so the YouTube screen can explain why. */
    val ytSetupIssue: com.cypher.desktop.player.YouTubeSetupIssue? = null,
    /** The song that could not open; Retry replays it once Chromium is available. */
    val ytPending: EmbedSession? = null
) {
    val currentTrackId: String? get() = queue.getOrNull(queueIndex)
}

/**
 * Single owner of the library document. Every mutation persists with an atomic write, so
 * the app keeps working offline and never depends on the account to remember anything.
 */
class AppState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(
        UiState(
            data = Storage.load().let { data ->
                // Repair a theme that arrived without alpha instead of rendering
                // invisible controls until the user picks an accent again.
                data.copy(preferences = data.preferences.copy(accent = normalizeAccent(data.preferences.accent)))
            }
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    // The audio engine starts on the first play, so the library and its tests never
    // depend on JavaFX being available.
    val player = AudioPlayer()
    val playback = player.state
    /** Real Windows media session; silently unavailable outside Windows. */
    private val smtc = com.cypher.desktop.player.SmtcBridge()
    private var remoteJob: Job? = null
    private var resolveJob: Job? = null
    /** Consent for the importer only: memory-only, never written to disk or logged. */
    private var youtubeTokens: YouTubeOAuth.Tokens? = null

    private var playStartedAt: Long = 0
    private var playStartedTrack: Track? = null

    init {
        player.onFinished = { playNext(auto = true) }
        // A real file taking over means the embed is no longer the source of sound.
        player.onLocalPlay = {
            if (_state.value.embed != null) _state.update { it.copy(embed = null) }
        }
        // Windows media keys and the system flyout drive the same queue as the app.
        smtc.onPlay = { player.resume() }
        smtc.onPause = { player.pause() }
        smtc.onNext = { playNext() }
        smtc.onPrevious = { playPrevious() }
        smtc.onSeek = { ms ->
            val total = playback.value.durationMs
            if (total > 0) player.seek((ms.toFloat() / total).coerceIn(0f, 1f))
        }
        scope.launch {
            playback.collect { state ->
                smtc.update(state.track, state.playing, state.stopped, state.error != null, state.positionMs, state.durationMs)
            }
        }
        _state.update { it.copy(ytDlpAvailable = YouTubeMusic.binary(it.data.preferences.ytDlpPath) != null) }
    }

    /** Called when the window closes so Windows drops the media session. */
    fun shutdown() {
        smtc.close()
        player.stop()
    }

    // --------------------------------------------------------------- plumbing

    private fun mutate(block: (LibraryData) -> LibraryData) {
        _state.update { ui ->
            val next = block(ui.data)
            Storage.save(next)
            ui.copy(data = next)
        }
    }

    fun snapshot(): LibraryData = _state.value.data

    /** Imports the phone's ZIP (or its JSON) and reports what was added. */
    fun importBackup(file: java.io.File) {
        runCatching { com.cypher.desktop.core.BackupImport.import(file, _state.value.data) }
            .onSuccess { (merged, summary) ->
                mutate { merged }
                _state.update {
                    it.copy(
                        backupNotice = if (summary.isEmpty) {
                            "«${summary.source}» no traía nada nuevo."
                        } else {
                            "«${summary.source}»: ${summary.playlists} playlists, ${summary.tracks} canciones, ${summary.favorites} favoritos."
                        }
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(backupNotice = "No se pudo importar: ${error.message}") }
            }
    }

    // ---------------------------------------------------------------- profile

    fun openProfileSetup() = _state.update { it.copy(profileSetup = true) }

    fun closeProfileSetup() = _state.update { it.copy(profileSetup = false) }

    /**
     * Stores the local display name. "Ahora no" is remembered too, so the welcome
     * screen appears once instead of on every start.
     */
    fun saveProfileName(name: String) {
        val trimmed = name.trim().take(24)
        mutate { data ->
            data.copy(
                profile = data.profile.copy(displayName = trimmed.ifBlank { "Cypher" }),
                profileUpdatedAt = System.currentTimeMillis(),
                profileSetupDone = true
            )
        }
        closeProfileSetup()
    }

    /** Test and demo hook: replaces the whole document and persists it. */
    fun replaceLibrary(block: (LibraryData) -> LibraryData) = mutate(block)

    fun select(screen: Screen) = _state.update {
        it.copy(screen = screen, query = "", selectedPlaylistId = null, albumFilter = null, artistFilter = null)
    }

    fun openPlaylist(id: String) = _state.update {
        it.copy(screen = Screen.PLAYLISTS, selectedPlaylistId = id)
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun openSearch() = _state.update { it.copy(searchOpen = true) }

    fun closeSearch() = _state.update { it.copy(searchOpen = false) }

    fun setLibraryPanel(open: Boolean) = _state.update { it.copy(libraryPanelOpen = open) }

    fun toggleRightPanel() = _state.update { it.copy(rightPanelOpen = !it.rightPanelOpen) }

    fun setRightPanelTab(tab: String) = _state.update { it.copy(rightPanelTab = tab, rightPanelOpen = true) }

    /** The full-window cover and lyrics screen. Closing it never stops playback. */
    fun expandPlayer() = _state.update { it.copy(playerExpanded = true) }

    fun collapsePlayer() {
        if (_state.value.embed != null) YouTubeEmbed.command("pause")
        _state.update { it.copy(playerExpanded = false) }
    }

    fun toggleExpandedPlayer() = _state.update { it.copy(playerExpanded = !it.playerExpanded) }

    // ------------------------------------------------------- recent searches

    /** Keeps the history short and free of duplicates; newest first. */
    private fun LibraryData.withRecent(entry: RecentSearch): LibraryData =
        copy(
            recentSearches = (listOf(entry) + recentSearches.filterNot { it.key == entry.key }).take(12)
        )

    fun recordQuerySearch(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        mutate { data ->
            data.withRecent(
                RecentSearch(
                    key = "q:${clean.lowercase()}",
                    query = clean,
                    title = clean,
                    subtitle = "Búsqueda",
                    kind = "QUERY",
                    at = System.currentTimeMillis()
                )
            )
        }
    }

    fun recordEntitySearch(kind: String, id: String) {
        val data = _state.value.data
        val (title, subtitle, artwork) = when (kind) {
            "PLAYLIST" -> {
                val playlist = data.playlist(id) ?: return
                Triple(playlist.name, "Lista · ${data.profile.displayName}", playlist.artwork)
            }
            "ALBUM" -> Triple(id, "Álbum", null)
            "ARTIST" -> Triple(id, "Artista", null)
            else -> {
                val view = viewOfData(data, id) ?: return
                Triple(view.first, view.second, view.third)
            }
        }
        mutate { it.withRecent(RecentSearch("$kind:$id", title = title, subtitle = subtitle, kind = kind, targetId = id, artwork = artwork, at = System.currentTimeMillis())) }
    }

    private fun viewOfData(data: LibraryData, id: String): Triple<String, String, String?>? {
        data.track(id)?.let { return Triple(it.title, "Canción · ${it.artist}", it.artwork) }
        return null
    }

    fun removeRecentSearch(key: String) = mutate { data ->
        data.copy(recentSearches = data.recentSearches.filterNot { it.key == key })
    }

    fun clearRecentSearches() = mutate { it.copy(recentSearches = emptyList()) }

    /** Runs a stored entry again: a query fills the field, an entity opens in its screen. */
    fun openRecentSearch(entry: com.cypher.desktop.core.RecentSearch) {
        when (entry.kind) {
            "QUERY" -> _state.update { it.copy(query = entry.query, searchOpen = true) }
            "PLAYLIST" -> {
                openPlaylist(entry.targetId ?: return)
                _state.update { it.copy(searchOpen = false) }
            }
            "ALBUM" -> {
                _state.update { it.copy(screen = Screen.LIBRARY, librarySection = LibrarySection.ALBUMS, albumFilter = entry.targetId) }
                _state.update { it.copy(searchOpen = false) }
            }
            "ARTIST" -> {
                _state.update { it.copy(screen = Screen.LIBRARY, librarySection = LibrarySection.ARTISTS, artistFilter = entry.targetId) }
                _state.update { it.copy(searchOpen = false) }
            }
            else -> {
                entry.targetId?.let { playQueue(listOf(it)) }
                _state.update { it.copy(searchOpen = false) }
            }
        }
    }

    // ------------------------------------------------------------ appearance

    fun setBackgroundMode(mode: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(backgroundMode = mode))
    }

    fun setLyricsSize(size: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(lyricsSize = size))
    }

    fun setLyricsAnimation(animation: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(lyricsAnimation = animation))
    }

    fun setLyricsShader(shader: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(lyricsShader = shader))
    }

    fun setLyricsLineSpacing(spacing: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(lyricsLineSpacing = spacing))
    }

    fun setLyricsAlign(align: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(lyricsAlign = align))
    }

    fun setLyricsColor(color: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(lyricsColor = color))
    }

    fun setLyricsInactiveOpacity(opacity: Float) = mutate { data ->
        data.copy(preferences = data.preferences.copy(lyricsInactiveOpacity = opacity.coerceIn(0.15f, 0.9f)))
    }

    fun setBackgroundImage(path: String?) = mutate { data ->
        data.copy(preferences = data.preferences.copy(backgroundImage = path, backgroundMode = if (path == null) "ORIGINAL" else "IMAGE"))
    }

    fun setBackgroundBlur(radius: Int) = mutate { data ->
        data.copy(preferences = data.preferences.copy(backgroundBlur = radius.coerceIn(0, 40)))
    }

    fun setBackgroundDim(value: Float) = mutate { data ->
        data.copy(preferences = data.preferences.copy(backgroundDim = value.coerceIn(0f, 0.85f)))
    }

    fun setBackgroundOpacity(value: Float) = mutate { data ->
        data.copy(preferences = data.preferences.copy(backgroundOpacity = value.coerceIn(0.15f, 1f)))
    }

    fun setBackgroundFit(fit: String) = mutate { data ->
        data.copy(preferences = data.preferences.copy(backgroundFit = fit))
    }
    fun setSort(sort: SortMode) = _state.update { it.copy(sort = sort) }
    fun setLibrarySection(section: LibrarySection) = _state.update {
        it.copy(librarySection = section, albumFilter = null, artistFilter = null)
    }
    fun openAlbum(name: String) = _state.update { it.copy(albumFilter = name) }
    fun openArtist(name: String) = _state.update { it.copy(artistFilter = name) }
    fun clearFilter() = _state.update { it.copy(albumFilter = null, artistFilter = null) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ---------------------------------------------------------------- library

    fun scanFolder(folder: File) {
        if (!folder.isDirectory) {
            _state.update { it.copy(notice = "Esa carpeta no existe.") }
            return
        }
        Storage.backup("prescan")
        _state.update { it.copy(scanning = true, scanFound = 0) }
        scope.launch {
            val found = Scanner.scan(folder) { count -> _state.update { it.copy(scanFound = count) } }
            mutate { data ->
                val byPath = data.tracks.associateBy { it.path }
                val merged = found.map { track ->
                    val previous = byPath[track.path]
                    if (previous == null) track
                    else track.copy(
                        addedAt = previous.addedAt,
                        updatedAt = maxOf(previous.updatedAt, track.updatedAt)
                    )
                }
                DataMutations.reconcileTracks(
                    data.copy(tracks = merged, libraryFolder = folder.absolutePath, lastScanAt = System.currentTimeMillis())
                )
            }
            _state.update { it.copy(scanning = false, notice = "${found.size} pistas en la biblioteca") }
        }
    }

    /** Adds/replaces tracks by id. Used by scans and by tests; never deletes others. */
    fun importTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        mutate { data ->
            val byId = data.tracks.associateBy { it.id }.toMutableMap()
            tracks.forEach { byId[it.id] = it }
            DataMutations.reconcileTracks(data.copy(tracks = byId.values.toList()))
        }
    }

    suspend fun rescan(): Int = withContext(Dispatchers.IO) {
        val folder = _state.value.data.libraryFolder?.let(::File) ?: return@withContext 0
        val found = Scanner.scan(folder)
        mutate { DataMutations.reconcileTracks(it.copy(tracks = found, lastScanAt = System.currentTimeMillis())) }
        found.size
    }

    // -------------------------------------------------------------- downloads

    /**
     * A downloads folder is just another place to read music from. Nothing is moved or
     * copied: files stay where the user already has them, and only their metadata is read.
     */
    fun scanDownloads(folder: File) {
        if (!folder.isDirectory) {
            _state.update { it.copy(notice = "Esa carpeta no existe.") }
            return
        }
        _state.update { it.copy(scanningDownloads = true) }
        scope.launch {
            val found = Scanner.scan(folder)
            val known = snapshot().tracks.map { it.path }.toSet()
            val candidates = found.filterNot { it.path in known }
            mutate { it.copy(downloadsFolder = folder.absolutePath) }
            _state.update {
                it.copy(
                    scanningDownloads = false,
                    downloadCandidates = candidates,
                    notice = if (candidates.isEmpty()) "No hay archivos nuevos en esa carpeta."
                    else "${candidates.size} archivos listos para añadir"
                )
            }
        }
    }

    fun importDownload(track: Track) {
        importTracks(listOf(track))
        _state.update { it.copy(downloadCandidates = it.downloadCandidates.filterNot { c -> c.id == track.id }) }
    }

    fun importAllDownloads() {
        val pending = _state.value.downloadCandidates
        if (pending.isEmpty()) return
        importTracks(pending)
        _state.update { it.copy(downloadCandidates = emptyList(), notice = "${pending.size} pistas añadidas a la biblioteca") }
    }

    // ------------------------------------------------- playlists (mobile parity)

    /**
     * A playlist cover is just an image the user picks; it is copied inside the profile so
     * the playlist keeps it even if the original file moves, like Android does with SAF.
     */
    /** Opens the app's own picker: no grey Swing dialog anywhere in the UI. */
    fun openFilePicker(config: com.cypher.desktop.ui.FilePickerConfig) {
        _state.update { it.copy(filePicker = config) }
    }

    fun closeFilePicker() {
        _state.update { it.copy(filePicker = null) }
    }

    fun setPlaylistCover(playlistId: String, file: java.io.File?) {
        val covers = Storage.dataDir.resolve("playlist-covers")
        runCatching { java.nio.file.Files.createDirectories(covers) }
        val previous = snapshot().playlist(playlistId)?.artwork
        val next: String? = if (file == null) null else {
            val extension = file.extension.ifBlank { "png" }.lowercase()
            val target = covers.resolve("$playlistId.$extension")
            runCatching {
                java.nio.file.Files.copy(
                    file.toPath(), target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                target.toString()
            }.getOrNull() ?: return
        }
        if (previous != null && previous != next && previous.startsWith(covers.toString())) {
            runCatching { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(previous)) }
        }
        mutate { data ->
            data.copy(
                playlists = data.playlists.map { playlist ->
                    if (playlist.id != playlistId) playlist
                    else playlist.copy(artwork = next, updatedAt = System.currentTimeMillis())
                }
            )
        }
        _state.update { it.copy(notice = if (next == null) "Portada quitada" else "Portada actualizada") }
    }

    /**
     * Downloads every song that only exists as a YouTube reference and swaps the reference
     * for the downloaded file, so the playlist keeps playing from disk afterwards.
     */
    fun downloadPlaylist(playlistId: String) {
        val current = _state.value
        if (current.onlineSaving) return
        val playlist = current.data.playlist(playlistId) ?: return
        val pending = playlist.trackIds.mapNotNull { current.data.remoteTrack(it) }
            .filter { it.source == "YOUTUBE" && it.available && it.videoKey().isNotBlank() }
        if (pending.isEmpty()) {
            _state.update { it.copy(notice = "Esa playlist ya está descargada.") }
            return
        }
        val folder = current.data.downloadsFolder?.let(::File)
            ?: File(System.getProperty("user.home"), "Music")
        Storage.backup("predownload")
        _state.update { it.copy(onlineSaving = true, onlineSaved = 0, onlineTotal = pending.size) }
        scope.launch {
            var done = 0
            for (remote in pending) {
                _state.update {
                    it.copy(onlineStatus = "Descargando «${remote.title}» (${done + 1}/${pending.size})…")
                }
                val result = runCatching {
                    YouTubeMusic.download(remote.videoKey(), folder, current.data.preferences.ytDlpPath)
                }
                val file = result.getOrNull()
                if (file == null) continue
                val scanned = runCatching { Scanner.read(file) }.getOrNull()
                val local = Track(
                    id = "yt:${remote.videoKey()}:${file.lastModified()}",
                    title = remote.title,
                    artist = remote.artist,
                    album = remote.album.ifBlank { "YouTube Music" },
                    durationMs = scanned?.durationMs?.takeIf { it > 0 } ?: remote.durationMs,
                    path = file.absolutePath,
                    artwork = remote.artworkUrl,
                    addedAt = file.lastModified(),
                    updatedAt = file.lastModified()
                )
                importTracks(listOf(local))
                mutate { data ->
                    data.copy(
                        playlists = data.playlists.map { entry ->
                            if (entry.id != playlistId) entry
                            else entry.copy(
                                trackIds = entry.trackIds.map { if (it == remote.id) local.id else it }.distinct(),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                    )
                }
                done++
                _state.update { it.copy(onlineSaved = done) }
            }
            _state.update {
                it.copy(
                    onlineSaving = false,
                    onlineTotal = 0,
                    onlineStatus = null,
                    notice = "«${playlist.name}»: $done de ${pending.size} descargadas"
                )
            }
        }
    }

    /** Deletes the audio behind this playlist and drops the local copies from the library. */
    fun removePlaylistDownloads(playlistId: String) {
        val current = _state.value
        val playlist = current.data.playlist(playlistId) ?: return
        val localIds = playlist.trackIds.filter { current.data.track(it) != null }.toSet()
        if (localIds.isEmpty()) {
            _state.update { it.copy(notice = "Esa playlist no tiene descargas.") }
            return
        }
        localIds.forEach { id ->
            current.data.track(id)?.path?.let { path ->
                runCatching { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(path)) }
            }
        }
        mutate { data ->
            data.copy(
                tracks = data.tracks.filterNot { it.id in localIds },
                playlists = data.playlists.map { entry ->
                    if (entry.id != playlistId) entry
                    else entry.copy(trackIds = entry.trackIds.filterNot { it in localIds })
                }
            )
        }
        _state.update { it.copy(notice = "Descargas de «${playlist.name}» eliminadas") }
    }

    /** Builds the cypher://p2p share string and opens the QR dialog. */
    fun openPlaylistShare(playlistId: String) {
        val data = snapshot()
        val playlist = data.playlist(playlistId) ?: return
        val tracks = playlist.trackIds.mapNotNull { id ->
            data.track(id)?.let { SharedTrack(it.title, it.artist, it.album, it.durationMs, null, "LOCAL") }
                ?: data.remoteTrack(id)?.let {
                    SharedTrack(
                        it.title, it.artist, it.album, it.durationMs,
                        it.videoId.ifBlank { null }, it.source
                    )
                }
        }
        val code = com.cypher.desktop.core.ShareCode.encode(
            com.cypher.desktop.core.ShareCode.Payload(playlist.name, playlist.description, tracks)
        )
        _state.update { it.copy(playlistShare = code, playlistShareName = playlist.name) }
    }

    fun closePlaylistShare() {
        _state.update { it.copy(playlistShare = null, playlistShareName = null) }
    }

    /** Adds a playlist from a pasted code; its songs join the library as references. */
    fun importSharedPlaylist(code: String) {
        val data = snapshot()
        val payload = runCatching { com.cypher.desktop.core.ShareCode.decode(code) }.getOrElse { error ->
            _state.update { it.copy(notice = "Código no válido: ${error.message}") }
            return
        }
        val playlist = com.cypher.desktop.core.ShareCode.toPlaylist(payload, data)
        val remote = payload.tracks.filter { it.videoId != null }.mapNotNull { shared ->
            val id = "yt_${shared.videoId}"
            if (data.remoteTrack(id) != null) null else RemoteTrack(
                id = id,
                videoId = shared.videoId.orEmpty(),
                title = shared.title,
                artist = shared.artist,
                album = shared.album,
                durationMs = shared.durationMs,
                source = "YOUTUBE"
            )
        }
        val ids = (playlist.trackIds + remote.map { it.id }).distinct()
        val missing = remote.filterNot { incoming -> data.remoteTracks.any { it.id == incoming.id } }
        mutate {
            it.copy(
                remoteTracks = it.remoteTracks + missing,
                playlists = it.playlists + playlist.copy(trackIds = ids)
            )
        }
        _state.update { it.copy(notice = "«${playlist.name}» añadida: ${ids.size} canciones") }
    }

    // ------------------------------------------------------- youtube music

    fun setOnlineQuery(query: String) = _state.update { it.copy(onlineQuery = query) }

    /** Drops "(Official Video)", "[Lyrics]" and similar tags from a pasted title. */
    private fun cleanSearchQuery(query: String): String {
        val builder = StringBuilder()
        var depth = 0
        for (character in query) {
            when {
                character == '(' || character == '[' -> depth++
                (character == ')' || character == ']') && depth > 0 -> depth--
                depth == 0 -> builder.append(character)
            }
        }
        val junk = setOf("official", "video", "audio", "lyrics", "letra", "hd", "4k", "remaster", "remastered")
        return builder.toString()
            .replace(',', ' ')
            .replace('-', ' ')
            .split(' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in junk }
            .joinToString(" ")
    }

    fun searchOnline() {
        // Collapse the whitespace pasted from YouTube titles: ytsearch treats it as noise.
        val query = _state.value.onlineQuery.trim().split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (query.isEmpty()) return
        _state.update { it.copy(onlineBusy = true, onlineQuery = query, onlineStatus = "Buscando «$query»…") }
        scope.launch {
            val configured = _state.value.data.preferences.ytDlpPath
            val first = runCatching { YouTubeMusic.search(query, limit = 20, configured = configured) }
            var hits = first.getOrDefault(emptyList())
            var failure = first.exceptionOrNull()?.message

            if (hits.isEmpty() && first.isSuccess) {
                // YouTube Music finds the song, not the upload, once the tags are gone.
                val cleaned = cleanSearchQuery(query)
                if (cleaned.isNotEmpty() && cleaned != query) {
                    val retry = runCatching { YouTubeMusic.search(cleaned, limit = 20, configured = configured) }
                    hits = retry.getOrDefault(emptyList())
                    failure = retry.exceptionOrNull()?.message
                }
            }

            // ytsearch lists the same upload under several ids.
            val unique = hits.distinctBy { it.videoId }
            _state.update { current ->
                current.copy(
                    onlineBusy = false,
                    // A failed search must not wipe the list the user is looking at.
                    onlineHits = if (unique.isEmpty() && failure != null) current.onlineHits else unique,
                    onlineListName = null,
                    onlineStatus = when {
                        failure != null -> "No se pudo buscar: $failure"
                        unique.isEmpty() -> "Sin resultados para «$query»."
                        else -> "${unique.size} resultados para «$query»"
                    }
                )
            }
        }
    }

    fun setOnlineLink(link: String) = _state.update { it.copy(onlineLink = link) }

    /** Loads a YouTube Music playlist/album URL into the list, without resolving streams. */
    fun loadOnlinePlaylist() {
        val link = _state.value.onlineLink.trim()
        if (link.isEmpty()) return
        _state.update { it.copy(onlineBusy = true, onlineStatus = "Leyendo la playlist…") }
        scope.launch {
            val result = runCatching { YouTubeMusic.playlist(link, _state.value.data.preferences.ytDlpPath) }
            val list = result.getOrNull()
            if (list == null) {
                _state.update {
                    it.copy(
                        onlineBusy = false,
                        onlineStatus = "No se pudo abrir la playlist: ${result.exceptionOrNull()?.message ?: "error desconocido"}"
                    )
                }
                return@launch
            }
            val plan = YouTubeImport.plan(list)
            val exists = _state.value.data.playlists.any {
                it.origin == "YOUTUBE" && it.sourceUrl == plan.sourceKey
            }
            _state.update {
                it.copy(
                    onlineBusy = false,
                    onlineHits = list.hits,
                    onlineListName = list.title,
                    youtubePlan = plan,
                    youtubeExisting = exists,
                    onlineStatus = if (plan.unavailable > 0) {
                        "${list.hits.size} canciones en «${list.title}» · ${plan.unavailable} no disponibles"
                    } else {
                        "${list.hits.size} canciones en «${list.title}»"
                    }
                )
            }
        }
    }

    /** Plays one result and keeps the visible list as the streaming queue. */
    fun playOnline(hit: MusicHit) {
        val visible = _state.value.onlineHits
        val index = visible.indexOfFirst { it.videoId == hit.videoId }.coerceAtLeast(0)
        _state.update {
            it.copy(onlineQueue = visible.ifEmpty { listOf(hit) }, onlineIndex = index)
        }
        startOnline(index)
    }

    /** Starts the whole visible list: search results or a loaded playlist. */
    fun playOnlineAll() {
        val hits = _state.value.onlineHits
        if (hits.isEmpty()) return
        _state.update { it.copy(onlineQueue = hits, onlineIndex = 0) }
        startOnline(0)
    }

    /**
     * Downloads the audio and plays it with the local engine. It is the fallback for the
     * songs YouTube will not embed, and it is exactly what the app did before the iframe.
     */
    private fun playDownloaded(videoId: String, title: String, artist: String, durationMs: Long) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    YouTubeMusic.cachedAudio(videoId, snapshot().preferences.ytDlpPath) { step ->
                        _state.update { it.copy(notice = "$step «$title»") }
                    }
                }
            }
            val audio = result.getOrNull()
            if (audio == null) {
                _state.update {
                    it.copy(notice = "No se pudo reproducir «$title»: ${result.exceptionOrNull()?.message ?: "error"}")
                }
                return@launch
            }
            val track = Track(
                id = "yt:$videoId",
                title = title,
                artist = artist,
                album = "YouTube Music",
                durationMs = durationMs,
                path = audio.absolutePath
            )
            player.play(track)
            _state.update { it.copy(notice = null) }
        }
    }

    private fun startOnline(index: Int) {
        val hit = _state.value.onlineQueue.getOrNull(index) ?: return
        _state.update {
            it.copy(
                queue = emptyList(),
                queueIndex = -1,
                onlineBusy = false,
                onlineIndex = index,
                onlineStatus = "Sonando: ${hit.title}"
            )
        }
        // No download: the iframe streams it, as on the phone.
        playEmbed(hit.videoId, hit.title, hit.artist, hit.durationMs)
    }

    private fun advanceOnline(auto: Boolean) {
        val current = _state.value
        if (current.onlineQueue.isEmpty()) return
        if (current.data.preferences.repeat == "ONE" && auto) {
            startOnline(current.onlineIndex.coerceAtLeast(0))
            return
        }
        var next = current.onlineIndex + 1
        if (next > current.onlineQueue.lastIndex) {
            if (current.data.preferences.repeat == "ALL" || !auto) next = 0
            else {
                player.stop()
                return
            }
        }
        startOnline(next)
    }

    /** Downloads the audio into the downloads folder (or ~/Music) and adds it to the library. */
    fun downloadOnline(hit: MusicHit) {
        val folder = _state.value.data.downloadsFolder?.let(::File)
            ?: File(System.getProperty("user.home"), "Music")
        _state.update { it.copy(onlineBusy = true, onlineStatus = "Descargando «${hit.title}»…") }
        Storage.backup("predownload")
        scope.launch {
            val result = runCatching { YouTubeMusic.download(hit.videoId, folder, _state.value.data.preferences.ytDlpPath) }
            val file = result.getOrNull()
            if (file == null) {
                _state.update {
                    it.copy(
                        onlineBusy = false,
                        onlineStatus = "No se pudo descargar: ${result.exceptionOrNull()?.message ?: "error desconocido"}"
                    )
                }
                return@launch
            }
            val scanned = runCatching { Scanner.read(file) }.getOrNull()
            val fallback = Track(
                id = "yt:${hit.videoId}:${file.lastModified()}",
                title = hit.title,
                artist = hit.artist,
                album = "YouTube Music",
                durationMs = hit.durationMs,
                path = file.absolutePath,
                addedAt = file.lastModified(),
                updatedAt = file.lastModified()
            )
            // yt-dlp's tags can come empty ("Artista desconocido"); the search result is
            // the better source for the display name, while the file keeps its real tags.
            val track = (scanned ?: fallback).copy(
                title = hit.title.ifBlank { scanned?.title.orEmpty() },
                artist = hit.artist.ifBlank { scanned?.artist ?: fallback.artist },
                durationMs = scanned?.durationMs?.takeIf { it > 0 } ?: hit.durationMs
            )
            importTracks(listOf(track))
            mutate { it.copy(downloadsFolder = folder.absolutePath) }
            _state.update {
                it.copy(
                    onlineBusy = false,
                    onlineStatus = "Descargada y añadida: ${file.name}",
                    notice = "${track.title} añadida desde YouTube Music"
                )
            }
        }
    }

    /**
     * Saves the loaded YouTube Music list as a local playlist: each song is downloaded,
     * imported and added in order. Stoppable, one download at a time.
     */
    fun saveOnlinePlaylist(limit: Int = Int.MAX_VALUE, folder: File? = null) {
        val current = _state.value
        if (current.onlineHits.isEmpty() || current.onlineSaving) return
        val hits = current.onlineHits.take(limit)
        val title = current.onlineListName?.takeIf { it.isNotBlank() } ?: "Playlist de YouTube Music"
        val target = folder ?: current.data.downloadsFolder?.let(::File)
            ?: File(System.getProperty("user.home"), "Music")
        val playlistId = createPlaylist(title, "Importada de YouTube Music") ?: return
        Storage.backup("predownload")
        _state.update {
            it.copy(
                onlineSaving = true,
                onlineSaved = 0,
                onlineTotal = hits.size,
                onlineStatus = "Guardando «$title» (0/${hits.size})…"
            )
        }
        scope.launch {
            var done = 0
            var skipped = 0
            for (hit in hits) {
                if (!_state.value.onlineSaving) break
                _state.update { it.copy(onlineStatus = "Descargando ${done + 1}/${hits.size}: ${hit.title}") }
                val file = runCatching {
                    YouTubeMusic.download(hit.videoId, target, _state.value.data.preferences.ytDlpPath)
                }.getOrNull()
                if (file == null) {
                    skipped++
                    continue
                }
                val scanned = runCatching { Scanner.read(file) }.getOrNull()
                val base = scanned ?: Track(
                    id = "yt:${hit.videoId}:${file.lastModified()}",
                    title = hit.title,
                    artist = hit.artist,
                    album = title,
                    durationMs = hit.durationMs,
                    path = file.absolutePath,
                    addedAt = file.lastModified(),
                    updatedAt = file.lastModified()
                )
                val track = base.copy(
                    title = hit.title.ifBlank { base.title },
                    artist = hit.artist.ifBlank { base.artist },
                    album = title,
                    artwork = hit.thumbnail ?: base.artwork,
                    durationMs = base.durationMs.takeIf { it > 0 } ?: hit.durationMs
                )
                importTracks(listOf(track))
                addToPlaylist(playlistId, track.id)
                if (done == 0 && hit.thumbnail != null) setPlaylistArtwork(playlistId, hit.thumbnail)
                done++
                _state.update { it.copy(onlineSaved = done) }
            }
            if (done == 0) {
                // An empty playlist would be noise: nothing could be downloaded.
                deletePlaylist(playlistId)
                _state.update {
                    it.copy(onlineSaving = false, onlineStatus = "No se pudo descargar ninguna canción de «$title»")
                }
                return@launch
            }
            mutate { it.copy(downloadsFolder = target.absolutePath) }
            _state.update {
                it.copy(
                    onlineSaving = false,
                    onlineStatus = when {
                        done == hits.size -> "Playlist guardada: $done canciones en «$title»"
                        skipped > 0 -> "Playlist guardada con $done de ${hits.size}: $skipped no se pudieron descargar"
                        else -> "Guardado detenido: $done de ${hits.size} canciones"
                    }
                )
            }
        }
    }

    fun stopOnlineSave() {
        _state.update { it.copy(onlineSaving = false, onlineStatus = "Deteniendo la descarga…") }
    }

    private fun setPlaylistArtwork(playlistId: String, artwork: String) {
        mutate { data ->
            data.copy(playlists = data.playlists.map { if (it.id == playlistId) it.copy(artwork = artwork) else it })
        }
    }

    fun installYtDlp() {
        _state.update { it.copy(onlineBusy = true, onlineStatus = "Descargando yt-dlp…") }
        scope.launch {
            val result = runCatching { YouTubeMusic.install() }
            val file = result.getOrNull()
            if (file == null) {
                _state.update {
                    it.copy(
                        onlineBusy = false,
                        onlineStatus = "No se pudo instalar yt-dlp: ${result.exceptionOrNull()?.message ?: "error desconocido"}"
                    )
                }
                return@launch
            }
            mutate { it.copy(preferences = it.preferences.copy(ytDlpPath = file.absolutePath)) }
            _state.update {
                it.copy(onlineBusy = false, ytDlpAvailable = true, onlineStatus = "yt-dlp instalado y listo.")
            }
        }
    }

    fun setYtDlpPath(path: String?) {
        val file = path?.let(::File)
        if (file == null || !file.isFile) {
            _state.update { it.copy(onlineStatus = "Ese archivo no existe.") }
            return
        }
        mutate { it.copy(preferences = it.preferences.copy(ytDlpPath = file.absolutePath)) }
        _state.update { it.copy(ytDlpAvailable = true, onlineStatus = "yt-dlp listo: ${file.name}") }
    }

    // --------------------------------------------------------------- playback

    /** Starts a queue. Repeat/shuffle are honoured by [playNext]. */
    fun playQueue(ids: List<String>, startIndex: Int = 0) {
        val clean = ids.distinct()
        if (clean.isEmpty()) return
        val index = startIndex.coerceIn(0, clean.lastIndex)
        _state.update { it.copy(queue = clean, queueIndex = index, onlineQueue = emptyList(), onlineIndex = -1) }
        startTrack(clean[index])
    }

    fun play(track: Track) = playQueue(listOf(track.id))

    fun playPlaylist(playlistId: String, shuffled: Boolean = false) {
        val data = snapshot()
        val playlist = data.playlist(playlistId) ?: return
        // Local files and imported YouTube references play from the same queue; only
        // true gaps (an unavailable video) are skipped.
        val ids = playlist.trackIds.filter { id ->
            data.track(id) != null ||
                data.remoteTrack(id)?.let { it.source == "YOUTUBE" && it.available } == true
        }
        if (ids.isEmpty()) {
            _state.update { it.copy(notice = "Esa playlist no tiene canciones que se puedan reproducir.") }
            return
        }
        playQueue(if (shuffled) ids.shuffled() else ids)
    }

    /** Last resort: the local file is gone, so look the same song up and stream it. */
    private fun fallbackToYouTube(track: Track) {
        _state.update {
            it.copy(
                onlineBusy = true,
                notice = "El archivo de «${track.title}» no está en este equipo. Buscando la canción en YouTube Music…"
            )
        }
        scope.launch {
            val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
            val result = runCatching { YouTubeMusic.search(query, limit = 5, configured = _state.value.data.preferences.ytDlpPath) }
            val hit = result.getOrDefault(emptyList()).firstOrNull()
            if (hit == null) {
                _state.update {
                    it.copy(
                        onlineBusy = false,
                        notice = "«${track.title}» no está en disco y YouTube Music no devolvió nada."
                    )
                }
                return@launch
            }
            _state.update { it.copy(onlineQueue = listOf(hit), onlineIndex = 0) }
            startOnline(0)
        }
    }

    private fun startTrack(trackId: String) {
        flushListening()
        val data = snapshot()
        val track = data.track(trackId)
        if (track == null) {
            val remote = data.remoteTrack(trackId)
            if (remote != null && remote.source == "YOUTUBE") {
                startRemoteYouTube(remote)
                return
            }
            if (remote != null) {
                _state.update {
                    it.copy(
                        notice = "«${remote.title}» vino de Spotify y aún no tiene una versión reproducible. " +
                            "Búscala en YouTube Music o elige otra coincidencia."
                    )
                }
                return
            }
            _state.update { it.copy(notice = "Esa canción ya no está en la biblioteca.") }
            return
        }
        // A library entry can survive its file (moved, deleted, another PC). Instead of a
        // dead click, the same song is streamed from YouTube Music.
        if (!track.path.startsWith("http") && !java.io.File(track.path).isFile) {
            fallbackToYouTube(track)
            return
        }
        playStartedAt = System.currentTimeMillis()
        playStartedTrack = track
        player.play(track)
        mutate { data ->
            data.copy(recent = (listOf(track.id) + data.recent.filterNot { it == track.id }).take(24))
        }
        registerPlay(track)
    }

    /** Adds the elapsed listening time of the current track to today's rollup. */
    fun flushListening() {
        val track = playStartedTrack ?: return
        val elapsed = (System.currentTimeMillis() - playStartedAt).coerceAtLeast(0)
        playStartedTrack = null
        if (elapsed < 2_000) return
        val day = java.time.LocalDate.now().toString()
        val now = System.currentTimeMillis()
        mutate { data ->
            val existing = data.stats.firstOrNull { it.day == day && it.trackId == track.id }
            val stat = if (existing == null) {
                DailyStat(day, track.id, elapsed, 0, track.title, track.artist, track.album, track.artwork, now)
            } else {
                existing.copy(
                    listenDurationMs = existing.listenDurationMs + elapsed,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    lastListenedAt = now
                )
            }
            val stats = data.stats.filterNot { it.day == day && it.trackId == track.id } + stat
            val next = data.copy(stats = stats)
            next
        }
    }

    /**
     * A favorite from the phone can carry a stream URL. Desktop plays it if it is still
     * valid; an expired URL surfaces as a playback error instead of a silent click.
     */
    fun playNext(auto: Boolean = false) {
        val current = _state.value
        if (current.queue.isEmpty()) {
            // A YouTube Music queue streams item by item; the library queue stays untouched.
            if (current.onlineQueue.isNotEmpty() && current.onlineIndex >= 0) advanceOnline(auto)
            return
        }
        val repeat = current.data.preferences.repeat
        if (repeat == "ONE" && auto) {
            startTrack(current.queue.getOrElse(current.queueIndex) { return })
            return
        }
        var nextIndex = current.queueIndex + 1
        if (nextIndex > current.queue.lastIndex) {
            if (repeat == "ALL" || !auto) nextIndex = 0 else {
                player.stop()
                flushListening()
                return
            }
        }
        _state.update { it.copy(queueIndex = nextIndex) }
        startTrack(current.queue[nextIndex])
    }

    fun playPrevious() {
        val current = _state.value
        if (current.queue.isEmpty()) {
            if (current.onlineQueue.isNotEmpty() && current.onlineIndex >= 0) {
                startOnline((current.onlineIndex - 1).coerceAtLeast(0))
            }
            return
        }
        flushListening()
        val index = if (current.queueIndex <= 0) current.queue.lastIndex else current.queueIndex - 1
        _state.update { it.copy(queueIndex = index) }
        startTrack(current.queue[index])
    }

        fun togglePlayback() {
        val embed = _state.value.embed
        if (embed != null) {
            YouTubeEmbed.command("toggle")
            player.external(YouTubeEmbed.positionMs, embed.durationMs, !YouTubeEmbed.playing)
            return
        }
        if (_state.value.queue.isEmpty()) {
            // A stream from YouTube Music has no queue: it toggles like any other track.
            if (player.state.value.track != null) player.toggle()
            else snapshot().tracks.firstOrNull()?.let { playQueue(listOf(it.id)) }
            return
        }

        player.toggle()
    }

    fun seek(fraction: Float) {
        val embed = _state.value.embed
        if (embed != null) {
            val total = YouTubeEmbed.durationMs.takeIf { it > 0 } ?: embed.durationMs
            YouTubeEmbed.command("seek:${(total * fraction.coerceIn(0f, 1f)).toLong()}")
            return
        }
        player.seek(fraction)
    }

    /**
     * Plays a YouTube song in the embedded browser instead of downloading it, exactly like
     * the phone does with its iframe. The app keeps the metadata, and the position arrives
     * from the video so the lyrics stay in time.
     */
    private fun playEmbed(videoId: String, title: String, artist: String, durationMs: Long) {
        val session = EmbedSession(videoId, title, artist, durationMs)
        player.stop()
        _state.update { it.copy(embed = session, playerExpanded = true, notice = "Abriendo «$title» en YouTube…") }
        scope.launch {
            // Warms Chromium up; the panel itself attaches the browser on the AWT thread.
            val started = runCatching { withContext(Dispatchers.IO) { YouTubeEmbed.prepare() } }
                .getOrDefault(false)
            if (!started) {
                _state.update {
                    it.copy(
                        embed = null,
                        notice = null,
                        // A raw exception in a snackbar told nobody anything, so the YouTube
                        // screen keeps the explanation and offers a retry.
                        ytSetupIssue = YouTubeEmbed.diagnostic
                            ?: com.cypher.desktop.player.YouTubeSetupIssue(
                                headline = "No se pudo abrir YouTube",
                                detail = "Chromium no arrancó en este equipo: ${YouTubeEmbed.error ?: "sin detalles"}.",
                                hint = "Pulsa Reintentar para volver a intentarlo."
                            ),
                        ytPending = session
                    )
                }
                return@launch
            }
            val track = Track(
                id = "yt:$videoId",
                title = title,
                artist = artist,
                album = "YouTube Music",
                durationMs = durationMs,
                path = "embed:$videoId"
            )
            player.preview(track, 0, durationMs)
            // Chromium paints on its own surface, so the video gets its own window and the
            // app keeps the metadata, the lyrics and the progress.
            YouTubeWindow.open(videoId, title) {
                _state.update { it.copy(embed = null) }
            }
            _state.update { it.copy(notice = null, ytSetupIssue = null, ytPending = null) }
            // Mirror the video onto the app state until another song takes over.
            while (_state.value.embed?.videoId == videoId) {
                delay(350)
                if (YouTubeEmbed.refused) {
                    // Some songs cannot be embedded (private, age-locked, embeds disabled).
                    // The window closes and the old download path takes over.
                    YouTubeWindow.close()
                    _state.update {
                        it.copy(embed = null, notice = "«$title» no se puede ver aquí: se reproduce desde el archivo.")
                    }
                    playDownloaded(videoId, title, artist, durationMs)
                    return@launch
                }
                val reported = YouTubeEmbed.durationMs
                player.external(
                    YouTubeEmbed.positionMs,
                    if (reported > 0) reported else durationMs,
                    YouTubeEmbed.playing
                )
            }
        }
    }

    /** Replays the song that failed to open, after the diagnostic card asked for it. */
    fun retryYouTube() {
        val pending = _state.value.ytPending ?: return
        _state.update { it.copy(ytSetupIssue = null) }
        playEmbed(pending.videoId, pending.title, pending.artist, pending.durationMs)
    }

    fun dismissYouTubeIssue() {
        _state.update { it.copy(ytSetupIssue = null, ytPending = null) }
    }

    fun setVolume(volume: Float) {
        player.setVolume(volume)
        mutate { it.copy(preferences = it.preferences.copy(volume = volume)) }
    }

    fun toggleShuffle() {
        val enabled = !_state.value.data.preferences.shuffle
        mutate { it.copy(preferences = it.preferences.copy(shuffle = enabled)) }
        val queue = _state.value.queue
        if (enabled && queue.isNotEmpty()) {
            val current = queue.getOrNull(_state.value.queueIndex)
            val shuffled = queue.shuffled().let { list ->
                if (current == null) list else listOf(current) + list.filterNot { it == current }
            }
            _state.update { it.copy(queue = shuffled, queueIndex = 0) }
        }
    }

    fun cycleRepeat() {
        val order = listOf("OFF", "ALL", "ONE")
        val next = order[(order.indexOf(_state.value.data.preferences.repeat).coerceAtLeast(0) + 1) % order.size]
        mutate { it.copy(preferences = it.preferences.copy(repeat = next)) }
    }

    private fun registerPlay(track: Track) {
        val now = System.currentTimeMillis()
        val day = java.time.LocalDate.now().toString()
        mutate { data ->
            val existing = data.stats.firstOrNull { it.day == day && it.trackId == track.id }
            val stat = if (existing == null) {
                DailyStat(day, track.id, 0, 1, track.title, track.artist, track.album, track.artwork, now)
            } else {
                existing.copy(playCount = existing.playCount + 1, lastListenedAt = now, title = track.title, artist = track.artist)
            }
            val stats = data.stats.filterNot { it.day == day && it.trackId == track.id } + stat
            val next = data.copy(stats = stats)
            next
        }
    }

    // -------------------------------------------------------------- favorites

    fun toggleFavorite(trackId: String) {
        val now = System.currentTimeMillis()
        mutate { data ->
            val isFav = data.favorites.contains(trackId)
            val favorites = if (isFav) data.favorites - trackId else listOf(trackId) + data.favorites
            data.copy(favorites = favorites)
        }
    }

    // ---------------------------------------------------------------- imports

    fun notify(message: String) = _state.update { it.copy(notice = message) }

    fun setImportTab(tab: String) = _state.update { it.copy(importTab = tab, importStatus = null) }

    fun toggleSpotifyPlaylist(sourceKey: String) = _state.update { ui ->
        val selected = if (sourceKey in ui.spotifySelected) ui.spotifySelected - sourceKey
        else ui.spotifySelected + sourceKey
        ui.copy(spotifySelected = selected)
    }

    fun cancelImport() = _state.update {
        it.copy(importBusy = false, importStatus = null, spotifyPlan = null, spotifySelected = emptySet())
    }

    /**
     * Reads CSV files and/or ZIPs chosen by the user and builds a preview. Nothing is
     * saved here: the library only changes when the user confirms.
     */
    fun analyzeSpotifyFiles(files: List<File>) {
        if (files.isEmpty()) return
        _state.update { it.copy(importBusy = true, importStatus = "Leyendo ${files.size} archivo(s)…", spotifyPlan = null) }
        scope.launch {
            val outcome = runCatching {
                val issues = mutableListOf<ParseIssue>()
                val rows = mutableListOf<SpotifyRow>()
                files.forEach { file ->
                    val bytes = runCatching { file.readBytes() }.getOrElse { error ->
                        issues += ParseIssue(file.name, "No se pudo leer: ${error.message ?: "error desconocido"}")
                        return@forEach
                    }
                    if (file.name.lowercase().endsWith(".zip")) {
                        val zip = SpotifyZip.inspect(bytes)
                        issues += zip.issues
                        zip.csvs.forEach { csv ->
                            val parsed = SpotifyCsv.parseFile(csv.name, csv.bytes)
                            parsed.error?.let { issues += ParseIssue(csv.name, it) }
                            rows += parsed.rows
                        }
                    } else {
                        val parsed = SpotifyCsv.parseFile(file.name, bytes)
                        parsed.error?.let { issues += ParseIssue(file.name, it) }
                        rows += parsed.rows
                    }
                }
                SpotifyMatcher.plan(rows, snapshot(), issues, files.map { it.name }) to issues
            }
            val plan = outcome.getOrNull()?.first
            if (plan == null) {
                _state.update {
                    it.copy(
                        importBusy = false,
                        importStatus = "No se pudo analizar: ${outcome.exceptionOrNull()?.message ?: "archivo no válido"}"
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    importBusy = false,
                    spotifyPlan = plan,
                    spotifySelected = plan.playlists.map { playlist -> playlist.sourceKey }.toSet(),
                    importStatus = if (plan.songs == 0) {
                        "No se encontraron canciones. " + (plan.issues.firstOrNull()?.message ?: "Revisa las columnas del CSV.")
                    } else {
                        "${plan.playlists.size} lista(s) · ${plan.songs} canciones · ${plan.recognized} reconocidas"
                    }
                )
            }
        }
    }

    /** Loads the signed-in account's own playlists (YouTube Data API, read-only). */
    fun connectYouTube() {
        if (_state.value.youtubeConnecting) return
        _state.update { it.copy(youtubeConnecting = true, importStatus = null) }
        scope.launch {
            val result = runCatching {
                YouTubeOAuth().signIn()
            }
            result.onSuccess { session ->
                youtubeTokens = session
                _state.update {
                    it.copy(
                        youtubeConnecting = false,
                        youtubeConnected = true,
                        youtubeEmail = session.email,
                        importStatus = "Cuenta de Google conectada. Pulsa «Cargar mis playlists»."
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        youtubeConnecting = false,
                        importStatus = "No se pudo conectar con Google: ${error.message ?: "error desconocido"}"
                    )
                }
            }
        }
    }

    fun disconnectYouTube() {
        youtubeTokens = null
        _state.update {
            it.copy(youtubeConnected = false, youtubeEmail = null, youtubeLibrary = emptyList(), youtubePlan = null)
        }
    }

    fun loadYouTubeLibrary() {
        if (_state.value.importBusy) return
        val token = youtubeTokens?.accessToken.orEmpty()
        if (token.isBlank()) {
            notify("Conecta tu cuenta de Google para ver tus playlists de YouTube Music.")
            return
        }
        _state.update { it.copy(youtubeLibraryBusy = true, importStatus = "Cargando tus playlists de YouTube…") }
        scope.launch {
            val result = runCatching { YouTubeData.allPlaylists(token) }
            val lists = result.getOrNull()
            if (lists == null) {
                _state.update {
                    it.copy(
                        youtubeLibraryBusy = false,
                        importStatus = "No se pudieron cargar tus playlists: ${result.exceptionOrNull()?.message ?: "error desconocido"}"
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    youtubeLibraryBusy = false,
                    youtubeLibrary = lists,
                    importStatus = if (lists.isEmpty())
                        "La cuenta no tiene playlists visibles. Crea una en YouTube Music o revisa el permiso concedido."
                    else "${lists.size} playlists en tu cuenta de YouTube Music"
                )
            }
        }
    }

    /** Reads one account playlist and shows it in the import preview. */
    fun previewYouTubeAccountPlaylist(summary: YouTubePlaylistSummary) {
        if (_state.value.importBusy) return
        val token = youtubeTokens?.accessToken.orEmpty()
        if (token.isBlank()) {
            notify("Conecta tu cuenta de Google para importar esta playlist.")
            return
        }
        _state.update {
            it.copy(importBusy = true, importStatus = "Leyendo «${summary.title}»…")
        }
        scope.launch {
            val result = runCatching { YouTubeData.allItems(token, summary.id) }
            val hits = result.getOrNull()
            if (hits == null) {
                _state.update {
                    it.copy(
                        importBusy = false,
                        importStatus = "No se pudo leer «${summary.title}»: ${result.exceptionOrNull()?.message ?: "error desconocido"}"
                    )
                }
                return@launch
            }
            if (hits.isEmpty()) {
                _state.update {
                    it.copy(importBusy = false, importStatus = "«${summary.title}» no tiene canciones visibles.")
                }
                return@launch
            }
            val list = YouTubeData.toMusicList(summary, hits)
            val plan = YouTubeImport.plan(list)
            val exists = _state.value.data.playlists.any {
                it.origin == "YOUTUBE" && it.sourceUrl == plan.sourceKey
            }
            _state.update {
                it.copy(
                    importBusy = false,
                    importTab = "YOUTUBE",
                    youtubePlan = plan,
                    youtubeExisting = exists,
                    onlineHits = hits,
                    onlineListName = summary.title,
                    importStatus = "${hits.size} canciones en «${summary.title}» · listo para importar"
                )
            }
        }
    }

    /** Stops a long YouTube matching pass without discarding the preview. */
    fun stopImportResolve() = _state.update { it.copy(importBusy = false) }

    /**
     * Resolves the rows that were not in the library by searching YouTube Music for
     * "title artist". Only a plausible result is kept: a live/remix mismatch is never
     * accepted, and everything stays in the preview until the user confirms.
     */
    fun resolveSpotifyWithYouTube() {
        val ui = _state.value
        val plan = ui.spotifyPlan ?: return
        if (ui.importBusy) return
        val pending = plan.pendingYoutube()
        if (pending.isEmpty()) {
            notify("No quedan canciones sin coincidencia.")
            return
        }
        if (YouTubeMusic.binary(ui.data.preferences.ytDlpPath) == null) {
            notify("Falta yt-dlp: instálalo desde YouTube Music para buscar coincidencias.")
            return
        }
        _state.update {
            it.copy(importBusy = true, importStatus = "Buscando 0/${pending.size} en YouTube Music…")
        }
        resolveJob = scope.launch {
            var current = plan
            var done = 0
            var found = 0
            val usedVideos = mutableMapOf<String, String>()
            for (entry in pending) {
                if (!_state.value.importBusy || _state.value.spotifyPlan == null) break
                val query = listOf(entry.row.title, entry.row.artist).filter { it.isNotBlank() }.joinToString(" ")
                val hit = runCatching {
                    YouTubeMusic.search(query, limit = 1, configured = snapshot().preferences.ytDlpPath).firstOrNull()
                }.getOrNull()
                done++
                if (hit != null && hit.videoId.length == 11 &&
                    !SpotifyMatcher.variantMismatch(entry.row.title, hit.title)
                ) {
                    val wanted = SpotifyMatcher.normalize(entry.row.title)
                    val foundTitle = SpotifyMatcher.normalize(hit.title)
                    val plausible = wanted == foundTitle || foundTitle.contains(wanted) || wanted.contains(foundTitle)
                    if (plausible && usedVideos[hit.videoId] == null) {
                        usedVideos[hit.videoId] = entry.key
                        val id = "yt_${hit.videoId}"
                        val quality = if (wanted == foundTitle) MatchQuality.MEDIUM else MatchQuality.LOW
                        val remote = RemoteTrack(
                            id = id,
                            videoId = hit.videoId,
                            title = hit.title,
                            artist = hit.artist.ifBlank { entry.row.artist },
                            album = "YouTube Music",
                            durationMs = hit.durationMs,
                            artworkUrl = hit.thumbnail,
                            source = "YOUTUBE",
                            available = true,
                            isrc = entry.row.isrc,
                            confidence = quality.name,
                            matchedTrackId = id,
                            updatedAt = System.currentTimeMillis()
                        )
                        current = current.repoint(
                            entry.key, id, quality, remote,
                            if (quality == MatchQuality.MEDIUM) "Coincidencia en YouTube Music" else "Resultado de YouTube para revisar"
                        )
                        found++
                    }
                }
                _state.update {
                    it.copy(importStatus = "Buscando $done/${pending.size} en YouTube Music · $found encontradas")
                }
            }
            val remaining = current.pendingYoutube().size
            _state.update {
                it.copy(
                    importBusy = false,
                    spotifyPlan = current,
                    importStatus = "$found coincidencias en YouTube Music · $remaining siguen sin coincidencia. " +
                        "Revisa las dudosas antes de importar."
                )
            }
        }
    }

    fun confirmSpotifyImport(updateExisting: Boolean) {
        val ui = _state.value
        val plan = ui.spotifyPlan ?: return
        if (plan.songs == 0) return
        Storage.backup("pre-spotify-import")
        var created = 0
        var updated = 0
        var songs = 0
        mutate { data ->
            val result = SpotifyImportApply.apply(data, plan, updateExisting, ui.spotifySelected)
            created = result.created.size
            updated = result.updated.size
            songs = result.created.sumOf { it.trackIds.size } + result.updated.sumOf { it.trackIds.size }
            result.data
        }
        val message = buildString {
            if (created > 0) append("$created lista(s) importada(s)")
            if (updated > 0) { if (isNotEmpty()) append(" · "); append("$updated actualizada(s)") }
            append(" · $songs canciones")
            if (ui.spotifyPlan.unmatched > 0) append(" · ${ui.spotifyPlan.unmatched} sin coincidencia conservadas")
        }
        _state.update { it.copy(spotifyPlan = null, importStatus = message, notice = message) }
    }

    fun confirmYoutubeImport(updateExisting: Boolean) {
        val plan = _state.value.youtubePlan ?: return
        Storage.backup("pre-yt-import")
        var playlistId: String? = null
        var unavailable = 0
        mutate { data ->
            val result = YouTubeImport.apply(data, plan, updateExisting)
            playlistId = result.playlist.id
            unavailable = plan.unavailable
            result.data
        }
        val message = buildString {
            append("«${plan.name}» importada · ${plan.total} canciones")
            if (unavailable > 0) append(" · $unavailable no disponibles en YouTube")
        }
        _state.update {
            it.copy(
                youtubePlan = null,
                onlineStatus = message,
                notice = message,
                screen = Screen.PLAYLISTS,
                selectedPlaylistId = playlistId,
                importStatus = message
            )
        }
    }

    /** Saves LRC text against a stable song id; an older document never overwrites a newer one. */
    fun saveLyrics(trackId: String, lrc: String, source: String = "LOCAL"): Boolean {
        if (trackId.isBlank() || lrc.isBlank()) return false
        val now = System.currentTimeMillis()
        val doc = LyricDoc(trackId = trackId, lrc = lrc.trimEnd(), source = source, synced = Lyrics.isSynced(lrc), updatedAt = now)
        mutate { data ->
            val existing = data.lyric(trackId)
            if (existing != null && existing.updatedAt > now) return@mutate data
            data.copy(lyrics = data.lyrics.filterNot { it.trackId == trackId } + doc)
        }
        return true
    }

    fun importLyricsFile(file: File, trackId: String): String {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            ?: return "No se pudo leer ${file.name}."
        if (Lyrics.fromText(text).isEmpty()) return "${file.name} no tiene marcas de tiempo LRC."
        return if (saveLyrics(trackId, text, "IMPORTED")) "Letra añadida: ${file.name}" else "No se pudo guardar la letra."
    }

    /** Plays a YouTube song referenced by a playlist: audio is cached, the id never changes. */
    private fun startRemoteYouTube(remote: RemoteTrack) {
        val videoKey = remote.videoKey()
        if (!remote.available || videoKey.isBlank()) {
            _state.update { it.copy(notice = "«${remote.title}» ya no está disponible en YouTube Music.") }
            return
        }
        remoteJob?.cancel()
        playEmbed(videoKey, remote.title, remote.artist, remote.durationMs)
        // The history and the counters are still the app's: the video only provides sound.
        val track = Track(
            id = remote.id,
            title = remote.title,
            artist = remote.artist,
            album = remote.album,
            durationMs = remote.durationMs,
            path = "embed:$videoKey",
            artwork = remote.artworkUrl,
            addedAt = 0,
            updatedAt = remote.updatedAt
        )
        playStartedAt = System.currentTimeMillis()
        playStartedTrack = track
        mutate { data ->
            data.copy(recent = (listOf(track.id) + data.recent.filterNot { it == track.id }).take(24))
        }
        registerPlay(track)
    }

    // -------------------------------------------------------------- playlists

    fun createPlaylist(name: String, description: String = ""): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val now = System.currentTimeMillis()
        val playlist = Playlist(UUID.randomUUID().toString(), clean, description.trim(), emptyList(), now, now)
        mutate { data ->
            val next = data.copy(playlists = data.playlists + playlist)
            next
        }
        return playlist.id
    }

    fun updatePlaylist(playlistId: String, name: String? = null, description: String? = null) {
        val now = System.currentTimeMillis()
        mutate { data ->
            val playlists = data.playlists.map { playlist ->
                if (playlist.id != playlistId) playlist else playlist.copy(
                    name = name?.trim()?.takeIf { it.isNotBlank() } ?: playlist.name,
                    description = description?.trim() ?: playlist.description,
                    updatedAt = now
                )
            }
            val next = data.copy(playlists = playlists)
            next
        }
    }

    fun deletePlaylist(id: String) {
        val now = System.currentTimeMillis()
        mutate { data ->
            val next = data.copy(playlists = data.playlists.filterNot { it.id == id })
            next
        }
        _state.update { if (it.selectedPlaylistId == id) it.copy(selectedPlaylistId = null) else it }
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        val now = System.currentTimeMillis()
        mutate { data ->
            val playlists = data.playlists.map { playlist ->
                if (playlist.id != playlistId || playlist.trackIds.contains(trackId)) playlist
                else playlist.copy(trackIds = playlist.trackIds + trackId, updatedAt = now)
            }
            val next = data.copy(playlists = playlists)
            next
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        val now = System.currentTimeMillis()
        mutate { data ->
            val playlists = data.playlists.map { playlist ->
                if (playlist.id != playlistId) playlist
                else playlist.copy(trackIds = playlist.trackIds - trackId, updatedAt = now)
            }
            val next = data.copy(playlists = playlists)
            next
        }
    }

    /** Moves a track inside a playlist, which is what decides the play order. */
    fun moveInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        val playlist = snapshot().playlist(playlistId) ?: return
        if (fromIndex !in playlist.trackIds.indices) return
        val target = toIndex.coerceIn(0, playlist.trackIds.lastIndex)
        if (fromIndex == target) return
        val mutable = playlist.trackIds.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(target, item)
        val now = System.currentTimeMillis()
        mutate { data ->
            val playlists = data.playlists.map {
                if (it.id == playlistId) it.copy(trackIds = mutable.toList(), updatedAt = now) else it
            }
            val next = data.copy(playlists = playlists)
            next
        }
    }

    // --------------------------------------------------------- profile/theme

    fun renameProfile(name: String) {
        val now = System.currentTimeMillis()
        mutate { data ->
            val next = data.copy(
                profile = data.profile.copy(displayName = name.ifBlank { "Cypher" }),
                profileUpdatedAt = now
            )
            next
        }
    }

    /** Saves one of the interface colours; [CypherApp] repaints as soon as it changes. */
    fun setThemeColors(colors: com.cypher.desktop.core.ThemeColors) {
        val now = System.currentTimeMillis()
        mutate { data ->
            data.copy(preferences = data.preferences.copy(colors = colors), themeUpdatedAt = now)
        }
    }

    fun resetThemeColors() = setThemeColors(com.cypher.desktop.core.ThemeColors())

    fun setLyricsColorCustom(color: Long) {
        mutate { it.copy(preferences = it.preferences.copy(lyricsColorCustom = color, lyricsColor = "CUSTOM")) }
    }

    fun setAccent(color: Long) {
        val now = System.currentTimeMillis()
        mutate { data ->
            data.copy(preferences = data.preferences.copy(accent = color), themeUpdatedAt = now)
        }
    }

    fun setLibraryStart(section: LibrarySection) {
        mutate { it.copy(preferences = it.preferences.copy(libraryStart = section.name)) }
    }

}

/**
 * Pure helpers so the reference rules stay testable without the UI.
 *
 * Favorites received from another device have metadata but no file here, so they are
 * valid references too: dropping them would silently lose what the phone marked.
 */
object DataMutations {
    fun reconcileTracks(data: LibraryData): LibraryData {
        val ids = data.tracks.map { it.id }.toSet() + data.remoteTracks.map { it.id }.toSet()
        return data.copy(
            favorites = data.favorites.filter { it in ids },
            playlists = data.playlists.map { playlist ->
                playlist.copy(trackIds = playlist.trackIds.filter { it in ids })
            },
            recent = data.recent.filter { it in ids }
        )
    }
}
