package com.cypher.music.network

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import org.json.JSONObject

data class SpotifyExportPlaylist(
    val name: String,
    val tracks: List<SpotifyTrackRef>
)

data class SpotifyZipImport(
    val playlists: List<SpotifyExportPlaylist> = emptyList(),
    val issues: List<String> = emptyList(),
    val duplicates: List<String> = emptyList()
)

/**
 * Reads the free ways of getting a Spotify library out of Spotify:
 * - a CSV exported with Exportify (one file per playlist),
 * - the JSON from Spotify's "Download your data" (all playlists in one file),
 * - a ZIP that contains any number of those files.
 *
 * None of them needs a Spotify account, Premium, a Client ID or the Web API, so the
 * transfer works for users who cannot connect an app.
 */
object SpotifyExportParser {
    private const val MAX_ENTRY_CHARS = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 2_000
    private const val MAX_PLAYLISTS = 1_000
    private const val MAX_TOTAL_TRACKS = 100_000

    fun parse(content: String, fallbackName: String): List<SpotifyExportPlaylist> {
        val trimmed = content.trimStart('\uFEFF', ' ', '\t', '\n', '\r')
        if (trimmed.isBlank()) return emptyList()

        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            if (trimmed.startsWith("[")) {
                parseJsonArray(trimmed)
            } else {
                parseJsonObject(trimmed)
            }
        } else {
            val tracks = parseCsv(trimmed)
            if (tracks.isEmpty()) {
                emptyList()
            } else {
                listOf(SpotifyExportPlaylist(fallbackName.ifBlank { "Playlist de Spotify" }, tracks))
            }
        }
    }

    /**
     * Streams every CSV/JSON entry of a ZIP. A broken entry is reported in [SpotifyZipImport.issues]
     * and the rest keeps importing; playlists with the same name (ignoring case and spacing) are
     * skipped and listed in [SpotifyZipImport.duplicates].
     */
    fun parseZip(input: InputStream, sourceName: String = "ZIP"): SpotifyZipImport {
        val playlists = mutableListOf<SpotifyExportPlaylist>()
        val issues = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val seenNames = mutableSetOf<String>()
        var totalTracks = 0
        var entries = 0

        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue

                    entries++
                    if (entries > MAX_ENTRIES) {
                        issues += "El $sourceName supera las $MAX_ENTRIES entradas; se omitió el resto."
                        break
                    }

                    val path = entry.name
                    val baseName = path.substringAfterLast('/').substringAfterLast('\\')
                    if (path.contains("__MACOSX/") || baseName.startsWith(".")) continue

                    val lower = baseName.lowercase()
                    val isCsv = lower.endsWith(".csv")
                    val isJson = lower.endsWith(".json")
                    if (!isCsv && !isJson) continue

                    val text = readEntry(zip, MAX_ENTRY_CHARS)
                    if (text == null) {
                        issues += "«$baseName»: supera el tamaño máximo de 8 MB."
                        continue
                    }
                    if (text.isBlank()) {
                        if (isCsv) issues += "«$baseName»: el archivo está vacío."
                        continue
                    }

                    val parsed = if (isCsv) {
                        val name = baseName.substringBeforeLast('.').ifBlank { "Playlist de Spotify" }
                        val tracks = parseCsv(text.trimStart('\uFEFF'))
                        if (tracks.isEmpty()) emptyList() else listOf(SpotifyExportPlaylist(name, tracks))
                    } else {
                        // Spotify's own export includes metadata JSON files; ignore those silently.
                        parse(text, baseName.substringBeforeLast('.'))
                    }

                    if (parsed.isEmpty()) {
                        if (isCsv) issues += "«$baseName»: no tiene canciones reconocibles."
                        continue
                    }

                    for (playlist in parsed) {
                        val key = normalizeName(playlist.name)
                        if (!seenNames.add(key)) {
                            duplicates += playlist.name
                            continue
                        }
                        if (playlists.size >= MAX_PLAYLISTS) {
                            issues += "Se alcanzó el límite de $MAX_PLAYLISTS playlists; el resto se omitió."
                            break
                        }
                        val remaining = MAX_TOTAL_TRACKS - totalTracks
                        if (remaining <= 0) {
                            issues += "Se alcanzó el límite de $MAX_TOTAL_TRACKS canciones; el resto se omitió."
                            break
                        }
                        val tracks = if (playlist.tracks.size > remaining) {
                            issues += "«${playlist.name}»: se importaron las primeras $remaining canciones."
                            playlist.tracks.take(remaining)
                        } else {
                            playlist.tracks
                        }
                        playlists += SpotifyExportPlaylist(playlist.name, tracks)
                        totalTracks += tracks.size
                    }
                }
            }
        } catch (e: Exception) {
            issues += "No se pudo leer el $sourceName: ${e.message ?: "archivo dañado"}."
        }

        if (playlists.isEmpty() && issues.isEmpty()) {
            issues += "No se encontró ninguna playlist dentro del $sourceName."
        }

        return SpotifyZipImport(playlists, issues, duplicates)
    }

    internal fun normalizeName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun readEntry(zip: ZipInputStream, limit: Int): String? {
        val reader = InputStreamReader(zip, Charsets.UTF_8)
        val buffer = CharArray(8192)
        val builder = StringBuilder()
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            builder.append(buffer, 0, read)
            if (builder.length > limit) return null
        }
        return builder.toString()
    }

    internal fun parseCsv(content: String): List<SpotifyTrackRef> {
        val rows = content.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .map { splitCsvLine(it) }
            .toList()
        if (rows.isEmpty()) return emptyList()

        val headerIndex = rows.indexOfFirst { row ->
            row.any { it.equals("Track Name", ignoreCase = true) }
        }
        if (headerIndex < 0) return emptyList()

        val header = rows[headerIndex]
        fun column(vararg names: String): Int = header.indexOfFirst { cell ->
            names.any { it.equals(cell.trim(), ignoreCase = true) }
        }

        val titleColumn = column("Track Name")
        val artistColumn = column("Artist Name(s)", "Artist Name", "Artist")
        val durationColumn = column("Track Duration (ms)", "Duration (ms)")
        val uriColumn = column("Track URI")

        return buildList {
            for (row in rows.drop(headerIndex + 1)) {
                val title = row.getOrNull(titleColumn)?.trim().orEmpty()
                if (title.isEmpty()) continue
                val artist = row.getOrNull(artistColumn)
                    ?.replace(";", ",")
                    ?.trim()
                    ?.trim(',')
                    ?.trim()
                    ?.ifBlank { null }
                    ?: "Artista desconocido"
                add(
                    SpotifyTrackRef(
                        title = title,
                        artist = artist,
                        durationMs = row.getOrNull(durationColumn)
                            ?.trim()?.toLongOrNull() ?: 0L,
                        spotifyUri = row.getOrNull(uriColumn)?.trim()
                            ?.takeIf { it.startsWith("spotify:") }
                    )
                )
            }
        }
    }

    private fun parseJsonObject(content: String): List<SpotifyExportPlaylist> {
        val root = try {
            JSONObject(content)
        } catch (e: Exception) {
            return emptyList()
        }

        val playlists = root.optJSONArray("playlists")
        if (playlists != null) {
            return buildList {
                for (index in 0 until playlists.length()) {
                    playlists.optJSONObject(index)?.let { parsePlaylist(it)?.let(::add) }
                }
            }
        }

        val single = parsePlaylist(root) ?: return emptyList()
        return listOf(single)
    }

    private fun parseJsonArray(content: String): List<SpotifyExportPlaylist> {
        val array = try {
            org.json.JSONArray(content)
        } catch (e: Exception) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { parsePlaylist(it)?.let(::add) }
            }
        }
    }

    private fun parsePlaylist(obj: JSONObject): SpotifyExportPlaylist? {
        val name = obj.optString("name").trim().ifBlank { "Playlist de Spotify" }
        val items = obj.optJSONArray("items") ?: obj.optJSONArray("tracks") ?: return null

        val tracks = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val track = item.optJSONObject("track") ?: item
                val title = track.optString("trackName").trim()
                    .ifBlank { track.optString("name").trim() }
                if (title.isEmpty()) continue

                val artist = track.optString("artistName").trim().ifBlank {
                    val artists = track.optJSONArray("artists")
                    if (artists == null) {
                        ""
                    } else {
                        buildList {
                            for (i in 0 until artists.length()) {
                                artists.optJSONObject(i)?.optString("name")
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }.joinToString(", ")
                    }
                }.ifBlank { "Artista desconocido" }

                add(
                    SpotifyTrackRef(
                        title = title,
                        artist = artist,
                        durationMs = track.optLong(
                            "duration_ms",
                            track.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L
                        ),
                        spotifyUri = track.optString("trackUri").ifBlank { track.optString("uri") }
                            .takeIf { it.startsWith("spotify:") }
                    )
                )
            }
        }

        return if (tracks.isEmpty()) null else SpotifyExportPlaylist(name, tracks)
    }

    /** RFC 4180-ish splitter: handles quoted commas and doubled quotes. */
    internal fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells += current.toString()
        return cells
    }
}
