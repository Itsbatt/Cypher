package com.cypher.music.scrobble

import android.content.Context
import android.util.Log
import com.cypher.music.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages silent background scrobbling for Media3 playback.
 * Automatically identifies when a song passes 50% of its duration
 * and dispatches scrobble requests to Last.fm and ListenBrainz.
 */
class ScrobbleManager(
    private val context: Context,
    private val apiService: ScrobbleApiService = DefaultScrobbleApiService(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "ScrobbleManager"
        private const val PREFS_NAME = "cypher_scrobble_prefs"

        @Volatile
        private var instance: ScrobbleManager? = null

        fun getInstance(context: Context): ScrobbleManager {
            return instance ?: synchronized(this) {
                instance ?: ScrobbleManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<ScrobbleConfig> = _config.asStateFlow()

    private var currentTrack: Track? = null
    private var scrobbledTrackId: String? = null
    private var accumulatedPlaybackTimeMs: Long = 0L
    private var lastRecordedPositionMs: Long = 0L

    private fun loadConfig(): ScrobbleConfig {
        return ScrobbleConfig(
            isEnabled = prefs.getBoolean("enabled", true),
            lastFmApiKey = prefs.getString("lastfm_api_key", "4a9f2c79bb8e400d4c57e80a0a9efb92") ?: "",
            lastFmSessionKey = prefs.getString("lastfm_session_key", "") ?: "",
            lastFmSecret = prefs.getString("lastfm_secret", "") ?: "",
            listenBrainzUserToken = prefs.getString("listenbrainz_token", "") ?: ""
        )
    }

    fun updateConfig(newConfig: ScrobbleConfig) {
        _config.value = newConfig
        prefs.edit()
            .putBoolean("enabled", newConfig.isEnabled)
            .putString("lastfm_api_key", newConfig.lastFmApiKey)
            .putString("lastfm_session_key", newConfig.lastFmSessionKey)
            .putString("lastfm_secret", newConfig.lastFmSecret)
            .putString("listenbrainz_token", newConfig.listenBrainzUserToken)
            .apply()
    }

    /**
     * Called when a new track begins playing in Media3 / MusicPlayerManager.
     */
    fun onTrackStarted(track: Track) {
        if (!_config.value.isEnabled) return
        if (currentTrack?.id == track.id) return

        currentTrack = track
        scrobbledTrackId = null
        accumulatedPlaybackTimeMs = 0L
        lastRecordedPositionMs = 0L

        val payload = ScrobbleTrackPayload(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs
        )

        // Silently update Now Playing in background
        scope.launch {
            try {
                apiService.updateNowPlayingLastFm(payload, _config.value)
                apiService.updateNowPlayingListenBrainz(payload, _config.value)
            } catch (e: Exception) {
                Log.w(TAG, "Silent now-playing update exception: ${e.message}")
            }
        }
    }

    /**
     * Called on playback tick / progress updates.
     * Detects when track exceeds 50% duration (or max 240s threshold) with min 30s length.
     */
    fun onPlaybackTick(track: Track, currentPositionMs: Long, durationMs: Long) {
        if (!_config.value.isEnabled) return
        if (scrobbledTrackId == track.id) return // Already scrobbled this track session

        val actualDuration = if (durationMs > 0) durationMs else track.durationMs
        val minDurationMs = _config.value.minDurationSec * 1000L

        // Scrobbling rule: track must be >= 30 seconds
        if (actualDuration < minDurationMs) return

        // 50% threshold or 4 minutes (standard Last.fm/ListenBrainz specification)
        val thresholdMs = minOf(actualDuration / 2, 240_000L)

        // Track accumulated playback time or current position
        if (currentPositionMs > lastRecordedPositionMs) {
            accumulatedPlaybackTimeMs += (currentPositionMs - lastRecordedPositionMs)
        }
        lastRecordedPositionMs = currentPositionMs

        val reachedThreshold = currentPositionMs >= thresholdMs || accumulatedPlaybackTimeMs >= thresholdMs

        if (reachedThreshold) {
            scrobbledTrackId = track.id
            val payload = ScrobbleTrackPayload(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = actualDuration
            )

            // Fire scrobble silently in background
            scope.launch {
                try {
                    apiService.scrobbleTrackLastFm(payload, _config.value)
                    apiService.scrobbleTrackListenBrainz(payload, _config.value)
                    Log.d(TAG, "Scrobbled track successfully: '${track.title}' by '${track.artist}'")
                } catch (e: Exception) {
                    Log.w(TAG, "Silent scrobble error: ${e.message}")
                }
            }
        }
    }

    /**
     * Resets tracking state when playback stops.
     */
    fun onTrackStopped() {
        currentTrack = null
        scrobbledTrackId = null
        accumulatedPlaybackTimeMs = 0L
        lastRecordedPositionMs = 0L
    }
}
