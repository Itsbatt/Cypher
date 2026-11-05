package com.cypher.music.network

import android.util.Log
import android.util.LruCache
import com.cypher.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class TrackLyrics(
    val trackId: String,
    val isSynced: Boolean,
    val lines: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val instrumental: Boolean = false
)

object LrcLibService {
    private const val TAG = "LrcLibService"
    private const val BASE_URL = "https://lrclib.net/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory cache for up to 60 tracks
    private val memoryCache = LruCache<String, TrackLyrics>(60)

    private val lrcPattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)")

    suspend fun getLyrics(track: Track): TrackLyrics? = withContext(Dispatchers.IO) {
        val cacheKey = "${track.id}_${track.title}_${track.artist}"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val cleanTitle = cleanTrackTitle(track.title)
        val cleanArtist = cleanArtistName(track.artist)
        val durationSec = if (track.durationMs > 0) (track.durationMs / 1000).toInt() else 0

        // 1. Try exact match endpoint
        var lyrics = fetchExactLyrics(cleanTitle, cleanArtist, durationSec)

        // 2. If not found, try search fallback
        if (lyrics == null) {
            lyrics = searchLyricsFallback("$cleanTitle $cleanArtist")
        }

        if (lyrics != null) {
            val trackLyrics = TrackLyrics(
                trackId = track.id,
                isSynced = lyrics.syncedLyrics != null && lyrics.syncedLines.isNotEmpty(),
                lines = lyrics.syncedLines,
                plainLyrics = lyrics.plainLyrics,
                instrumental = lyrics.instrumental
            )
            memoryCache.put(cacheKey, trackLyrics)
            return@withContext trackLyrics
        }

        null
    }

    private fun fetchExactLyrics(title: String, artist: String, durationSec: Int): RawLyricsResponse? {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            var url = "$BASE_URL/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            if (durationSec > 0) {
                url += "&duration=$durationSec"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Cypher-Android/1.0 (https://github.com)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    return parseLyricsJson(body)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching exact lyrics for $title: ${e.message}")
        }
        return null
    }

    private fun searchLyricsFallback(query: String): RawLyricsResponse? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search?q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Cypher-Android/1.0 (https://github.com)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 0) {
                        // Pick first result that has lyrics (preferably synced)
                        for (i in 0 until jsonArray.length().coerceAtMost(3)) {
                            val parsed = parseLyricsJsonObject(jsonArray.getJSONObject(i))
                            if (parsed.syncedLines.isNotEmpty() || !parsed.plainLyrics.isNullOrBlank()) {
                                return parsed
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in searchLyricsFallback for $query: ${e.message}")
        }
        return null
    }

    private fun parseLyricsJson(jsonStr: String): RawLyricsResponse {
        val json = JSONObject(jsonStr)
        return parseLyricsJsonObject(json)
    }

    private fun parseLyricsJsonObject(json: JSONObject): RawLyricsResponse {
        val synced = if (json.has("syncedLyrics") && !json.isNull("syncedLyrics")) json.getString("syncedLyrics") else null
        val plain = if (json.has("plainLyrics") && !json.isNull("plainLyrics")) json.getString("plainLyrics") else null
        val instrumental = json.optBoolean("instrumental", false)

        val parsedLines = if (!synced.isNullOrBlank()) {
            parseLrc(synced)
        } else {
            emptyList()
        }

        return RawLyricsResponse(
            syncedLyrics = synced,
            syncedLines = parsedLines,
            plainLyrics = plain,
            instrumental = instrumental
        )
    }

    fun parseLrc(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val rawLines = lrcText.split("\n")

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matcher = lrcPattern.matcher(trimmed)
            if (matcher.find()) {
                val minStr = matcher.group(1) ?: "0"
                val secStr = matcher.group(2) ?: "0"
                val fracStr = matcher.group(3) ?: "0"
                val text = matcher.group(4)?.trim() ?: ""

                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val ms = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + ms
                lines.add(LyricLine(timeMs = totalMs, text = text))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\[(official|video|audio|lyrics|hd|4k|music video).*?\\]"), "")
            .replace(Regex("(?i)\\((official|video|audio|lyrics|hd|4k|music video|feat\\..*?|ft\\..*?)\\)"), "")
            .replace(Regex("(?i)-(official|video|audio|lyrics|hd|4k|music video).*"), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("(?i) - Topic$"), "")
            .replace(Regex("(?i)VEVO$"), "")
            .replace(Regex("(?i)Official$"), "")
            .replace(Regex("(?i)Records$"), "")
            .trim()
    }

    private data class RawLyricsResponse(
        val syncedLyrics: String?,
        val syncedLines: List<LyricLine>,
        val plainLyrics: String?,
        val instrumental: Boolean
    )
}
