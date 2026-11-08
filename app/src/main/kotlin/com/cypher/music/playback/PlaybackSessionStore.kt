package com.cypher.music.playback

import android.content.Context
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import org.json.JSONArray
import org.json.JSONObject

data class RestoredSession(
    val track: Track,
    val queue: List<Track>,
    val queueIndex: Int,
    val positionMs: Long,
    val wasPlaying: Boolean,
    val isShuffle: Boolean,
    val repeatMode: RepeatMode
)

/**
 * Durable snapshot of the player. It is written only on real changes (track, position,
 * queue, modes) and once every few seconds while playing, never on every frame.
 *
 * The snapshot is restored paused on purpose: Cypher shows the last song, its queue and
 * the exact position, and only starts audio when the user presses play. Resuming audio
 * automatically after a crash or a reboot would be an unexpected sound.
 */
object PlaybackSessionStore {
    private const val PREFS = "cypher_playback_session"
    private const val KEY = "session"
    private const val VERSION = 1

    fun save(context: Context, state: PlayerState) {
        val track = state.currentTrack ?: return
        val json = JSONObject().apply {
            put("version", VERSION)
            put("track", trackToJson(track))
            put("queue", JSONArray().apply { state.queue.forEach { put(trackToJson(it)) } })
            put("queueIndex", state.queueIndex)
            put("positionMs", state.currentPositionMs.coerceAtLeast(0L))
            put("wasPlaying", state.isPlaying)
            put("isShuffle", state.isShuffle)
            put("repeatMode", state.repeatMode.name)
            put("savedAt", System.currentTimeMillis())
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    fun load(context: Context): RestoredSession? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return null
        val json = JSONObject(raw)
        val track = trackFromJson(json.optJSONObject("track") ?: return null) ?: return null
        val queueArray = json.optJSONArray("queue") ?: JSONArray()
        val queue = buildList {
            for (index in 0 until queueArray.length()) {
                trackFromJson(queueArray.optJSONObject(index))?.let(::add)
            }
        }.ifEmpty { listOf(track) }
        val index = json.optInt("queueIndex", 0)
            .coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        RestoredSession(
            track = track,
            queue = queue,
            queueIndex = index,
            positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
            wasPlaying = json.optBoolean("wasPlaying", false),
            isShuffle = json.optBoolean("isShuffle", false),
            repeatMode = runCatching {
                RepeatMode.valueOf(json.optString("repeatMode", RepeatMode.OFF.name))
            }.getOrDefault(RepeatMode.OFF)
        )
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("durationMs", track.durationMs)
        put("artworkUrl", track.artworkUrl ?: "")
        put("streamUrl", track.streamUrl)
        put("sourceType", track.sourceType.name)
        put("path", track.path ?: "")
        put("isLiked", track.isLiked)
        put("isDownloaded", track.isDownloaded)
        put("genre", track.genre ?: "")
    }

    private fun trackFromJson(json: JSONObject): Track? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = id,
            title = json.optString("title", "Sin título"),
            artist = json.optString("artist", "Artista desconocido"),
            album = json.optString("album", "Sencillo"),
            durationMs = json.optLong("durationMs", 0L),
            artworkUrl = json.optString("artworkUrl").takeIf { it.isNotBlank() },
            streamUrl = json.optString("streamUrl"),
            sourceType = runCatching {
                AudioSourceType.valueOf(json.optString("sourceType", AudioSourceType.LOCAL.name))
            }.getOrDefault(AudioSourceType.LOCAL),
            path = json.optString("path").takeIf { it.isNotBlank() },
            isLiked = json.optBoolean("isLiked", false),
            isDownloaded = json.optBoolean("isDownloaded", false),
            genre = json.optString("genre").takeIf { it.isNotBlank() }
        )
    }
}
