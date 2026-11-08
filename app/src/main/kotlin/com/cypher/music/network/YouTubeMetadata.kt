package com.cypher.music.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Album and genre for YouTube tracks.
 *
 * YouTube Music tracks are published with an auto-generated description that
 * lists the album, and some labels add an explicit genre. We read only what the
 * source actually states: when a field is missing it stays null and Wrapped
 * shows its honest empty state instead of guessing.
 */
object YouTubeMetadata {

    data class Info(val album: String?, val genre: String?)

    private const val TAG = "CypherMetadata"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private val UNKNOWN_ALBUMS = setOf("sencillo", "youtube music", "álbum local", "album local", "single")

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Info>()

    suspend fun resolve(videoId: String): Info? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        cache[videoId]?.let { cached ->
            return@withContext cached.takeIf { it.album != null || it.genre != null }
        }
        val info = runCatching { fetch(videoId) }
            .onFailure { Log.w(TAG, "No se pudo leer metadatos de $videoId: ${it.message}") }
            .getOrNull()
        if (cache.size > 400) cache.clear()
        cache[videoId] = info ?: Info(null, null)
        info?.takeIf { it.album != null || it.genre != null }
    }

    private fun fetch(videoId: String): Info? {
        val request = Request.Builder()
            // m.youtube.com serves the page directly; the desktop URL only redirects here.
            .url("https://m.youtube.com/watch?v=$videoId&hl=es")
            .header("User-Agent", UA)
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .header("Cookie", "CONSENT=YES+1")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null
            // Reading the escaped field directly is more robust than parsing the
            // whole player JSON: the page also embeds scripts and other payloads.
            val match = SHORT_DESCRIPTION.find(html)
            if (match == null) {
                Log.w(TAG, "Sin shortDescription en la página de $videoId (${html.length} B)")
                return null
            }
            return parseDescription(unescape(match.groupValues[1]))
        }
    }

    private val SHORT_DESCRIPTION =
        Regex("\\\"shortDescription\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")

    private fun unescape(raw: String): String {
        if (!raw.contains('\\')) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val code = raw.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) out.append(code.toChar())
                            i += 4
                        }
                    }
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** Pure parser, so the description rules can be reasoned about in isolation. */
    fun parseDescription(description: String?): Info {
        val lines = description
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return Info(null, null)

        var album = lines.firstOrNull {
            it.startsWith("Album:", ignoreCase = true) || it.startsWith("Álbum:", ignoreCase = true)
        }?.substringAfter(':')?.let(::clean)

        val genre = lines.firstOrNull {
            it.startsWith("Genre:", ignoreCase = true) || it.startsWith("Género:", ignoreCase = true)
        }?.substringAfter(':')?.let(::clean)

        if (album == null) {
            // Auto-generated block:
            // Provided to YouTube by <label>
            // <Song> · <Artist>
            // <Album>
            // ℗ <year> <label>
            val provided = lines.indexOfFirst { it.startsWith("Provided to YouTube by", ignoreCase = true) }
            if (provided >= 0) {
                val block = lines.drop(provided + 1).takeWhile { line ->
                    !line.startsWith("℗") &&
                        !line.startsWith("Released on", ignoreCase = true) &&
                        !line.startsWith("Auto-generated", ignoreCase = true)
                }
                val header = block.indexOfFirst { it.contains(" · ") }
                if (header >= 0) {
                    album = block.drop(header + 1)
                        .asSequence()
                        .mapNotNull(::clean)
                        .firstOrNull()
                }
            }
        }

        return Info(album, genre)
    }

    private fun clean(raw: String?): String? {
        val value = raw?.trim()?.trim('"')?.trim() ?: return null
        if (value.isEmpty() || value.length > 120) return null
        if (value.lowercase() in UNKNOWN_ALBUMS) return null
        if (value.startsWith("Auto-generated", ignoreCase = true)) return null
        return value
    }
}
