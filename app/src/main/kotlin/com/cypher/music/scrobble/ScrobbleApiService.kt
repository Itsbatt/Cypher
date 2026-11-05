package com.cypher.music.scrobble

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface ScrobbleApiService {
    suspend fun updateNowPlayingLastFm(payload: ScrobbleTrackPayload, config: ScrobbleConfig): Result<Unit>
    suspend fun scrobbleTrackLastFm(payload: ScrobbleTrackPayload, config: ScrobbleConfig): Result<Unit>
    suspend fun updateNowPlayingListenBrainz(payload: ScrobbleTrackPayload, config: ScrobbleConfig): Result<Unit>
    suspend fun scrobbleTrackListenBrainz(payload: ScrobbleTrackPayload, config: ScrobbleConfig): Result<Unit>
}

/**
 * Production-ready Scrobbling API client handling Last.fm and ListenBrainz protocols.
 * Executed silently in the background on Dispatchers.IO.
 */
class DefaultScrobbleApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : ScrobbleApiService {

    companion object {
        private const val TAG = "ScrobbleApiService"
        private const val LAST_FM_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
        private const val LISTENBRAINZ_ENDPOINT = "https://api.listenbrainz.org/1/submit-listens"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun updateNowPlayingLastFm(
        payload: ScrobbleTrackPayload,
        config: ScrobbleConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (config.lastFmSessionKey.isBlank() && config.lastFmApiKey.isBlank()) {
            return@withContext Result.success(Unit)
        }
        try {
            val params = sortedMapOf(
                "method" to "track.updateNowPlaying",
                "artist" to payload.artist,
                "track" to payload.title,
                "album" to payload.album,
                "api_key" to config.lastFmApiKey,
                "sk" to config.lastFmSessionKey
            )
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) ->
                if (v.isNotBlank()) formBuilder.add(k, v)
            }
            if (config.lastFmSecret.isNotBlank()) {
                val sig = generateLastFmSignature(params, config.lastFmSecret)
                formBuilder.add("api_sig", sig)
            }
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(LAST_FM_ENDPOINT)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Last.fm updateNowPlaying returned HTTP ${response.code}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Last.fm updateNowPlaying failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun scrobbleTrackLastFm(
        payload: ScrobbleTrackPayload,
        config: ScrobbleConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (config.lastFmSessionKey.isBlank() && config.lastFmApiKey.isBlank()) {
            return@withContext Result.success(Unit)
        }
        try {
            val params = sortedMapOf(
                "method" to "track.scrobble",
                "artist" to payload.artist,
                "track" to payload.title,
                "album" to payload.album,
                "timestamp" to payload.timestampSeconds.toString(),
                "api_key" to config.lastFmApiKey,
                "sk" to config.lastFmSessionKey
            )
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) ->
                if (v.isNotBlank()) formBuilder.add(k, v)
            }
            if (config.lastFmSecret.isNotBlank()) {
                val sig = generateLastFmSignature(params, config.lastFmSecret)
                formBuilder.add("api_sig", sig)
            }
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(LAST_FM_ENDPOINT)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Last.fm scrobble sent: '${payload.title}' (HTTP ${response.code})")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Last.fm scrobble failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateNowPlayingListenBrainz(
        payload: ScrobbleTrackPayload,
        config: ScrobbleConfig
    ): Result<Unit> = sendListenBrainz(payload, config, isPlayingNow = true)

    override suspend fun scrobbleTrackListenBrainz(
        payload: ScrobbleTrackPayload,
        config: ScrobbleConfig
    ): Result<Unit> = sendListenBrainz(payload, config, isPlayingNow = false)

    private suspend fun sendListenBrainz(
        payload: ScrobbleTrackPayload,
        config: ScrobbleConfig,
        isPlayingNow: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (config.listenBrainzUserToken.isBlank()) {
            return@withContext Result.success(Unit)
        }
        try {
            val root = JSONObject()
            root.put("listen_type", if (isPlayingNow) "playing_now" else "single")

            val trackMetadata = JSONObject().apply {
                put("artist_name", payload.artist)
                put("track_name", payload.title)
                if (payload.album.isNotBlank()) put("release_name", payload.album)
                val additionalInfo = JSONObject().apply {
                    put("media_player", "Cypher")
                    put("submission_client", "Cypher Music")
                    if (payload.durationMs > 0) put("duration_ms", payload.durationMs)
                }
                put("additional_info", additionalInfo)
            }

            val listenItem = JSONObject().apply {
                if (!isPlayingNow) {
                    put("listened_at", payload.timestampSeconds)
                }
                put("track_metadata", trackMetadata)
            }

            val payloadArray = JSONArray().apply { put(listenItem) }
            root.put("payload", payloadArray)

            val body = root.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(LISTENBRAINZ_ENDPOINT)
                .header("Authorization", "Token ${config.listenBrainzUserToken}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "ListenBrainz submission (playingNow=$isPlayingNow): HTTP ${response.code}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "ListenBrainz submission failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun generateLastFmSignature(params: Map<String, String>, secret: String): String {
        val buffer = StringBuilder()
        params.forEach { (k, v) ->
            if (v.isNotBlank()) {
                buffer.append(k).append(v)
            }
        }
        buffer.append(secret)
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(buffer.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
