package com.cypher.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Lyrics for songs that do not ship with an `.lrc` file, which is most of them once the
 * library comes from YouTube Music.
 *
 * Provider: LRCLIB (`lrclib.net`), a free, open, key-less database that returns LRC text
 * with `[mm:ss.xx]` timestamps. No login, no token, no scraping, and the client identifies
 * itself as the API asks.
 *
 * Only real lyrics are shown: if the provider has nothing synced, the result is NOT_FOUND
 * instead of invented lines. Whatever comes back is cached on disk, so the second visit is
 * instant and works offline.
 */
object LyricsProvider {

    enum class Status { LOADING, FOUND, NOT_FOUND, OFFLINE }

    data class Result(val lines: List<LyricLine>, val status: Status)

    private val memory = HashMap<String, Result>()
    private val lock = Any()

    private val cacheDir: File
        get() = File(Storage.dataDir.toFile(), "lyrics-cache").also { it.mkdirs() }

    private fun key(title: String, artist: String) =
        ("${title.trim().lowercase()}\u0000${artist.trim().lowercase()}")

    private fun file(title: String, artist: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(key(title, artist).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.lrc")
    }

    fun cached(title: String?, artist: String?): List<LyricLine>? {
        if (title.isNullOrBlank()) return null
        val cachedFile = file(title, artist.orEmpty())
        if (!cachedFile.isFile) return null
        val text = runCatching { cachedFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return toLines(text)
    }

    /**
     * LRC when the provider has timings, plain text otherwise. Plain lines carry -1 so the
     * UI knows not to highlight anything: the text is real, the timing is not invented.
     */
    private fun toLines(text: String): List<LyricLine>? {
        if (text.isBlank()) return null
        val synced = Lyrics.fromText(text)
        if (synced.isNotEmpty()) return synced
        val plain = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (plain.isEmpty()) return null
        return plain.map { LyricLine(-1, it) }
    }

    /** Reads the disk cache or asks LRCLIB once. */
    suspend fun load(title: String?, artist: String?, album: String?, durationMs: Long): Result {
        if (title.isNullOrBlank()) return Result(emptyList(), Status.NOT_FOUND)
        val cacheKey = key(title, artist.orEmpty())
        synchronized(lock) { memory[cacheKey]?.let { return it } }

        cached(title, artist)?.let { lines ->
            return Result(lines, Status.FOUND).also { synchronized(lock) { memory[cacheKey] = it } }
        }

        val result = withContext(Dispatchers.IO) { remote(title, artist, album, durationMs) }
        if (result.status == Status.FOUND || result.status == Status.NOT_FOUND) {
            synchronized(lock) { memory[cacheKey] = result }
        }
        return result
    }

    /**
     * Three attempts, because the exact endpoint is strict about duration: a library that
     * says 3:21 for a track the database lists as 3:04 would never match.
     */
    private fun remote(title: String, artist: String?, album: String?, durationMs: Long): Result {
        var offline = false
        for (attempt in listOf("full", "no-duration", "search")) {
            val body = when (attempt) {
                "search" -> request(searchUrl(title, artist.orEmpty()))
                else -> request(getUrl(title, artist, album, if (attempt == "full") durationMs else 0))
            }
            when (body) {
                null -> offline = true
                NOT_FOUND -> Unit
                else -> {
                    val record = if (attempt == "search") firstRecord(body) else body
                    parse(record)?.let { text ->
                        val lines = toLines(text)
                        if (lines != null) {
                            store(title, artist, text)
                            return Result(lines, Status.FOUND)
                        }
                    }
                }
            }
        }
        return Result(emptyList(), if (offline) Status.OFFLINE else Status.NOT_FOUND)
    }

    private val NOT_FOUND = "{}"

    private fun getUrl(title: String, artist: String?, album: String?, durationMs: Long): String {
        val params = buildList {
            add("track_name=" + encode(title))
            add("artist_name=" + encode(artist.orEmpty()))
            if (!album.isNullOrBlank()) add("album_name=" + encode(album))
            if (durationMs > 0) add("duration=" + (durationMs / 1000))
        }.joinToString("&")
        return "https://lrclib.net/api/get?$params"
    }

    private fun searchUrl(title: String, artist: String): String =
        "https://lrclib.net/api/search?" + encode("$title $artist").let { "q=$it" }

    /** Returns the JSON body, NOT_FOUND for a 404, or null when the network failed. */
    private fun request(url: String): String? {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "CypherDesktop/1.0 (https://cypher.local)")
        connection.setRequestProperty("Accept", "application/json")
        return try {
            when (val code = connection.responseCode) {
                404 -> NOT_FOUND
                in 200..299 -> connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                else -> null
            }
        } catch (error: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** `/api/search` answers with an array; the first usable record is the match. */
    private fun firstRecord(body: String): String {
        val start = body.indexOf('{').takeIf { it >= 0 } ?: return NOT_FOUND
        var depth = 0
        for (index in start until body.length) {
            when (body[index]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return body.substring(start, index + 1) }
            }
        }
        return NOT_FOUND
    }

    /** Synced lines when available, plain text otherwise. */
    private fun parse(record: String): String? {
        jsonString(record, "syncedLyrics")?.takeIf { it.isNotBlank() }?.let { return it }
        return jsonString(record, "plainLyrics")?.takeIf { it.isNotBlank() }
    }

    /** Same directory, atomic write: a dropped connection never leaves a broken file. */
    private fun store(title: String, artist: String?, text: String) {
        runCatching {
            val target = file(title, artist.orEmpty())
            val temp = File(target.parentFile, target.name + ".tmp")
            Files.write(temp.toPath(), text.toByteArray(Charsets.UTF_8))
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Minimal JSON reader: enough for the one field we need, without a parser dependency. */
    private fun jsonString(body: String, field: String): String? {
        val marker = "\"$field\""
        val start = body.indexOf(marker)
        if (start < 0) return null
        val colon = body.indexOf(':', start + marker.length)
        if (colon < 0) return null
        val quote = body.indexOf('"', colon + 1)
        if (quote < 0) return null
        if (body.substring(colon + 1, quote).contains("null")) return null
        val out = StringBuilder()
        var index = quote + 1
        while (index < body.length) {
            val char = body[index]
            when {
                char == '\\' && index + 1 < body.length -> {
                    when (val next = body[index + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        'u' -> {
                            val hex = body.substring(index + 2, index + 6)
                            out.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> out.append(next)
                    }
                    index += 2
                }
                char == '"' -> return out.toString()
                else -> { out.append(char); index++ }
            }
        }
        return null
    }
}
