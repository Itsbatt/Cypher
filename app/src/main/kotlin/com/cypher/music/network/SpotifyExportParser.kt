package com.cypher.music.network

import android.util.Log
import org.json.JSONObject

data class SpotifyExportPlaylist(
    val name: String,
    val tracks: List<SpotifyTrackRef>
)

/**
 * Reads the two free ways of getting a Spotify library out of Spotify:
 * - a CSV exported with Exportify (one file per playlist),
 * - the JSON from Spotify's "Download your data" (all playlists in one file).
 *
 * Neither needs a Spotify account, Premium, a Client ID or the Web API, so the
 * transfer works for users who cannot connect an app.
 */
object SpotifyExportParser {
    private const val TAG = "SpotifyExport"

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
            Log.w(TAG, "Not a Spotify JSON export: ${e.message}")
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
            Log.w(TAG, "Invalid JSON array export: ${e.message}")
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
