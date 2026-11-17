package com.cypher.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads the backup the Android app produces and merges it into this library.
 *
 * The mobile side writes a ZIP with `cypher-library.json` inside (playlists, favorites and
 * a few settings). This importer accepts that ZIP, and also a bare JSON, so a file shared
 * from the phone works without renaming it. Everything is merged by id: importing the same
 * file twice does not duplicate playlists, and tracks that already exist are reused.
 */
object BackupImport {

    data class Summary(
        val playlists: Int,
        val tracks: Int,
        val favorites: Int,
        val lyrics: Int = 0,
        val source: String = ""
    ) {
        val isEmpty: Boolean get() = playlists == 0 && tracks == 0 && favorites == 0
    }

    class ImportException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads a ZIP (or a JSON) and returns the merged document plus what was added. */
    fun import(file: File, current: LibraryData): Pair<LibraryData, Summary> {
        if (!file.isFile) throw ImportException("El archivo no existe")
        var lyricsRaw: String? = null
        val raw = if (file.name.endsWith(".zip", ignoreCase = true)) {
            val entries = readFromZip(file)
            lyricsRaw = entries.second
            entries.first
        } else {
            file.readText(Charsets.UTF_8)
        }
        if (raw.isBlank()) throw ImportException("El archivo está vacío")
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: throw ImportException("No es un respaldo de Cypher")

        val playlistsJson = (root["playlists"] as? JsonArray) ?: JsonArray(emptyList())
        val favoriteIds = (root["favorites"] as? JsonArray)?.mapNotNull { it.stringOrNull() }.orEmpty()
        if (lyricsRaw == null) {
            lyricsRaw = (root["lyrics"] as? JsonArray)?.toString()
        }

        val newTracks = LinkedHashMap<String, Track>()
        val newRemote = LinkedHashMap<String, RemoteTrack>()
        val newPlaylists = LinkedHashMap<String, Playlist>()

        for (element in playlistsJson) {
            val playlist = element as? JsonObject ?: continue
            val id = playlist["id"].stringOrNull() ?: continue
            val name = playlist["name"].stringOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val ids = mutableListOf<String>()

            for (trackElement in (playlist["tracks"] as? JsonArray).orEmpty()) {
                val song = trackElement as? JsonObject ?: continue
                val trackId = song["id"].stringOrNull() ?: continue
                val title = song["title"].stringOrNull()?.takeIf { it.isNotBlank() } ?: continue
                val artist = song["artist"].stringOrNull().orEmpty()
                val album = song["album"].stringOrNull().orEmpty()
                val duration = song["durationMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val artwork = song["artworkUrl"].stringOrNull()?.takeIf { it.isNotBlank() }
                val stream = song["streamUrl"].stringOrNull().orEmpty()
                val sourceType = song["sourceType"].stringOrNull().orEmpty().uppercase()

                val exists = current.track(trackId) != null || current.remoteTrack(trackId) != null
                if (!exists) {
                    if (sourceType.contains("YOUTUBE") || stream.startsWith("http")) {
                        // Remote song: nothing was downloaded, so it stays a reference that
                        // plays through the importer when the user hits play.
                        newRemote[trackId] = RemoteTrack(
                            id = trackId,
                            videoId = song["videoId"].stringOrNull().orEmpty(),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            artworkUrl = artwork,
                            source = "YOUTUBE"
                        )
                    } else {
                        newTracks[trackId] = Track(
                            id = trackId,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            path = stream,
                            artwork = artwork,
                            addedAt = playlist["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                        )
                    }
                }
                ids += trackId
            }

            val existing = current.playlists.firstOrNull { it.id == id }
                ?: current.playlists.firstOrNull { it.name.equals(name, ignoreCase = true) }
            newPlaylists[id] = if (existing != null) {
                // Same playlist: remote order first, then what is only here.
                existing.copy(trackIds = (existing.trackIds + ids).distinct())
            } else {
                Playlist(
                    id = id,
                    name = name,
                    description = playlist["description"].stringOrNull().orEmpty(),
                    trackIds = ids.distinct(),
                    createdAt = playlist["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    updatedAt = playlist["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    artwork = playlist["coverUri"].stringOrNull()?.takeIf { it.isNotBlank() },
                    origin = "YOUTUBE"
                )
            }
        }

        val keptPlaylists = current.playlists.filterNot { existing ->
            newPlaylists.keys.any { it == existing.id }
        }
        val mergedPlaylists = keptPlaylists + newPlaylists.values.toList()

        val incomingLyrics = runCatching {
            val array = json.parseToJsonElement(lyricsRaw ?: "[]") as? JsonArray ?: JsonArray(emptyList())
            array.mapNotNull { element ->
                val doc = element as? JsonObject ?: return@mapNotNull null
                val trackId = doc["trackId"].stringOrNull() ?: return@mapNotNull null
                val lrc = doc["lrc"].stringOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LyricDoc(
                    trackId = trackId,
                    lrc = lrc,
                    source = "IMPORTED",
                    synced = lrc.contains('[') && lrc.contains(']')
                )
            }
        }.getOrDefault(emptyList())
        val mergedLyrics = current.lyrics + incomingLyrics.filterNot { candidate ->
            current.lyrics.any { it.trackId == candidate.trackId }
        }

        val merged = current.copy(
            tracks = current.tracks + newTracks.values.filterNot { candidate -> current.tracks.any { it.id == candidate.id } },
            remoteTracks = current.remoteTracks + newRemote.values.filterNot { candidate -> current.remoteTracks.any { it.id == candidate.id } },
            playlists = mergedPlaylists,
            favorites = (current.favorites + favoriteIds).distinct(),
            lyrics = mergedLyrics
        )

        val summary = Summary(
            playlists = newPlaylists.count { added -> current.playlists.none { it.id == added.key } },
            tracks = newTracks.size + newRemote.size,
            favorites = (favoriteIds.filterNot { current.favorites.contains(it) }).size,
            lyrics = (incomingLyrics.filterNot { candidate -> current.lyrics.any { it.trackId == candidate.trackId } }).size,
            source = file.name
        )
        return merged to summary
    }

    /** The phone writes `cypher-library.json` plus, when it has any, `cypher-lyrics.json`. */
    private fun readFromZip(file: File): Pair<String, String?> {
        ZipFile(file).use { zip ->
            var library: String? = null
            var lyrics: String? = null
            for (entry in zip.entries()) {
                if (entry.isDirectory || !entry.name.endsWith(".json", ignoreCase = true)) continue
                val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                when {
                    entry.name.contains("lyrics", ignoreCase = true) -> lyrics = text
                    entry.name.contains("library", ignoreCase = true) -> library = text
                    else -> library = library ?: text
                }
            }
            return (library ?: throw ImportException("El ZIP no contiene ningún respaldo de Cypher")) to lyrics
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Writes the backup this app would produce, used by Settings and by the tests. */
    fun exportJson(data: LibraryData): String {
        val root = buildJsonObject {
            put("version", JsonPrimitive(4))
            put("appName", JsonPrimitive("Cypher"))
            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
            put("playlists", JsonArray(data.playlists.map { playlist ->
                buildJsonObject {
                    put("id", JsonPrimitive(playlist.id))
                    put("name", JsonPrimitive(playlist.name))
                    put("description", JsonPrimitive(playlist.description))
                    put("coverUri", JsonPrimitive(playlist.artwork ?: ""))
                    put("createdAt", JsonPrimitive(playlist.createdAt))
                    put("updatedAt", JsonPrimitive(playlist.updatedAt))
                    put("tracks", JsonArray(playlist.trackIds.mapNotNull { id ->
                        val track = data.track(id)
                        val remote = data.remoteTrack(id)
                        when {
                            track != null -> buildJsonObject {
                                put("id", JsonPrimitive(track.id))
                                put("title", JsonPrimitive(track.title))
                                put("artist", JsonPrimitive(track.artist))
                                put("album", JsonPrimitive(track.album))
                                put("durationMs", JsonPrimitive(track.durationMs))
                                put("artworkUrl", JsonPrimitive(track.artwork ?: ""))
                                put("streamUrl", JsonPrimitive(track.path))
                                put("sourceType", JsonPrimitive("LOCAL"))
                            }
                            remote != null -> buildJsonObject {
                                put("id", JsonPrimitive(remote.id))
                                put("title", JsonPrimitive(remote.title))
                                put("artist", JsonPrimitive(remote.artist))
                                put("album", JsonPrimitive(remote.album))
                                put("durationMs", JsonPrimitive(remote.durationMs))
                                put("artworkUrl", JsonPrimitive(remote.artworkUrl ?: ""))
                                put("streamUrl", JsonPrimitive(""))
                                put("sourceType", JsonPrimitive("YOUTUBE"))
                            }
                            else -> null
                        }
                    }))
                }
            }))
            put("favorites", JsonArray(data.favorites.map { JsonPrimitive(it) }))
        }
        return root.toString()
    }
}
