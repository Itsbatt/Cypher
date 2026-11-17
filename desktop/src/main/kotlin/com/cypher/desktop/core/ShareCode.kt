package com.cypher.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Shares a playlist without a server, the same way Android does: the songs are packed as
 * compact JSON, gzipped and base64url encoded into a `cypher://p2p?d=...` string. That
 * string is what goes inside the QR and what the user can copy as a code.
 *
 * Only the identity of each song travels (title, artist, video id, source); audio never does.
 */
object ShareCode {

    private const val PREFIX = "cypher://p2p?d="
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class SharedTrack(
        val title: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0,
        val videoId: String? = null,
        val source: String = "YOUTUBE"
    )

    data class Payload(
        val name: String,
        val description: String = "",
        val tracks: List<SharedTrack> = emptyList()
    )

    /** Full share string, ready for the QR. */
    fun encode(payload: Payload): String {
        val root = buildJsonObject {
            put("v", JsonPrimitive(1))
            put("name", JsonPrimitive(payload.name))
            if (payload.description.isNotBlank()) put("desc", JsonPrimitive(payload.description))
            put("trks", JsonArray(payload.tracks.map { track ->
                buildJsonObject {
                    put("t", JsonPrimitive(track.title))
                    put("a", JsonPrimitive(track.artist))
                    if (track.album.isNotBlank()) put("al", JsonPrimitive(track.album))
                    if (track.durationMs > 0) put("d", JsonPrimitive(track.durationMs))
                    track.videoId?.takeIf { it.isNotBlank() }?.let { put("v", JsonPrimitive(it)) }
                    put("s", JsonPrimitive(track.source))
                }
            }))
        }
        val raw = ByteArrayOutputStream().also { buffer ->
            GZIPOutputStream(buffer).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    /** Accepts the full link, just the payload or the payload with spaces/newlines pasted in. */
    fun decode(code: String): Payload {
        val cleaned = code.trim().replace("\n", "").replace(" ", "")
        val payload = cleaned.removePrefix(PREFIX)
            .removePrefix("cypher://playlist?d=")
        val raw = runCatching { Base64.getUrlDecoder().decode(payload) }.getOrNull()
            ?: throw IllegalArgumentException("El código no es válido")
        val decoded = runCatching { GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() } }
            .getOrNull() ?: throw IllegalArgumentException("El código está incompleto")
        val root = runCatching { json.parseToJsonElement(decoded.toString(Charsets.UTF_8)) as? JsonObject }
            .getOrNull() ?: throw IllegalArgumentException("El código no contiene una playlist")
        val name = (root["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: "Playlist compartida"
        val tracks = (root["trks"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj["t"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SharedTrack(
                title = title,
                artist = obj["a"]?.jsonPrimitive?.content.orEmpty(),
                album = obj["al"]?.jsonPrimitive?.content.orEmpty(),
                durationMs = obj["d"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                videoId = obj["v"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                source = obj["s"]?.jsonPrimitive?.content.orEmpty().ifBlank { "YOUTUBE" }
            )
        }.orEmpty()
        return Payload(
            name = name,
            description = (root["desc"] as? JsonPrimitive)?.content.orEmpty(),
            tracks = tracks
        )
    }

    /** Turns a received playlist into a local one, matching songs already in the library. */
    fun toPlaylist(payload: Payload, library: LibraryData, id: String = UUID.randomUUID().toString()): Playlist {
        val now = System.currentTimeMillis()
        val ids = payload.tracks.mapNotNull { shared ->
            // The id may arrive raw ("abc123") or already prefixed ("yt_abc123"): accept both.
            val clean = shared.videoId?.removePrefix("yt_")
            val byVideo = clean?.let { video ->
                library.remoteTracks.firstOrNull { it.videoId == video || it.id == "yt_$video" }?.id
                    ?: library.tracks.firstOrNull { it.id.contains(video) }?.id
            }
            if (byVideo != null) return@mapNotNull byVideo
            library.tracks.firstOrNull {
                it.title.equals(shared.title, ignoreCase = true) && it.artist.equals(shared.artist, ignoreCase = true)
            }?.id
        }
        return Playlist(
            id = id,
            name = payload.name,
            description = payload.description,
            trackIds = ids.distinct(),
            createdAt = now,
            updatedAt = now,
            origin = "SHARED"
        )
    }
}
