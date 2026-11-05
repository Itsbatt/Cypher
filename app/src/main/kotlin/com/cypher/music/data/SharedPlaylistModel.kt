package com.cypher.music.data

import android.util.Base64
import android.util.Log
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class SharedPlaylistPayload(
    val v: Int = 1, // Protocol version
    val name: String,
    val description: String? = null,
    val tracks: List<SharedTrackPayload>
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("v", v)
        root.put("name", name)
        if (!description.isNullOrBlank()) {
            root.put("desc", description)
        }
        val tracksArray = JSONArray()
        for (track in tracks) {
            val tObj = JSONObject()
            tObj.put("t", track.title)
            tObj.put("a", track.artist)
            if (!track.album.isNullOrBlank()) tObj.put("al", track.album)
            if (track.duration > 0) tObj.put("d", track.duration)
            if (!track.videoId.isNullOrBlank()) tObj.put("v", track.videoId)
            tObj.put("s", track.source)
            tracksArray.put(tObj)
        }
        root.put("trks", tracksArray)
        return root.toString()
    }

    /**
     * Serializes this payload into an ultra-compact URL-safe P2P QR string using GZIP + Base64.
     */
    fun toP2pQrPayload(): String {
        val json = toJson()
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(json.toByteArray(Charsets.UTF_8))
        }
        val base64 = Base64.encodeToString(bos.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return "cypher://p2p?d=$base64"
    }

    fun toPlaylist(): Playlist {
        val convertedTracks = tracks.map { st ->
            val vid = st.videoId?.trim()?.removePrefix("yt_")
            val isLocal = st.source.equals("LOCAL", ignoreCase = true)
            val trackId = if (!vid.isNullOrEmpty()) "yt_$vid" else UUID.randomUUID().toString()
            val artwork = if (!vid.isNullOrEmpty()) "https://i.ytimg.com/vi/$vid/hqdefault.jpg" else null
            val stream = if (!vid.isNullOrEmpty()) "https://www.youtube.com/watch?v=$vid" else ""

            Track(
                id = trackId,
                title = st.title,
                artist = ArtistNormalizer.normalize(st.artist),
                album = st.album ?: "",
                durationMs = st.duration,
                artworkUrl = artwork,
                streamUrl = stream,
                sourceType = if (isLocal) AudioSourceType.LOCAL else AudioSourceType.YOUTUBE,
                path = null
            )
        }

        return Playlist(
            id = "pl_shared_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            name = name,
            description = description ?: "Playlist importada mediante Cypher",
            coverUri = null, // uses mosaic from tracks automatically
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPrivate = true,
            tracks = convertedTracks
        )
    }

    companion object {
        fun fromPlaylist(playlist: Playlist): SharedPlaylistPayload {
            val sharedTracks = playlist.tracks.map { track ->
                val fromUrl = track.streamUrl.let { url ->
                    if (url.contains("v=")) url.substringAfter("v=").substringBefore("&").substringBefore("?")
                    else if (url.contains("youtu.be/")) url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                    else null
                }
                val fromId = track.id.removePrefix("yt_")
                    .substringAfterLast("v=")
                    .substringAfterLast("/")
                    .substringBefore("?")
                    .substringBefore("&")
                val vid = fromUrl ?: if (track.sourceType == AudioSourceType.YOUTUBE && fromId.isNotBlank()) fromId else null

                SharedTrackPayload(
                    title = track.title,
                    artist = track.artist,
                    album = track.album.ifEmpty { null },
                    duration = track.durationMs,
                    videoId = vid,
                    source = track.sourceType.name
                )
            }

            return SharedPlaylistPayload(
                v = 1,
                name = playlist.name,
                description = playlist.description.ifEmpty { null },
                tracks = sharedTracks
            )
        }

        fun fromJson(jsonStr: String): SharedPlaylistPayload {
            val root = JSONObject(jsonStr)
            val version = root.optInt("v", 1)
            val name = root.getString("name")
            val desc = if (root.has("desc")) root.optString("desc") else if (root.has("description")) root.optString("description") else null

            val tracksArray = if (root.has("trks")) root.getJSONArray("trks") else root.optJSONArray("tracks") ?: JSONArray()
            val trackList = mutableListOf<SharedTrackPayload>()

            for (i in 0 until tracksArray.length()) {
                val tObj = tracksArray.getJSONObject(i)
                val title = if (tObj.has("t")) tObj.getString("t") else tObj.getString("title")
                val artist = if (tObj.has("a")) tObj.getString("a") else tObj.getString("artist")
                val album = if (tObj.has("al")) tObj.optString("al") else if (tObj.has("album")) tObj.optString("album") else null
                val duration = if (tObj.has("d")) tObj.optLong("d", 0L) else tObj.optLong("duration", 0L)
                val videoId = if (tObj.has("v")) tObj.optString("v") else if (tObj.has("videoId")) tObj.optString("videoId") else null
                val source = if (tObj.has("s")) tObj.optString("s", "YOUTUBE") else tObj.optString("source", "YOUTUBE")

                trackList.add(
                    SharedTrackPayload(
                        title = title,
                        artist = artist,
                        album = if (album.isNullOrBlank()) null else album,
                        duration = duration,
                        videoId = if (videoId.isNullOrBlank()) null else videoId,
                        source = source
                    )
                )
            }

            return SharedPlaylistPayload(
                v = version,
                name = name,
                description = desc,
                tracks = trackList
            )
        }

        fun fromP2pQrPayload(payload: String): SharedPlaylistPayload? {
            val trimmed = payload.trim()
            val base64 = when {
                trimmed.startsWith("cypher://p2p?d=") -> trimmed.removePrefix("cypher://p2p?d=")
                trimmed.startsWith("https://cypher.app/p2p?d=") -> trimmed.removePrefix("https://cypher.app/p2p?d=")
                trimmed.startsWith("MF1:") -> trimmed.removePrefix("MF1:")
                else -> trimmed
            }

            // 1. Try decoding as Base64 (URL_SAFE or DEFAULT) + GZIP
            val bytes = try {
                Base64.decode(base64, Base64.URL_SAFE or Base64.NO_WRAP)
            } catch (_: Exception) {
                try {
                    Base64.decode(base64, Base64.DEFAULT)
                } catch (_: Exception) {
                    null
                }
            }

            if (bytes != null && bytes.isNotEmpty()) {
                try {
                    val bis = ByteArrayInputStream(bytes)
                    val json = GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    return fromJson(json)
                } catch (e: Exception) {
                    Log.w("SharedPlaylistModel", "GZIP decompression failed, trying raw json fallback: ${e.message}")
                }
            }

            // 2. Fallback: try raw JSON directly
            return try {
                fromJson(trimmed)
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class SharedTrackPayload(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long = 0L,
    val videoId: String? = null,
    val source: String = "YOUTUBE"
)
