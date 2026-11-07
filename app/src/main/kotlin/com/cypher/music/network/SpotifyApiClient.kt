package com.cypher.music.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val ownerName: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val isLikedSongs: Boolean = false
)

/**
 * Spotify Web API reads used by the transfer. Everything is paged until Spotify returns
 * no `next` page, so a playlist with thousands of songs is read completely instead of
 * stopping at the first 50/100.
 */
class SpotifyApiClient(private val auth: SpotifyAuthManager) {

    class SpotifyApiException(val status: Int, message: String) : Exception(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPlaylists(): Result<List<SpotifyPlaylistSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val playlists = mutableListOf<SpotifyPlaylistSummary>()
            var offset = 0
            while (true) {
                val page = getJson("${SpotifyAuth.API_BASE}/me/playlists?limit=50&offset=$offset")
                val items = page.optJSONArray("items") ?: break
                playlists += parsePlaylists(page)
                if (page.isNull("next") || items.length() == 0) break
                offset += items.length()
            }
            playlists
        }
    }

    suspend fun fetchLikedSongsTotal(): Int = withContext(Dispatchers.IO) {
        runCatching {
            getJson("${SpotifyAuth.API_BASE}/me/tracks?limit=1").optInt("total", 0)
        }.getOrDefault(0)
    }

    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<SpotifyTrackRef>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tracks = mutableListOf<SpotifyTrackRef>()
                var offset = 0
                while (true) {
                    val page = try {
                        getJson(
                            "${SpotifyAuth.API_BASE}/playlists/$playlistId/items" +
                                "?limit=100&offset=$offset&additional_types=track"
                        )
                    } catch (e: SpotifyApiException) {
                        // Older deployments still expose /tracks; keep both paths working.
                        if (e.status == 404 || e.status == 400) {
                            getJson(
                                "${SpotifyAuth.API_BASE}/playlists/$playlistId/tracks" +
                                    "?limit=100&offset=$offset"
                            )
                        } else {
                            throw e
                        }
                    }
                    val items = page.optJSONArray("items") ?: break
                    tracks += parseTracks(page)
                    if (page.isNull("next") || items.length() == 0) break
                    offset += items.length()
                }
                tracks
            }
        }

    suspend fun fetchSavedTracks(): Result<List<SpotifyTrackRef>> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = mutableListOf<SpotifyTrackRef>()
            var offset = 0
            while (true) {
                val page = getJson("${SpotifyAuth.API_BASE}/me/tracks?limit=50&offset=$offset")
                val items = page.optJSONArray("items") ?: break
                tracks += parseSavedTracks(page)
                if (page.isNull("next") || items.length() == 0) break
                offset += items.length()
            }
            tracks
        }
    }

    private fun getJson(url: String): JSONObject {
        repeat(4) { attempt ->
            val token = auth.accessToken()
                ?: throw SpotifyApiException(401, "La sesión de Spotify ha caducado. Vuelve a conectar la cuenta.")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> return JSONObject(body)

                    response.code == 401 -> auth.accessToken(forceRefresh = true)

                    response.code == 429 -> {
                        val seconds = response.header("Retry-After")?.toLongOrNull()
                            ?.coerceIn(1L, 15L) ?: 3L
                        Log.w("SpotifyApi", "Rate limited, waiting ${seconds}s")
                        Thread.sleep(seconds * 1000L)
                    }

                    response.code in 500..599 -> Thread.sleep(700L * (attempt + 1))

                    else -> {
                        val message = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull()
                        throw SpotifyApiException(
                            response.code,
                            message?.takeIf { it.isNotBlank() }
                                ?: "Spotify respondió ${response.code}"
                        )
                    }
                }
            }
        }
        throw SpotifyApiException(503, "Spotify no responde después de varios intentos.")
    }

    companion object {
        internal fun parsePlaylists(root: JSONObject): List<SpotifyPlaylistSummary> {
            val items = root.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val images = item.optJSONArray("images")
                    add(
                        SpotifyPlaylistSummary(
                            id = id,
                            name = item.optString("name").ifBlank { "Playlist de Spotify" },
                            ownerName = item.optJSONObject("owner")?.optString("display_name")
                                ?.takeIf { it.isNotBlank() },
                            coverUrl = images?.optJSONObject(0)?.optString("url")
                                ?.takeIf { it.isNotBlank() },
                            trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                        )
                    )
                }
            }
        }

        internal fun parseTracks(root: JSONObject): List<SpotifyTrackRef> {
            val items = root.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (index in 0 until items.length()) {
                    val wrapper = items.optJSONObject(index) ?: continue
                    val track = wrapper.optJSONObject("track") ?: continue
                    addOrSkip(track)
                }
            }
        }

        internal fun parseSavedTracks(root: JSONObject): List<SpotifyTrackRef> {
            val items = root.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (index in 0 until items.length()) {
                    val wrapper = items.optJSONObject(index) ?: continue
                    addOrSkip(wrapper.optJSONObject("track") ?: continue)
                }
            }
        }

        private fun MutableList<SpotifyTrackRef>.addOrSkip(track: JSONObject) {
            if (track.optString("type", "track") != "track") return
            val title = track.optString("name").trim()
            if (title.isEmpty()) return
            val artists = track.optJSONArray("artists")
            val artist = buildList {
                if (artists != null) {
                    for (i in 0 until artists.length()) {
                        artists.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                            ?.let { add(it) }
                    }
                }
            }.joinToString(", ").ifBlank { "Artista desconocido" }

            add(
                SpotifyTrackRef(
                    title = title,
                    artist = artist,
                    durationMs = track.optLong("duration_ms", 0L),
                    spotifyUri = track.optString("uri").takeIf { it.isNotBlank() }
                )
            )
        }
    }
}
