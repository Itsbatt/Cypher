package com.cypher.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.cypher.music.MainActivity
import com.cypher.music.data.room.toDomain
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import com.cypher.music.scrobble.ScrobbleManager
import com.cypher.music.service.PlaybackService
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RepeatMode {
    OFF, ALL, ONE
}

enum class SleepTimerMode(val minutes: Int, val label: String) {
    OFF(0, "Desactivado"),
    MIN_15(15, "15 minutos"),
    MIN_30(30, "30 minutos"),
    MIN_45(45, "45 minutos"),
    MIN_60(60, "60 minutos"),
    END_OF_TRACK(0, "Al finalizar la canción actual")
}

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val isRepeat: Boolean
        get() = repeatMode != RepeatMode.OFF

    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

@OptIn(UnstableApi::class)
class MusicPlayerManager(private val context: Context) {
    companion object {
        var activeMediaSession: MediaSession? = null
        var activeMediaLibrarySession: MediaLibrarySession? = null
        var activeInstance: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return activeInstance ?: synchronized(this) {
                activeInstance ?: MusicPlayerManager(context.applicationContext).also {
                    activeInstance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var listeningTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var fadeInJob: Job? = null

    // Native DSP Loudness Enhancer
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var isLoudnessEnhancerEnabled: Boolean = true

    // Native DSP Equalizer Manager
    val equalizerManager = EqualizerManager(context)

    // Background Native Scrobbler (Last.fm & ListenBrainz)
    val scrobbleManager = ScrobbleManager.getInstance(context)

    // Sleep Timer states
    private val _sleepTimerMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepTimerMode: StateFlow<SleepTimerMode> = _sleepTimerMode.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()

    // Crossfade setting (0 = disabled, 2, 4, 6, 8 seconds)
    private var crossfadeSeconds: Int = 0

    // Listening time analytics callback: (track: Track, elapsedMs: Long, isNewPlay: Boolean)
    var onListeningTimeAccumulated: ((track: Track, elapsedMs: Long, isNewPlay: Boolean) -> Unit)? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // 120Hz Isolated Playback Progress Streams
    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private var lastSeekTimestamp = 0L

    // 1. ExoPlayer with Persistent Streaming Cache
    private val mediaSourceFactory = MediaCacheManager.buildMediaSourceFactory(context)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    // 2. YouTube IFrame Player instance
    private var youTubePlayer: YouTubePlayer? = null
    private var pendingYouTubeVideoId: String? = null
    private var isYouTubeListenerAttached = false
    private var isPlayingOffline: Boolean = false

    // 3. Android System MediaSession Player Forwarder
    private val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
        override fun getDuration(): Long {
            val cur = _state.value
            return if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE && !isPlayingOffline) {
                cur.durationMs.coerceAtLeast(0L)
            } else {
                super.getDuration()
            }
        }

        override fun getCurrentPosition(): Long {
            val cur = _state.value
            return if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE && !isPlayingOffline) {
                _playbackPositionMs.value.coerceAtLeast(0L)
            } else {
                super.getCurrentPosition()
            }
        }

        override fun isPlaying(): Boolean {
            val cur = _state.value
            return if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE && !isPlayingOffline) {
                cur.isPlaying
            } else {
                super.isPlaying()
            }
        }

        override fun getPlaybackState(): Int {
            val cur = _state.value
            return if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE && !isPlayingOffline) {
                if (cur.isBuffering) Player.STATE_BUFFERING else Player.STATE_READY
            } else {
                super.getPlaybackState()
            }
        }

        override fun getPlayWhenReady(): Boolean {
            val cur = _state.value
            return if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE && !isPlayingOffline) {
                cur.isPlaying
            } else {
                super.getPlayWhenReady()
            }
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_PLAY_PAUSE -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_PLAY_PAUSE)
                .build()
        }

        override fun seekTo(positionMs: Long) {
            val cur = _state.value
            if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE) {
                val fraction = if (cur.durationMs > 0) positionMs.toFloat() / cur.durationMs else 0f
                seekToProgress(fraction)
            } else {
                super.seekTo(positionMs)
            }
        }

        override fun seekToNext() {
            playNext()
        }

        override fun seekToNextMediaItem() {
            playNext()
        }

        override fun seekToPrevious() {
            playPrevious()
        }

        override fun seekToPreviousMediaItem() {
            playPrevious()
        }

        override fun play() {
            val cur = _state.value
            if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE) {
                if (!cur.isPlaying) togglePlayPause()
            } else {
                super.play()
            }
        }

        override fun pause() {
            val cur = _state.value
            if (cur.currentTrack?.sourceType == AudioSourceType.YOUTUBE) {
                if (cur.isPlaying) togglePlayPause()
            } else {
                super.pause()
            }
        }
    }

    // 4. Android System MediaLibrarySession (Android Auto & MediaSession compatible)
    val mediaLibrarySession: MediaLibrarySession
    val mediaSession: MediaSession get() = mediaLibrarySession

    init {
        activeInstance = this

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val libraryCallback = object : MediaLibrarySession.Callback {
            private fun createMediaItemFromTrack(t: Track): MediaItem {
                val uri = if (t.sourceType == AudioSourceType.LOCAL && !t.path.isNullOrEmpty() && File(t.path).exists()) {
                    Uri.fromFile(File(t.path))
                } else if (!t.streamUrl.isNullOrEmpty() && (t.streamUrl.startsWith("content://") || t.streamUrl.startsWith("http"))) {
                    Uri.parse(t.streamUrl)
                } else {
                    Uri.parse("cypher://track/${t.id}")
                }
                return MediaItem.Builder()
                    .setMediaId(t.id)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(t.title)
                            .setArtist(t.artist)
                            .setAlbumTitle(t.album)
                            .setArtworkUri(if (!t.artworkUrl.isNullOrEmpty()) Uri.parse(t.artworkUrl) else null)
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setFolderType(MediaMetadata.FOLDER_TYPE_NONE)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .build()
                    ).build()
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                android.util.Log.e("CypherAA", ">>> onGetLibraryRoot from ${browser.packageName}")
                val rootExtras = android.os.Bundle().apply {
                    putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // List
                    putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1) // List
                }
                val libraryParams = MediaLibraryService.LibraryParams.Builder()
                    .setExtras(rootExtras)
                    .build()
                val rootItem = MediaItem.Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Cypher")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, libraryParams))
            }

            override fun onSubscribe(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                android.util.Log.e("CypherAA", ">>> onSubscribe: parentId='$parentId' from ${browser.packageName}")
                scope.launch(Dispatchers.Main) {
                    try {
                        session.notifyChildrenChanged(browser, parentId, 100, params)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                return Futures.immediateFuture(LibraryResult.ofVoid(params))
            }

            override fun onUnsubscribe(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String
            ): ListenableFuture<LibraryResult<Void>> {
                android.util.Log.e("CypherAA", ">>> onUnsubscribe: parentId='$parentId' from ${browser.packageName}")
                return Futures.immediateFuture(LibraryResult.ofVoid())
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                android.util.Log.e("CypherAA", ">>> onGetChildren: parentId='$parentId' page=$page pageSize=$pageSize")
                val allMatchedItems = mutableListOf<MediaItem>()
                try {
                    val roomDb = com.cypher.music.data.room.CypherDatabase.getInstance(context)

                    when (parentId) {
                        "root" -> {
                            // 1. Playlists folder tab
                            allMatchedItems.add(
                                MediaItem.Builder()
                                    .setMediaId("cat_playlists")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("Tus Playlists")
                                            .setSubtitle("Tus listas de reproducción")
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                            .build()
                                    ).build()
                            )
                            // 2. Favorites tab
                            allMatchedItems.add(
                                MediaItem.Builder()
                                    .setMediaId("cat_favorites")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("Canciones Favoritas")
                                            .setSubtitle("Tus temas guardados")
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                            .build()
                                    ).build()
                            )
                            // 3. Local music on device tab
                            allMatchedItems.add(
                                MediaItem.Builder()
                                    .setMediaId("cat_local")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("Música en el Dispositivo")
                                            .setSubtitle("Canciones locales del almacenamiento")
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                            .build()
                                    ).build()
                            )
                            // 4. Recently played tab
                            allMatchedItems.add(
                                MediaItem.Builder()
                                    .setMediaId("cat_recents")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("Escuchadas Recientemente")
                                            .setSubtitle("Últimas pistas reproducidas")
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                            .build()
                                    ).build()
                            )
                        }
                        "cat_playlists" -> {
                            val allPlaylists = roomDb.playlistDao().allPlaylistsWithTracks.map { it.toDomain() }
                            // Sort playlists with tracks first so empty playlists don't show at the top
                            val sortedPlaylists = allPlaylists.sortedByDescending { it.tracks.size }
                            for (pl in sortedPlaylists) {
                                val trackCount = pl.tracks.size
                                allMatchedItems.add(
                                    MediaItem.Builder()
                                        .setMediaId("pl_${pl.id}")
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(pl.name)
                                                .setSubtitle("$trackCount canciones")
                                                .setArtworkUri(if (!pl.coverUri.isNullOrEmpty()) Uri.parse(pl.coverUri) else null)
                                                .setIsBrowsable(true)
                                                .setIsPlayable(false)
                                                .setFolderType(MediaMetadata.FOLDER_TYPE_TITLES)
                                                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                                .build()
                                        ).build()
                                )
                            }
                        }
                        "cat_favorites" -> {
                            val favs = roomDb.trackDao().favoriteTracks.map { it.toDomain() }
                            for (t in favs) {
                                allMatchedItems.add(createMediaItemFromTrack(t))
                            }
                        }
                        "cat_local" -> {
                            try {
                                val projection = arrayOf(
                                    android.provider.MediaStore.Audio.Media._ID,
                                    android.provider.MediaStore.Audio.Media.TITLE,
                                    android.provider.MediaStore.Audio.Media.ARTIST,
                                    android.provider.MediaStore.Audio.Media.ALBUM,
                                    android.provider.MediaStore.Audio.Media.DURATION,
                                    android.provider.MediaStore.Audio.Media.ALBUM_ID,
                                    android.provider.MediaStore.Audio.Media.DATA
                                )
                                val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
                                val sortOrder = "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
                                val cursor = context.contentResolver.query(
                                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    projection,
                                    selection,
                                    null,
                                    sortOrder
                                )
                                cursor?.use { c ->
                                    val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                                    val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                                    val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                                    val albumCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                                    val durCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                                    val albumIdCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
                                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)

                                    while (c.moveToNext()) {
                                        val id = c.getLong(idCol)
                                        val title = c.getString(titleCol) ?: "Pista local"
                                        val artist = c.getString(artistCol) ?: "Artista desconocido"
                                        val album = c.getString(albumCol) ?: "Álbum local"
                                        val dur = c.getLong(durCol)
                                        val albumId = c.getLong(albumIdCol)
                                        val filePath = c.getString(dataCol)
                                        val contentUri = android.content.ContentUris.withAppendedId(
                                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                                        )
                                        val artUri = android.content.ContentUris.withAppendedId(
                                            Uri.parse("content://media/external/audio/albumart"), albumId
                                        ).toString()

                                        val track = Track(
                                            id = "local_$id",
                                            title = title,
                                            artist = if (artist == "<unknown>") "Artista desconocido" else artist,
                                            album = album,
                                            durationMs = dur,
                                            artworkUrl = artUri,
                                            streamUrl = contentUri.toString(),
                                            sourceType = AudioSourceType.LOCAL,
                                            path = filePath
                                        )
                                        allMatchedItems.add(createMediaItemFromTrack(track))
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CypherAA", "Error querying local audio", e)
                            }
                        }
                        "cat_recents" -> {
                            val recents = roomDb.recentlyPlayedDao().getRecentlyPlayed(30).map { it.toDomain() }
                            for (r in recents) {
                                val track = roomDb.trackDao().getTrackById(r.extraId ?: r.id)?.toDomain()
                                    ?: Track(
                                        id = r.extraId ?: r.id,
                                        title = r.title,
                                        artist = r.subtitle,
                                        album = "",
                                        durationMs = 0L,
                                        artworkUrl = r.artworkUrl,
                                        streamUrl = r.streamUrl ?: "",
                                        sourceType = r.sourceType
                                    )
                                allMatchedItems.add(createMediaItemFromTrack(track))
                            }
                        }
                        else -> {
                            val plId = if (parentId.startsWith("pl_")) parentId.removePrefix("pl_") else parentId
                            val tracks = roomDb.playlistDao().getTracksForPlaylist(plId).map { it.toDomain() }
                            for (t in tracks) {
                                allMatchedItems.add(createMediaItemFromTrack(t))
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CypherAA", "Error onGetChildren for $parentId", e)
                }

                // If non-root and non-cat_playlists category has 0 items, provide an informative placeholder
                // so Android Auto UI stops spinning and shows an empty indicator
                if (parentId != "root" && parentId != "cat_playlists" && allMatchedItems.isEmpty()) {
                    allMatchedItems.add(
                        MediaItem.Builder()
                            .setMediaId("empty_${parentId}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Sin canciones")
                                    .setSubtitle("Esta sección o lista no tiene canciones aún")
                                    .setIsBrowsable(false)
                                    .setIsPlayable(false)
                                    .setFolderType(MediaMetadata.FOLDER_TYPE_NONE)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            ).build()
                    )
                }

                // Strict Media3 pagination constraint: never return more items than pageSize!
                val pagedItems = if (pageSize > 0 && pageSize != Int.MAX_VALUE) {
                    val from = (page * pageSize).coerceIn(0, allMatchedItems.size)
                    val to = (from + pageSize).coerceIn(0, allMatchedItems.size)
                    allMatchedItems.subList(from, to)
                } else {
                    allMatchedItems
                }

                android.util.Log.e("CypherAA", "<<< onGetChildren returning ${pagedItems.size} items (total ${allMatchedItems.size}) for '$parentId'")
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.copyOf(pagedItems), params)
                )
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                android.util.Log.e("CypherAA", ">>> onGetItem: mediaId='$mediaId'")
                return try {
                    val roomDb = com.cypher.music.data.room.CypherDatabase.getInstance(context)
                    val plId = if (mediaId.startsWith("pl_")) mediaId.removePrefix("pl_") else mediaId
                    val matchedPlaylist = if (mediaId != "root" && !mediaId.startsWith("cat_")) {
                        roomDb.playlistDao().getPlaylistWithTracks(plId)?.toDomain()
                            ?: roomDb.playlistDao().getPlaylistWithTracks(mediaId)?.toDomain()
                    } else null

                    val item: MediaItem = when {
                        mediaId == "root" -> {
                            MediaItem.Builder()
                                .setMediaId("root")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Cypher")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .build()
                                )
                                .build()
                        }
                        mediaId == "cat_playlists" -> {
                            MediaItem.Builder()
                                .setMediaId("cat_playlists")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Tus Playlists")
                                        .setSubtitle("Tus listas de reproducción")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                        .build()
                                ).build()
                        }
                        mediaId == "cat_favorites" -> {
                            MediaItem.Builder()
                                .setMediaId("cat_favorites")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Canciones Favoritas")
                                        .setSubtitle("Tus temas guardados")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                        .build()
                                ).build()
                        }
                        mediaId == "cat_local" -> {
                            MediaItem.Builder()
                                .setMediaId("cat_local")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Música en el Dispositivo")
                                        .setSubtitle("Canciones locales del almacenamiento")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                        .build()
                                ).build()
                        }
                        mediaId == "cat_recents" -> {
                            MediaItem.Builder()
                                .setMediaId("cat_recents")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Escuchadas Recientemente")
                                        .setSubtitle("Últimas pistas reproducidas")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                        .build()
                                ).build()
                        }
                        matchedPlaylist != null -> {
                            MediaItem.Builder()
                                .setMediaId(mediaId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(matchedPlaylist.name)
                                        .setSubtitle("${matchedPlaylist.tracks.size} canciones")
                                        .setArtworkUri(if (!matchedPlaylist.coverUri.isNullOrEmpty()) Uri.parse(matchedPlaylist.coverUri) else null)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_TITLES)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build()
                                ).build()
                        }
                        mediaId.startsWith("pl_") -> {
                            MediaItem.Builder()
                                .setMediaId(mediaId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Playlist")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_TITLES)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build()
                                ).build()
                        }
                        else -> {
                            val entity = roomDb.trackDao().getTrackById(mediaId)
                            if (entity != null) {
                                createMediaItemFromTrack(entity.toDomain())
                            } else {
                                MediaItem.Builder()
                                    .setMediaId(mediaId)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle("Pista de audio")
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setFolderType(MediaMetadata.FOLDER_TYPE_NONE)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                            .build()
                                    ).build()
                            }
                        }
                    }
                    Futures.immediateFuture(LibraryResult.ofItem(item, null))
                } catch (e: Exception) {
                    android.util.Log.e("CypherAA", "Error onGetItem for $mediaId", e)
                    Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                }
            }

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                android.util.Log.e("CypherAA", ">>> onConnect from ${controller.packageName}")
                val connectionResult = super.onConnect(session, controller)
                val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    .add(androidx.media3.session.SessionCommand(androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                    .add(androidx.media3.session.SessionCommand(androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE))
                    .add(androidx.media3.session.SessionCommand(androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE))
                    .add(androidx.media3.session.SessionCommand(androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
                    .add(androidx.media3.session.SessionCommand(androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                    .add(androidx.media3.session.SessionCommand(androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableSessionCommands)
                    .setAvailablePlayerCommands(connectionResult.availablePlayerCommands)
                    .build()
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                android.util.Log.e("CypherAA", ">>> onAddMediaItems: ${mediaItems.map { it.mediaId }}")
                val roomDb = com.cypher.music.data.room.CypherDatabase.getInstance(context)
                val updatedList = mediaItems.map { item ->
                    try {
                        val plId = if (item.mediaId.startsWith("pl_")) item.mediaId.removePrefix("pl_") else item.mediaId
                        val pl = roomDb.playlistDao().getPlaylistWithTracks(plId)?.toDomain()
                            ?: roomDb.playlistDao().getPlaylistWithTracks(item.mediaId)?.toDomain()
                        if (pl != null && pl.tracks.isNotEmpty()) {
                            scope.launch(Dispatchers.Main) {
                                playTrack(pl.tracks.first(), pl.tracks)
                            }
                            createMediaItemFromTrack(pl.tracks.first())
                        } else {
                            val track = roomDb.trackDao().getTrackById(item.mediaId)?.toDomain()

                            if (track != null) {
                                scope.launch(Dispatchers.Main) {
                                    val allPlaylists = roomDb.playlistDao().allPlaylistsWithTracks.map { it.toDomain() }
                                    val containingPlaylist = allPlaylists.firstOrNull { p ->
                                        p.tracks.any { it.id == track.id }
                                    }
                                    val queue = containingPlaylist?.tracks ?: listOf(track)
                                    playTrack(track, queue)
                                }
                                createMediaItemFromTrack(track)
                            } else {
                                item
                            }
                        }
                    } catch (e: Exception) {
                        item
                    }
                }.toMutableList()
                return Futures.immediateFuture(updatedList)
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(context, forwardingPlayer, libraryCallback)
            .setSessionActivity(sessionActivity)
            .build()
        activeMediaSession = mediaLibrarySession
        activeMediaLibrarySession = mediaLibrarySession

        scope.launch(Dispatchers.Main) {
            try {
                mediaLibrarySession.notifyChildrenChanged("root", 0, null)
                mediaLibrarySession.notifyChildrenChanged("cat_playlists", 0, null)
                mediaLibrarySession.notifyChildrenChanged("cat_favorites", 0, null)
            } catch (e: Exception) {
                // Ignore if no controllers connected yet
            }
        }

        // Attach Equalizer and LoudnessEnhancer to initial session if available
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            equalizerManager.attachAudioSession(exoPlayer.audioSessionId)
            attachLoudnessEnhancer(exoPlayer.audioSessionId)
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizerManager.attachAudioSession(audioSessionId)
                attachLoudnessEnhancer(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicPlayerManager", "ExoPlayer onPlayerError: ${error.errorCodeName} (${error.errorCode})", error)
                val track = _state.value.currentTrack
                val is403 = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                        error.message?.contains("403") == true ||
                        error.cause?.message?.contains("403") == true
                if (track != null && is403) {
                    com.cypher.music.network.StreamingTelemetry.recordError(403, error.message, "ExoPlayer:StreamPlayback")
                    scope.launch {
                        try {
                            val videoId = extractVideoId(track.streamUrl.ifEmpty { track.id })
                            Log.w("MusicPlayerManager", "Refreshing expired 403 stream URL for video: $videoId")
                            val freshUrl = com.cypher.music.network.YouTubeRepository().getStreamUrl(videoId)
                            if (!freshUrl.isNullOrEmpty()) {
                                val savedPos = _playbackPositionMs.value
                                val metadata = MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setAlbumTitle(track.album)
                                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                                    .build()
                                val mediaItem = MediaItem.Builder()
                                    .setUri(Uri.parse(freshUrl))
                                    .setMediaMetadata(metadata)
                                    .build()
                                exoPlayer.setMediaItem(mediaItem, savedPos)
                                exoPlayer.prepare()
                                exoPlayer.play()
                                Log.i("MusicPlayerManager", "Successfully resumed playback with fresh stream URL at ${savedPos}ms")
                                return@launch
                            } else {
                                // Tier 4 Fallback: YouTube IFrame player fallback to prevent playback disruption
                                Log.w("MusicPlayerManager", "Direct stream unavailable. Switching to official IFrame player engine.")
                                youTubePlayer?.loadVideo(videoId, (_playbackPositionMs.value / 1000f))
                                return@launch
                            }
                        } catch (e: Exception) {
                            Log.e("MusicPlayerManager", "Failed to refresh 403 stream URL", e)
                        }
                        playNext()
                    }
                } else {
                    _state.update { it.copy(isBuffering = false, isPlaying = false) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_state.value.currentTrack?.sourceType == AudioSourceType.LOCAL || isPlayingOffline) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) startLocalProgressTracker() else stopLocalProgressTracker()
                    PlaybackService.updateService(context)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (_state.value.currentTrack?.sourceType == AudioSourceType.YOUTUBE && !isPlayingOffline) {
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE) {
                        if (!playWhenReady) {
                            youTubePlayer?.pause()
                            _state.update { it.copy(isPlaying = false) }
                        } else {
                            youTubePlayer?.play()
                            _state.update { it.copy(isPlaying = true) }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (_state.value.currentTrack?.sourceType == AudioSourceType.LOCAL || isPlayingOffline) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                        Player.STATE_READY -> _state.update {
                            it.copy(
                                isBuffering = false,
                                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                            )
                        }
                        Player.STATE_ENDED -> {
                            if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
                                pause()
                                setSleepTimer(SleepTimerMode.OFF)
                            } else {
                                playNext()
                            }
                        }
                        Player.STATE_IDLE -> _state.update { it.copy(isBuffering = false) }
                    }
                }
            }
        })
    }

    fun attachYouTubePlayer(ytPlayer: YouTubePlayer) {
        Log.d("AppleMusicPlayer", "attachYouTubePlayer: YouTubePlayer instance registered")
        this.youTubePlayer = ytPlayer

        pendingYouTubeVideoId?.let { vid ->
            Log.d("AppleMusicPlayer", "Loading pending video: $vid")
            ytPlayer.loadVideo(vid, 0f)
            pendingYouTubeVideoId = null
        }

        if (!isYouTubeListenerAttached) {
            isYouTubeListenerAttached = true
            ytPlayer.addListener(object : AbstractYouTubePlayerListener() {
                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                    Log.d("AppleMusicPlayer", "YouTubePlayer onStateChange: $state")
                    if (_state.value.currentTrack?.sourceType == AudioSourceType.YOUTUBE) {
                        when (state) {
                            PlayerConstants.PlayerState.PLAYING -> {
                                bufferingWatchdogJob?.cancel()
                                _state.update { it.copy(isPlaying = true, isBuffering = false) }
                                if (!exoPlayer.isPlaying) exoPlayer.play()
                                PlaybackService.updateService(context)
                            }
                            PlayerConstants.PlayerState.PAUSED -> {
                                bufferingWatchdogJob?.cancel()
                                _state.update { it.copy(isPlaying = false, isBuffering = false) }
                                if (exoPlayer.isPlaying) exoPlayer.pause()
                                PlaybackService.updateService(context)
                            }
                            PlayerConstants.PlayerState.BUFFERING -> {
                                _state.update { it.copy(isBuffering = true) }
                            }
                            PlayerConstants.PlayerState.VIDEO_CUED -> {
                                Log.d("AppleMusicPlayer", "Video cued -> forcing play()")
                                youTubePlayer.play()
                            }
                            PlayerConstants.PlayerState.ENDED -> {
                                bufferingWatchdogJob?.cancel()
                                if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
                                    pause()
                                    setSleepTimer(SleepTimerMode.OFF)
                                } else {
                                    playNext()
                                }
                            }
                            else -> {}
                        }
                    }
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    Log.e("AppleMusicPlayer", "YouTubePlayer onError: $error")
                    bufferingWatchdogJob?.cancel()
                    _state.update { it.copy(isBuffering = false, isPlaying = false) }

                    if (error == PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ||
                        error == PlayerConstants.PlayerError.VIDEO_NOT_FOUND) {
                        scope.launch {
                            delay(1000)
                            playNext()
                        }
                    }
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    val curTrack = _state.value.currentTrack
                    if (curTrack?.sourceType == AudioSourceType.YOUTUBE && !isPlayingOffline) {
                        if (System.currentTimeMillis() - lastSeekTimestamp < 600L) {
                            return
                        }
                        val pos = (second * 1000).toLong().coerceAtLeast(0L)
                        _playbackPositionMs.value = pos
                        val dur = _state.value.durationMs
                        val frac = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                        _playbackProgress.value = frac
                        _state.update { it.copy(currentPositionMs = pos) }

                        checkVolumeTransitions(pos, dur)
                    }
                }

                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                    if (_state.value.currentTrack?.sourceType == AudioSourceType.YOUTUBE && duration > 0) {
                        _state.update { it.copy(durationMs = (duration * 1000).toLong()) }
                    }
                }
            })
        }

        // 5. Effective Listening Time Tracker
        listeningTrackerJob = scope.launch(Dispatchers.Default) {
            var lastTick = System.currentTimeMillis()
            var lastReportedTrackId: String? = null
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val curState = _state.value
                val track = curState.currentTrack
                if (curState.isPlaying && track != null) {
                    val isNewPlay = track.id != lastReportedTrackId
                    lastReportedTrackId = track.id
                    if (isNewPlay) {
                        scrobbleManager.onTrackStarted(track)
                    }
                    scrobbleManager.onPlaybackTick(track, curState.currentPositionMs, curState.durationMs)
                    val elapsed = (now - lastTick).coerceIn(0L, 2500L)
                    if (elapsed > 0) {
                        withContext(Dispatchers.Main) {
                            onListeningTimeAccumulated?.invoke(track, elapsed, isNewPlay)
                        }
                    }
                } else {
                    lastReportedTrackId = null
                }
                lastTick = now
            }
        }
    }

    private fun extractVideoId(raw: String): String {
        return raw.removePrefix("yt_")
            .substringAfterLast("v=")
            .substringAfterLast("/")
            .substringBefore("?")
            .substringBefore("&")
            .trim()
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        val index = newQueue.indexOfFirst { it.id == track.id }.let { if (it == -1) 0 else it }

        Log.d("AppleMusicPlayer", "playTrack: '${track.title}' (${track.sourceType})")
        scrobbleManager.onTrackStarted(track)

        _playbackPositionMs.value = 0L
        _playbackProgress.value = 0f

        _state.update {
            it.copy(
                currentTrack = track,
                queue = newQueue,
                queueIndex = index,
                isBuffering = true,
                currentPositionMs = 0L,
                durationMs = track.durationMs
            )
        }

        // Watchdog to guarantee UI never gets stuck in buffering
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = scope.launch {
            delay(8000)
            if (_state.value.isBuffering && !_state.value.isPlaying) {
                Log.w("AppleMusicPlayer", "Buffering watchdog: re-triggering play()")
                youTubePlayer?.play()
                delay(2000)
                if (_state.value.isBuffering && !_state.value.isPlaying) {
                    Log.w("AppleMusicPlayer", "Buffering watchdog: clearing isBuffering flag")
                    _state.update { it.copy(isBuffering = false) }
                }
            }
        }

        // Start background PlaybackService
        try {
            val serviceIntent = Intent(context, PlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w("AppleMusicPlayer", "Could not start PlaybackService", e)
        }

        // Update Android MediaSession Metadata
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
            .build()

        try {
            // Check if track is downloaded in the private offline vault
            val offlineFile = MediaCacheManager.getOfflineTrackFile(context, track.id)
            val isDownloaded = offlineFile != null && offlineFile.exists() && offlineFile.length() > 10000
            isPlayingOffline = isDownloaded

            if (track.sourceType == AudioSourceType.LOCAL || isPlayingOffline) {
                youTubePlayer?.pause()
                startLocalProgressTracker()

                exoPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                val fileUri = if (isPlayingOffline && offlineFile != null) Uri.fromFile(offlineFile) else Uri.parse(track.streamUrl)
                val mediaItem = MediaItem.Builder()
                    .setUri(fileUri)
                    .setMediaMetadata(metadata)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                Log.i("AppleMusicPlayer", "Playing '${track.title}' via ExoPlayer (offlineVault=$isPlayingOffline)")
            } else {
                stopLocalProgressTracker()
                val videoId = extractVideoId(track.streamUrl.ifEmpty { track.id })
                Log.d("AppleMusicPlayer", "Target YouTube videoId: $videoId")

                try {
                    exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, false)
                    exoPlayer.volume = 0f
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                    val silentUri = Uri.parse("android.resource://${context.packageName}/${com.cypher.music.R.raw.silence}")
                    val activeItem = MediaItem.Builder()
                        .setMediaId(track.id)
                        .setUri(silentUri)
                        .setMediaMetadata(metadata)
                        .build()
                    exoPlayer.setMediaItem(activeItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                } catch (e: Exception) {
                    Log.w("AppleMusicPlayer", "Failed to start silent carrier in ExoPlayer", e)
                }

                val player = youTubePlayer
                if (player != null) {
                    Log.d("AppleMusicPlayer", "Calling youTubePlayer.loadVideo($videoId)")
                    player.loadVideo(videoId, 0f)
                } else {
                    Log.w("AppleMusicPlayer", "youTubePlayer not attached yet, queued as pending: $videoId")
                    pendingYouTubeVideoId = videoId
                }
            }

            // Smooth crossfade fade-in for incoming track
            startFadeIn()
        } catch (e: Exception) {
            Log.e("AppleMusicPlayer", "Error in playTrack", e)
            _state.update { it.copy(isBuffering = false) }
        }
    }

    fun togglePlayPause() {
        val track = _state.value.currentTrack ?: return
        if (track.sourceType == AudioSourceType.LOCAL || isPlayingOffline) {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                exoPlayer.play()
            }
        } else {
            if (_state.value.isPlaying) {
                youTubePlayer?.pause()
                if (exoPlayer.isPlaying) exoPlayer.pause()
                _state.update { it.copy(isPlaying = false) }
            } else {
                youTubePlayer?.play()
                if (!exoPlayer.isPlaying) exoPlayer.play()
                _state.update { it.copy(isPlaying = true) }
            }
        }
        PlaybackService.updateService(context)
    }

    fun pause() {
        val track = _state.value.currentTrack
        if (track?.sourceType == AudioSourceType.LOCAL || isPlayingOffline) {
            exoPlayer.pause()
        } else {
            youTubePlayer?.pause()
            if (exoPlayer.isPlaying) exoPlayer.pause()
            _state.update { it.copy(isPlaying = false) }
        }
        PlaybackService.updateService(context)
    }

    /**
     * Handles ACTION_AUDIO_BECOMING_NOISY (cable unplugged / Bluetooth disconnected).
     *
     * Local and offline playback is paused by ExoPlayer itself through
     * setHandleAudioBecomingNoisy(true), so this only stops the YouTube IFrame engine,
     * whose audio bypasses ExoPlayer. Guarded to avoid duplicate pauses and service
     * updates when nothing is actually playing.
     */
    fun pauseForHeadphoneDisconnect() {
        val snapshot = _state.value
        val track = snapshot.currentTrack ?: return
        if (!snapshot.isPlaying) return
        if (track.sourceType == AudioSourceType.LOCAL || isPlayingOffline) return

        youTubePlayer?.pause()
        if (exoPlayer.isPlaying) exoPlayer.pause()
        _state.update { it.copy(isPlaying = false) }
        PlaybackService.updateService(context)
    }

    fun seekToProgress(fraction: Float) {
        val track = _state.value.currentTrack ?: return
        val clampedFrac = fraction.coerceIn(0f, 1f)
        val targetMs = (_state.value.durationMs * clampedFrac).toLong()
        lastSeekTimestamp = System.currentTimeMillis()
        _playbackPositionMs.value = targetMs
        _playbackProgress.value = clampedFrac
        _state.update { it.copy(currentPositionMs = targetMs) }

        if (track.sourceType == AudioSourceType.LOCAL || isPlayingOffline) {
            exoPlayer.seekTo(targetMs)
        } else {
            youTubePlayer?.seekTo(targetMs / 1000f)
        }
    }

    fun playNext() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        if (_state.value.repeatMode == RepeatMode.ONE) {
            val cur = _state.value.currentTrack ?: return
            playTrack(cur, q)
            return
        }
        val nextIdx = if (_state.value.queueIndex + 1 < q.size) {
            _state.value.queueIndex + 1
        } else if (_state.value.repeatMode == RepeatMode.ALL) {
            0
        } else return
        playTrack(q[nextIdx], q)
    }

    fun playPrevious() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        if (_state.value.currentPositionMs > 3000L) {
            seekToProgress(0f)
            return
        }
        val prevIdx = if (_state.value.queueIndex - 1 >= 0) {
            _state.value.queueIndex - 1
        } else {
            q.size - 1
        }
        playTrack(q[prevIdx], q)
    }

    fun toggleShuffle() {
        val nextShuffle = !_state.value.isShuffle
        val q = _state.value.queue
        val curTrack = _state.value.currentTrack
        if (nextShuffle && q.size > 2 && curTrack != null) {
            val remaining = q.filter { it.id != curTrack.id }.shuffled()
            val newQ = listOf(curTrack) + remaining
            _state.update { it.copy(isShuffle = true, queue = newQ, queueIndex = 0) }
        } else {
            _state.update { it.copy(isShuffle = nextShuffle) }
        }
        PlaybackService.updateService(context)
    }

    fun toggleRepeat() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update { it.copy(repeatMode = nextMode) }
        PlaybackService.updateService(context)
    }

    fun enqueueNext(track: Track) {
        val q = _state.value.queue.toMutableList()
        val insertIdx = if (_state.value.queueIndex >= 0) _state.value.queueIndex + 1 else 0
        q.add(insertIdx.coerceAtMost(q.size), track)
        _state.update { it.copy(queue = q) }
        PlaybackService.updateService(context)
    }

    fun addToQueue(track: Track) {
        val q = _state.value.queue.toMutableList()
        q.add(track)
        _state.update { it.copy(queue = q) }
        PlaybackService.updateService(context)
    }

    fun removeFromQueue(index: Int) {
        val q = _state.value.queue.toMutableList()
        if (index in q.indices) {
            q.removeAt(index)
            val newIdx = when {
                index < _state.value.queueIndex -> _state.value.queueIndex - 1
                index == _state.value.queueIndex -> _state.value.queueIndex.coerceAtMost(q.size - 1)
                else -> _state.value.queueIndex
            }
            _state.update { it.copy(queue = q, queueIndex = newIdx) }
            PlaybackService.updateService(context)
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val q = _state.value.queue
        if (index in q.indices) {
            playTrack(q[index], q)
        }
    }

    // Master volume setter handling both Local ExoPlayer and YouTube WebView
    fun setMasterPlaybackVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        if (_state.value.currentTrack?.sourceType == AudioSourceType.LOCAL) {
            exoPlayer.volume = clamped
        } else {
            youTubePlayer?.setVolume((clamped * 100).toInt())
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        crossfadeSeconds = seconds
    }

    private fun startFadeIn() {
        fadeInJob?.cancel()
        if (crossfadeSeconds <= 0) {
            setMasterPlaybackVolume(1f)
            return
        }
        fadeInJob = scope.launch {
            setMasterPlaybackVolume(0f)
            val steps = (crossfadeSeconds * 10).coerceAtLeast(10)
            val stepDelay = (crossfadeSeconds * 1000L) / steps
            for (i in 0..steps) {
                val vol = i.toFloat() / steps
                setMasterPlaybackVolume(vol)
                delay(stepDelay)
            }
            setMasterPlaybackVolume(1f)
        }
    }

    private fun checkVolumeTransitions(currentPosMs: Long, totalDurationMs: Long) {
        if (totalDurationMs <= 0) return
        val remainingMs = totalDurationMs - currentPosMs

        // Check Sleep Timer "Al finalizar la canción" fade-out (last 10s)
        if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
            if (remainingMs in 0..10000L) {
                val fade = (remainingMs / 10000f).coerceIn(0f, 1f)
                setMasterPlaybackVolume(fade)
                return
            }
        }

        // Check Crossfade ramp down
        if (crossfadeSeconds > 0) {
            val crossfadeMs = crossfadeSeconds * 1000L
            if (remainingMs in 0..crossfadeMs) {
                val fade = (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0f, 1f)
                setMasterPlaybackVolume(fade)
            }
        }
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        sleepTimerJob?.cancel()
        setMasterPlaybackVolume(1f)
        _sleepTimerMode.value = mode

        when (mode) {
            SleepTimerMode.OFF -> {
                _sleepTimerRemainingSeconds.value = null
            }
            SleepTimerMode.END_OF_TRACK -> {
                _sleepTimerRemainingSeconds.value = null
            }
            else -> {
                val totalSeconds = mode.minutes * 60L
                sleepTimerJob = scope.launch {
                    var remaining = totalSeconds
                    while (remaining > 0 && isActive) {
                        _sleepTimerRemainingSeconds.value = remaining
                        delay(1000)
                        remaining--
                        // Smooth 10s fade-out before stopping
                        if (remaining in 1..10) {
                            val fadeFraction = remaining / 10f
                            setMasterPlaybackVolume(fadeFraction)
                        }
                    }
                    // Stop playback and restore baseline volume
                    setMasterPlaybackVolume(0f)
                    pause()
                    setMasterPlaybackVolume(1f)
                    _sleepTimerMode.value = SleepTimerMode.OFF
                    _sleepTimerRemainingSeconds.value = null
                }
            }
        }
    }

    private fun startLocalProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val isLocalOrOffline = _state.value.currentTrack?.sourceType == AudioSourceType.LOCAL || isPlayingOffline
                if (isLocalOrOffline && exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _playbackPositionMs.value = pos
                    val frac = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                    _playbackProgress.value = frac
                    _state.update { it.copy(currentPositionMs = pos) }

                    checkVolumeTransitions(pos, dur)
                }
                delay(250)
            }
        }
    }

    private fun stopLocalProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun attachLoudnessEnhancer(sessionId: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            if (sessionId != C.AUDIO_SESSION_ID_UNSET && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                val enhancer = android.media.audiofx.LoudnessEnhancer(sessionId)
                enhancer.setTargetGain(800) // 800 mB normalization target boost
                enhancer.enabled = isLoudnessEnhancerEnabled
                loudnessEnhancer = enhancer
                Log.d("MusicPlayerManager", "Attached LoudnessEnhancer to audioSessionId: $sessionId")
            }
        } catch (e: Exception) {
            Log.w("MusicPlayerManager", "Could not attach LoudnessEnhancer", e)
        }
    }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        isLoudnessEnhancerEnabled = enabled
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            Log.w("MusicPlayerManager", "Error updating LoudnessEnhancer state", e)
        }
    }

    fun release() {
        sleepTimerJob?.cancel()
        fadeInJob?.cancel()
        listeningTrackerJob?.cancel()
        bufferingWatchdogJob?.cancel()
        stopLocalProgressTracker()
        equalizerManager.release()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        if (activeInstance == this) activeInstance = null
        if (activeMediaLibrarySession == mediaLibrarySession) activeMediaLibrarySession = null
        if (activeMediaSession == mediaSession) activeMediaSession = null
        mediaLibrarySession.release()
        exoPlayer.release()
    }
}
