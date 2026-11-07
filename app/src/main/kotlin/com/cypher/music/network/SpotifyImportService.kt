package com.cypher.music.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SpotifyTrackRef(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val spotifyUri: String?
)

data class SpotifyPlaylistRef(
    val id: String,
    val name: String,
    val owner: String? = null,
    val coverUrl: String? = null,
    val tracks: List<SpotifyTrackRef> = emptyList()
)

/**
 * Reads a public Spotify playlist without requiring a Spotify account.
 *
 * It fetches the public embed page and parses the `__NEXT_DATA__` payload that Spotify
 * ships for it, so users coming from Spotify can paste their playlist link and keep
 * listening. Private playlists (and Liked Songs) are not exposed publicly and need an
 * OAuth session, which this credential-free path deliberately does not fake.
 */
object SpotifyImportService {
    private const val TAG = "SpotifyImport"
    const val IMPORT_DESCRIPTION = "Importada desde Spotify"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val PLAYLIST_PATH = Regex("(?:playlist[:/])([A-Za-z0-9]{16,40})")
    private val RAW_ID = Regex("^[A-Za-z0-9]{22}$")

    /** Accepts share links, `spotify:playlist:` URIs and raw playlist ids. */
    fun parsePlaylistId(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        val fromPath = PLAYLIST_PATH.find(value)?.groupValues?.getOrNull(1)
            ?.substringBefore("?")
            ?.substringBefore("&")
            ?.trim()
        if (!fromPath.isNullOrBlank()) return fromPath
        return value.takeIf { RAW_ID.matches(it) }
    }

    suspend fun fetchPlaylist(input: String): Result<SpotifyPlaylistRef> = withContext(Dispatchers.IO) {
        val id = parsePlaylistId(input)
            ?: return@withContext Result.failure(
                IllegalArgumentException(
                    "Pega un enlace de playlist de Spotify (open.spotify.com/playlist/…) o su URI."
                )
            )

        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/playlist/$id")
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure<SpotifyPlaylistRef>(
                        IllegalStateException(
                            "Spotify respondió ${response.code}. Comprueba que la playlist sea pública."
                        )
                    )
                }
                val html = response.body?.string().orEmpty()
                val playlist = parseEmbed(html, id)
                if (playlist == null || playlist.tracks.isEmpty()) {
                    Result.failure(
                        IllegalStateException(
                            "No se pudieron leer las canciones. La playlist debe ser pública y tener al menos una canción."
                        )
                    )
                } else {
                    Result.success(playlist)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPlaylist failed: ${e.message}")
            Result.failure(IllegalStateException("No se pudo conectar con Spotify: ${e.message}", e))
        }
    }

    /** Pure parser so the embed payload can be unit-tested without network access. */
    internal fun parseEmbed(html: String, fallbackId: String): SpotifyPlaylistRef? {
        val markerIndex = html.indexOf("id=\"__NEXT_DATA__\"")
        if (markerIndex < 0) return null
        val jsonStart = html.indexOf('>', markerIndex).takeIf { it > markerIndex }?.plus(1) ?: return null
        val jsonEnd = html.indexOf("</script>", jsonStart).takeIf { it > jsonStart } ?: return null

        return try {
            val root = JSONObject(html.substring(jsonStart, jsonEnd))
            val entity = root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity") ?: return null

            val trackList = entity.optJSONArray("trackList") ?: return null
            val tracks = buildList {
                for (i in 0 until trackList.length()) {
                    val item = trackList.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    if (title.isEmpty()) continue
                    add(
                        SpotifyTrackRef(
                            title = title,
                            artist = item.optString("subtitle").trim()
                                .ifEmpty { "Artista desconocido" },
                            durationMs = item.optLong("duration", 0L),
                            spotifyUri = item.optString("uri").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val sources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            SpotifyPlaylistRef(
                id = fallbackId,
                name = entity.optString("name").ifBlank { entity.optString("title") }
                    .ifBlank { "Playlist de Spotify" },
                owner = entity.optString("subtitle").trim()
                    .takeIf { it.isNotBlank() && !it.equals("Spotify", ignoreCase = true) },
                coverUrl = sources?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() },
                tracks = tracks
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseEmbed failed: ${e.message}")
            null
        }
    }
}
