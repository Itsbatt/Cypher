package com.cypher.music.playback

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.cypher.music.data.Playlist
import com.cypher.music.data.room.CypherDatabase
import com.cypher.music.data.room.toDomain
import com.cypher.music.data.room.toEntity
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class PlaylistDownloadProgress(
    val current: Int,
    val total: Int,
    val currentTrackTitle: String
)

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private const val TAG = "MediaCacheManager"
    private const val MAX_CACHE_SIZE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
    private const val MIN_FREE_BYTES = 80L * 1024 * 1024

    // Anything smaller cannot be a playable container. Kept low so very short
    // tracks are not rejected as invalid downloads.
    private const val MIN_AUDIO_BYTES = 1024L
    private const val DOWNLOAD_QUEUE_PREFS = "cypher_download_queue"
    private const val DOWNLOAD_QUEUE_KEY = "pending"

    // Live reactive state: trackId -> progress (0.0f .. 1.0f). If present, it's currently downloading.
    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    // Live reactive set of downloaded track IDs
    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    // Live reactive playlist batch download progress: playlistId -> PlaylistDownloadProgress
    private val _playlistProgressMap = MutableStateFlow<Map<String, PlaylistDownloadProgress>>(emptyMap())
    val playlistProgressMap: StateFlow<Map<String, PlaylistDownloadProgress>> = _playlistProgressMap.asStateFlow()

    // Failed downloads with a readable reason, so a row never stays "downloading" forever
    // and the user can retry the exact song.
    private val _failedDownloads = MutableStateFlow<Map<String, String>>(emptyMap())
    val failedDownloads: StateFlow<Map<String, String>> = _failedDownloads.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun registerFailure(trackId: String, reason: String) {
        _failedDownloads.update { it + (trackId to reason) }
    }

    fun clearFailure(trackId: String) {
        if (_failedDownloads.value.containsKey(trackId)) {
            _failedDownloads.update { it - trackId }
        }
    }

    /** Retries one failed song in the background; progress flows as usual. */
    fun retryDownload(context: Context, track: Track) {
        if (isTrackDownloading(track.id)) return
        downloadScope.launch {
            downloadTrackOffline(context.applicationContext, track)
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var isYtDlpInitialized = false

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (isYtDlpInitialized) return
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            try {
                FFmpeg.getInstance().init(context.applicationContext)
            } catch (fe: Exception) {
                Log.w(TAG, "FFmpeg init note: ${fe.message}")
            }
            isYtDlpInitialized = true
            Log.i(TAG, "YoutubeDL engine successfully initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing YoutubeDL engine", e)
        }
    }

    /**
     * Synchronizes the reactive set of downloaded IDs with the files on disk and Room DB.
     */
    suspend fun syncDownloadedIds(context: Context) = withContext(Dispatchers.IO) {
        try {
            val vaultDir = getOfflineVaultDir(context)
            val files = vaultDir.listFiles() ?: emptyArray()
            val validIds = mutableSetOf<String>()
            for (f in files) {
                if (f.isFile && f.name != ".nomedia" && hasAudioSignature(f)) {
                    val baseName = f.name.substringBeforeLast(".")
                    validIds.add(baseName)
                    // Also check if original track was prefixed with yt_
                    if (baseName.startsWith("yt_")) {
                        validIds.add(baseName)
                    }
                }
            }
            // Also merge with Room database entries
            val roomDb = CypherDatabase.getInstance(context)
            val entities = roomDb.trackDao().getDownloadedTracks()
            for (e in entities) {
                if (isTrackDownloaded(context, e.id)) {
                    validIds.add(e.id)
                }
            }
            _downloadedTrackIds.value = validIds
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing downloaded IDs", e)
        }
    }

    @Volatile
    private var vaultDirCache: File? = null

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    @Volatile
    private var databaseProviderInstance: StandaloneDatabaseProvider? = null

    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        return simpleCacheInstance ?: synchronized(this) {
            val cacheDir = File(context.cacheDir, "cypher_audio_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val databaseProvider = databaseProviderInstance ?: StandaloneDatabaseProvider(context.applicationContext).also {
                databaseProviderInstance = it
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            SimpleCache(cacheDir, evictor, databaseProvider).also {
                simpleCacheInstance = it
            }
        }
    }

    fun buildCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val cache = getSimpleCache(context)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Cypher/1.0 (Linux; Android)")

        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun buildMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val cacheDataSourceFactory = buildCacheDataSourceFactory(context)
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)
    }

    /**
     * Returns the Scoped Storage offline vault directory.
     * Prioritizes removable external storage (MicroSD card) when available and mounted,
     * otherwise uses app-specific external files dir or fallback internal storage.
     * Automatically ensures a .nomedia file exists to shield downloads from MediaStore.
     * Safely migrates legacy downloads from context.filesDir to the new location.
     */
    fun getOfflineVaultDir(context: Context): File {
        // Resolved once per process: callers include UI code, so this must never walk
        // volumes or migrate files on every lookup.
        vaultDirCache?.let { if (it.exists()) return it }
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        // Prefer removable SD card if mounted and accessible
        val targetBase = externalDirs.firstOrNull { dir ->
            dir != null && Environment.isExternalStorageRemovable(dir) &&
                    Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
        } ?: externalDirs.firstOrNull { it != null } ?: context.filesDir

        val vaultDir = File(targetBase, "offline_vault")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }

        // Privacy protection: ensure .nomedia exists so files are shielded from external scanners
        val noMedia = File(vaultDir, ".nomedia")
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (_: Exception) {}
        }

        // Seamless migration: migrate files from old internal storage if they exist
        val legacyDir = File(context.filesDir, "offline_vault")
        if (legacyDir.exists() && legacyDir.absolutePath != vaultDir.absolutePath) {
            try {
                legacyDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name != ".nomedia") {
                        val target = File(vaultDir, file.name)
                        if (!target.exists() || target.length() == 0L) {
                            file.copyTo(target, overwrite = true)
                        }
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Legacy vault migration notice: ${e.message}")
            }
        }

        vaultDirCache = vaultDir
        return vaultDir
    }

    /**
     * Checks if the user is currently offline or on mobile data.
     */
    fun isOfflineOrMobileData(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return !isWifi
    }

    /**
     * Checks if an offline downloaded audio file exists for the given track ID.
     * Supports high-fidelity uncompressed and compressed formats: FLAC, WAV, M4A, WEBM, OPUS, MP3, AAC, OGG.
     * Inspects primary Scoped Storage vault and fallback external storage volumes.
     */
    fun getOfflineTrackFile(context: Context, trackId: String): File? {
        val safeId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val extensions = listOf("flac", "wav", "m4a", "webm", "opus", "mp3", "aac", "ogg")

        // 1. Primary Scoped Storage Vault
        val primaryDir = getOfflineVaultDir(context)
        for (ext in extensions) {
            val f = File(primaryDir, "$safeId.$ext")
            if (hasAudioSignature(f)) return f
        }

        // 2. Scan across all mounted external volumes & legacy filesDir for resilient lookup
        val allBases = ContextCompat.getExternalFilesDirs(context, null).filterNotNull() + listOf(context.filesDir)
        for (base in allBases) {
            val dir = File(base, "offline_vault")
            if (dir.exists() && dir.absolutePath != primaryDir.absolutePath) {
                for (ext in extensions) {
                    val f = File(dir, "$safeId.$ext")
                    if (hasAudioSignature(f)) return f
                }
            }
        }
        return null
    }

    /**
     * Cheap container sniffing so a saved file is really audio and not an HTML/JSON
     * error page renamed with an audio extension.
     */
    private fun hasAudioSignature(file: File): Boolean {
        if (!file.exists() || file.length() <= MIN_AUDIO_BYTES) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 4) return false
                val ascii = String(header, 0, read, Charsets.ISO_8859_1)
                when {
                    read >= 8 && ascii.regionMatches(4, "ftyp", 0, 4) -> true   // MP4/M4A
                    ascii.startsWith("ID3") -> true                             // MP3
                    ascii.startsWith("fLaC") -> true                            // FLAC
                    ascii.startsWith("OggS") -> true                            // OGG/Opus
                    ascii.startsWith("RIFF") -> true                            // WAV
                    header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() -> true // WebM/EBML
                    header[0] == 0xFF.toByte() && (header[1].toInt() and 0xF6) == 0xF0 -> true // AAC ADTS
                    else -> false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns whether a track has been downloaded and is available for offline playback.
     */
    fun isTrackDownloaded(context: Context, trackId: String): Boolean {
        if (_downloadedTrackIds.value.contains(trackId)) return true
        val file = getOfflineTrackFile(context, trackId)
        val exists = file != null
        if (exists && !_downloadedTrackIds.value.contains(trackId)) {
            _downloadedTrackIds.update { it + trackId }
        }
        return exists
    }

    /**
     * Returns whether a track is currently being downloaded.
     */
    fun isTrackDownloading(trackId: String): Boolean {
        return _downloadProgressMap.value.containsKey(trackId)
    }

    /**
     * Returns the current download progress of a track (0.0f .. 1.0f) or null if not downloading.
     */
    fun getTrackDownloadProgress(trackId: String): Float? {
        return _downloadProgressMap.value[trackId]
    }

    /**
     * Returns all currently downloaded tracks from the database.
     */
    suspend fun getDownloadedTracks(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val roomDb = CypherDatabase.getInstance(context)
        val entities = roomDb.trackDao().getDownloadedTracks()
        val validTracks = mutableListOf<Track>()
        val validIds = mutableSetOf<String>()

        for (entity in entities) {
            val file = getOfflineTrackFile(context, entity.id)
            if (file != null) {
                validTracks.add(entity.toDomain().copy(isDownloaded = true))
                validIds.add(entity.id)
            } else {
                roomDb.trackDao().setDownloaded(entity.id, false, null, System.currentTimeMillis())
            }
        }
        _downloadedTrackIds.value = validIds
        validTracks
    }

    fun hasFreeSpace(context: Context, requiredBytes: Long = MIN_FREE_BYTES): Boolean = runCatching {
        val stats = StatFs(getOfflineVaultDir(context).absolutePath)
        stats.availableBytes >= requiredBytes
    }.getOrDefault(true)

    private fun queuePrefs(context: Context) =
        context.getSharedPreferences(DOWNLOAD_QUEUE_PREFS, Context.MODE_PRIVATE)

    private fun readQueue(context: Context): JSONArray = runCatching {
        JSONArray(queuePrefs(context).getString(DOWNLOAD_QUEUE_KEY, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    private fun writeQueue(context: Context, queue: JSONArray) {
        queuePrefs(context).edit().putString(DOWNLOAD_QUEUE_KEY, queue.toString()).apply()
    }

    /** Tracks whose download was interrupted and will be retried on the next start. */
    fun pendingDownloadIds(context: Context): Set<String> {
        val queue = readQueue(context)
        val ids = HashSet<String>(queue.length())
        for (index in 0 until queue.length()) {
            queue.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(ids::add)
        }
        return ids
    }

    fun enqueueDownload(context: Context, track: Track) {
        val queue = readQueue(context)
        for (index in 0 until queue.length()) {
            if (queue.optJSONObject(index)?.optString("id") == track.id) return
        }
        queue.put(trackToJson(track))
        writeQueue(context, queue)
    }

    fun removeQueuedDownload(context: Context, trackId: String) {
        val queue = readQueue(context)
        val remaining = JSONArray()
        for (index in 0 until queue.length()) {
            val item = queue.optJSONObject(index) ?: continue
            if (item.optString("id") != trackId) remaining.put(item)
        }
        writeQueue(context, remaining)
    }

    /** Retries downloads that were interrupted by a reboot, a crash or a kill. */
    suspend fun resumePendingDownloads(context: Context) = withContext(Dispatchers.IO) {
        val queue = readQueue(context)
        if (queue.length() == 0) return@withContext
        for (index in 0 until queue.length()) {
            val track = trackFromJson(queue.optJSONObject(index) ?: continue) ?: continue
            if (isTrackDownloaded(context, track.id)) {
                removeQueuedDownload(context, track.id)
                continue
            }
            downloadTrackOffline(context, track)
        }
    }

    /**
     * Copies a downloaded track into the shared Music collection so it survives
     * uninstalling the app. Returns true when the file was published.
     */
    suspend fun exportToMediaStore(context: Context, track: Track): Boolean = withContext(Dispatchers.IO) {
        val file = getOfflineTrackFile(context, track.id) ?: return@withContext false
        val resolver = context.contentResolver
        val displayName = "${track.artist} - ${track.title}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(120)
            .ifBlank { track.id }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$displayName.m4a")
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.ALBUM, track.album)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Cypher")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        if (!copied) runCatching { resolver.delete(uri, null, null) }
        copied
    }

    private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("durationMs", track.durationMs)
        put("artworkUrl", track.artworkUrl)
        put("streamUrl", track.streamUrl)
        put("sourceType", track.sourceType.name)
        put("path", track.path)
    }

    private fun trackFromJson(json: JSONObject): Track? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = id,
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            durationMs = json.optLong("durationMs", 0L),
            artworkUrl = json.optString("artworkUrl"),
            streamUrl = json.optString("streamUrl"),
            sourceType = runCatching { AudioSourceType.valueOf(json.optString("sourceType")) }
                .getOrDefault(AudioSourceType.YOUTUBE),
            path = json.optString("path").takeIf { it.isNotBlank() }
        )
    }

    /**
     * Downloads a track for offline playback into the private vault, hidden from MediaStore.
     * Updates reactive progress StateFlows for real-time UI feedback.
     */
    suspend fun downloadTrackOffline(
        context: Context,
        track: Track,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val safeId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        clearFailure(track.id)
        if (!hasFreeSpace(context)) {
            Log.w(TAG, "Not enough free space to download ${track.id}")
            registerFailure(track.id, "No hay espacio libre suficiente")
            return@withContext null
        }
        enqueueDownload(context, track)
        try {
            ensureInitialized(context)

            val existing = getOfflineTrackFile(context, track.id)
            if (existing != null && existing.exists() && existing.length() > MIN_AUDIO_BYTES) {
                val roomDb = CypherDatabase.getInstance(context)
                roomDb.trackDao().insertTrack(track.toEntity(isDownloaded = true, downloadedUri = existing.absolutePath))
                roomDb.trackDao().setDownloaded(track.id, true, existing.absolutePath, System.currentTimeMillis())
                _downloadedTrackIds.update { it + track.id }
                removeQueuedDownload(context, track.id)
                clearFailure(track.id)
                return@withContext existing
            }

            // Register starting download
            _downloadProgressMap.update { it + (track.id to 0.02f) }
            onProgress(0.02f)

            val vaultDir = getOfflineVaultDir(context)
            val isYouTube = track.sourceType == AudioSourceType.YOUTUBE || 
                            track.id.startsWith("yt_") || 
                            track.streamUrl.isEmpty() ||
                            track.streamUrl.contains("youtube.com") ||
                            track.streamUrl.contains("youtu.be")

            if (isYouTube) {
                val cleanVid = track.id.removePrefix("yt_").trim()
                val targetUrl = if (cleanVid.startsWith("http")) cleanVid else "https://www.youtube.com/watch?v=$cleanVid"
                val processId = "dl_${safeId}_${System.currentTimeMillis()}"

                Log.d(TAG, "Starting yt-dlp download for $targetUrl -> safeId: $safeId")

                var downloadSuccess = false

                // TIER 1: yt-dlp with android,ios,tv player clients (bypasses YouTube SABR streaming & 403 Forbidden)
                try {
                    val request = YoutubeDLRequest(targetUrl).apply {
                        addOption("-o", "${vaultDir.absolutePath}/$safeId.%(ext)s")
                        addOption("-f", "140/bestaudio[ext=m4a]/bestaudio/best")
                        addOption("-x")
                        addOption("--audio-format", "m4a")
                        addOption("--extractor-args", "youtube:player_client=android,ios,tv")
                        addOption("--no-playlist")
                        addOption("--no-mtime")
                        addOption("--no-warnings")
                        addOption("--no-update")
                        addOption("--geo-bypass")
                    }

                    val res = YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                        if (progress >= 0f) {
                            val frac = (progress / 100f).coerceIn(0f, 1f)
                            _downloadProgressMap.update { it + (track.id to frac) }
                            onProgress(frac)
                        }
                    }
                    Log.d(TAG, "yt-dlp tier 1 completed with exit code: ${res.exitCode}")
                    downloadSuccess = (getOfflineTrackFile(context, track.id) != null)
                } catch (e: Exception) {
                    Log.w(TAG, "yt-dlp tier 1 error: ${e.message}, attempting tier 2 fallback...")
                }

                // TIER 2: yt-dlp with tv,android player clients and relaxed format
                if (!downloadSuccess) {
                    try {
                        val fallbackReq = YoutubeDLRequest(targetUrl).apply {
                            addOption("-o", "${vaultDir.absolutePath}/$safeId.%(ext)s")
                            addOption("-f", "bestaudio/best")
                            addOption("--extractor-args", "youtube:player_client=tv,android")
                            addOption("--no-playlist")
                            addOption("--no-mtime")
                            addOption("--no-warnings")
                            addOption("--no-update")
                        }
                        val res2 = YoutubeDL.getInstance().execute(fallbackReq, "${processId}_fb") { progress, _, _ ->
                            if (progress >= 0f) {
                                val frac = (progress / 100f).coerceIn(0f, 1f)
                                _downloadProgressMap.update { it + (track.id to frac) }
                                onProgress(frac)
                            }
                        }
                        Log.d(TAG, "yt-dlp tier 2 completed with exit code: ${res2.exitCode}")
                        downloadSuccess = (getOfflineTrackFile(context, track.id) != null)
                    } catch (fbEx: Exception) {
                        Log.w(TAG, "yt-dlp tier 2 error: ${fbEx.message}, attempting tier 3 direct stream extraction...")
                    }
                }

                // TIER 3: Direct stream extraction via yt-dlp -g + OkHttp streaming
                if (!downloadSuccess) {
                    try {
                        val gReq = YoutubeDLRequest(targetUrl).apply {
                            addOption("-f", "140/bestaudio[ext=m4a]/bestaudio/best")
                            addOption("-g")
                            addOption("--extractor-args", "youtube:player_client=android,ios,tv")
                            addOption("--no-playlist")
                            addOption("--no-warnings")
                            addOption("--no-update")
                        }
                        val gRes = YoutubeDL.getInstance().execute(gReq, "${processId}_g")
                        val directStreamUrl = gRes.out.trim().lines().firstOrNull { it.startsWith("http") }
                        if (!directStreamUrl.isNullOrBlank()) {
                            Log.d(TAG, "Tier 3 extracted stream URL: $directStreamUrl, downloading with OkHttp...")
                            val targetFile = File(vaultDir, "$safeId.m4a")
                            val okReq = Request.Builder()
                                .url(directStreamUrl)
                                .header("User-Agent", "com.google.android.youtube/19.29.37 (Linux; U; Android 14; en_US) gzip")
                                .build()
                            httpClient.newCall(okReq).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body
                                    if (body != null) {
                                        val totalBytes = body.contentLength()
                                        var downloadedBytes = 0L
                                        body.byteStream().use { input ->
                                            FileOutputStream(targetFile).use { output ->
                                                val buffer = ByteArray(16384)
                                                var read: Int
                                                while (input.read(buffer).also { read = it } != -1) {
                                                    output.write(buffer, 0, read)
                                                    downloadedBytes += read
                                                    if (totalBytes > 0) {
                                                        val frac = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                                        _downloadProgressMap.update { it + (track.id to frac) }
                                                        onProgress(frac)
                                                    }
                                                }
                                                output.flush()
                                            }
                                        }
                                        downloadSuccess = (targetFile.exists() && targetFile.length() > MIN_AUDIO_BYTES)
                                    }
                                }
                            }
                        }
                    } catch (t3Ex: Exception) {
                        Log.e(TAG, "Tier 3 direct stream download failed: ${t3Ex.message}", t3Ex)
                    }
                }
            } else {
                // Direct HTTP download for non-YouTube tracks
                val targetFile = File(vaultDir, "$safeId.m4a")
                val req = Request.Builder()
                    .url(track.streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                // The response must be closed: without use{} every direct download
                // leaked its socket and file descriptor.
                httpClient.newCall(req).execute().use { response ->
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    val isAudioResponse = contentType.startsWith("audio/") ||
                        contentType.startsWith("application/octet-stream") ||
                        contentType.isEmpty()
                    if (response.isSuccessful && isAudioResponse) {
                        val body = response.body
                        if (body != null) {
                            val totalBytes = body.contentLength()
                            var downloadedBytes = 0L
                            body.byteStream().use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        downloadedBytes += read
                                        if (totalBytes > 0) {
                                            val frac = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                            _downloadProgressMap.update { it + (track.id to frac) }
                                            onProgress(frac)
                                        }
                                    }
                                    output.flush()
                                }
                            }
                        }
                    }
                }
            }

            // Verify downloaded file in the private vault
            val downloadedFile = getOfflineTrackFile(context, track.id)
            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > MIN_AUDIO_BYTES) {
                Log.i(TAG, "Successfully downloaded: ${downloadedFile.name} (${downloadedFile.length()} bytes)")
                val roomDb = CypherDatabase.getInstance(context)
                roomDb.trackDao().insertTrack(track.toEntity(isDownloaded = true, downloadedUri = downloadedFile.absolutePath))
                roomDb.trackDao().setDownloaded(track.id, true, downloadedFile.absolutePath, System.currentTimeMillis())
                _downloadedTrackIds.update { it + track.id }
                removeQueuedDownload(context, track.id)
                clearFailure(track.id)
                downloadedFile
            } else {
                Log.e(TAG, "Download failed or output file is empty/missing for track: ${track.id}")
                // A rejected download used to leave its junk behind for ever.
                for (ext in listOf("flac", "wav", "m4a", "webm", "opus", "mp3", "aac", "ogg")) {
                    val junk = File(vaultDir, "$safeId.$ext")
                    if (junk.exists() && !hasAudioSignature(junk)) junk.delete()
                }
                registerFailure(track.id, "La descarga no produjo un archivo válido")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download track offline", e)
            registerFailure(track.id, e.message ?: "Error de red durante la descarga")
            null
        } finally {
            _downloadProgressMap.update { it - track.id }
        }
    }

    /**
     * Downloads an entire playlist of tracks sequentially into the private offline vault.
     */
    suspend fun downloadPlaylistOffline(
        context: Context,
        playlist: Playlist,
        onProgress: (current: Int, total: Int, currentTrackTitle: String) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val tracksToDownload = playlist.tracks.filter { !isTrackDownloaded(context, it.id) }
        val total = tracksToDownload.size
        if (total == 0) return@withContext 0

        var downloadedCount = 0
        _playlistProgressMap.update { it + (playlist.id to PlaylistDownloadProgress(0, total, "")) }

        for ((index, track) in tracksToDownload.withIndex()) {
            _playlistProgressMap.update { 
                it + (playlist.id to PlaylistDownloadProgress(index + 1, total, track.title)) 
            }
            onProgress(index + 1, total, track.title)
            val file = downloadTrackOffline(context, track)
            if (file != null) {
                downloadedCount++
            }
        }

        _playlistProgressMap.update { it - playlist.id }
        downloadedCount
    }

    /**
     * Deletes all offline downloaded files belonging to a playlist.
     */
    suspend fun deletePlaylistDownloads(context: Context, playlist: Playlist) = withContext(Dispatchers.IO) {
        for (track in playlist.tracks) {
            deleteOfflineTrack(context, track.id)
        }
    }

    /**
     * Checks if all tracks in a playlist are downloaded offline.
     */
    fun isPlaylistFullyDownloaded(context: Context, playlist: Playlist): Boolean {
        if (playlist.tracks.isEmpty()) return false
        return playlist.tracks.all { isTrackDownloaded(context, it.id) }
    }

    /**
     * Removes an offline downloaded track from the device and updates Room.
     */
    suspend fun deleteOfflineTrack(context: Context, trackId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val extensions = listOf("flac", "wav", "m4a", "webm", "opus", "mp3", "aac", "ogg")
            // Lookup checks every volume, so deletion must too; otherwise a download
            // on a secondary volume stayed on disk while the UI said it was removed.
            val dirs = (listOf(getOfflineVaultDir(context)) +
                ContextCompat.getExternalFilesDirs(context, null).filterNotNull()
                    .map { File(it, "offline_vault") })
                .distinctBy { it.absolutePath }
            var deleted = false
            for (dir in dirs) {
                for (ext in extensions) {
                    val f = File(dir, "$safeId.$ext")
                    if (f.exists()) {
                        deleted = f.delete() || deleted
                    }
                }
            }
            val roomDb = CypherDatabase.getInstance(context)
            roomDb.trackDao().setDownloaded(trackId, false, null, System.currentTimeMillis())
            _downloadedTrackIds.update { it - trackId }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
