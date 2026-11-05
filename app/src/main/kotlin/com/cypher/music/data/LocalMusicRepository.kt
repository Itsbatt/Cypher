package com.cypher.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Repository accessing Android MediaStore with intelligent filtering:
 * - Excludes non-music audios (IS_MUSIC != 0)
 * - Excludes short ringtones, notifications, and audio snippets (< 15 seconds)
 * - Excludes WhatsApp / Telegram audios, voice notes, recordings, and app caches
 *   via path token validation and regex pattern matching.
 */
class LocalMusicRepository(private val context: Context) {

    companion object {
        // Minimum track length: 15 seconds to eliminate notification sounds & short voice clips
        private const val MIN_TRACK_DURATION_MS = 15000L

        // Blacklisted directory segments (case-insensitive)
        private val EXCLUDED_PATH_TOKENS = listOf(
            "whatsapp",
            "whatsapp audio",
            "whatsapp voice notes",
            "whatsapp media",
            "telegram",
            "telegram audio",
            "telegram files",
            "telegram documents",
            "recordings",
            "voice recorder",
            "voicenotes",
            "voice_notes",
            "callrecordings",
            "call_recordings",
            "notifications",
            "ringtones",
            "alarms",
            "sound_recorder",
            "android/data",
            "android/media",
            ".cache",
            "/cache/",
            ".trash",
            "thumbnails"
        )

        // Regex pattern to catch voice memos, PTTs, and call logs
        private val JUNK_FILENAME_REGEX = Regex(
            "^(aud|ptt|voice|rec|call|recording|audio_note)[-_\\d].*",
            RegexOption.IGNORE_CASE
        )

        // Standard accepted lossless and lossy music extensions
        private val ALLOWED_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "wav", "aac", "ogg", "alac", "opus", "aiff"
        )
    }

    suspend fun getLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val trackList = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // Strict SQL filter: must be flagged as music and above the minimum duration threshold
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(MIN_TRACK_DURATION_MS.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val filePath = it.getString(dataColumn) ?: ""
                    val duration = it.getLong(durationColumn)

                    // Secondary strict memory filter for paths and filenames
                    if (isJunkAudioFile(filePath, duration)) {
                        continue
                    }

                    val title = it.getString(titleColumn)?.trim()?.ifEmpty { null } ?: "Pista desconocida"
                    val artist = it.getString(artistColumn)?.trim()?.let { a ->
                        if (a == "<unknown>" || a.equals("unknown", ignoreCase = true)) "Artista desconocido" else a
                    } ?: "Artista desconocido"
                    val album = it.getString(albumColumn)?.trim()?.ifEmpty { null } ?: "Álbum local"
                    val albumId = it.getLong(albumIdColumn)

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    trackList.add(
                        Track(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            artworkUrl = artworkUri,
                            streamUrl = contentUri.toString(),
                            sourceType = AudioSourceType.LOCAL,
                            path = filePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        trackList
    }

    /**
     * Inspects file path, extension, and file name against blacklist tokens and regex patterns.
     */
    private fun isJunkAudioFile(filePath: String, durationMs: Long): Boolean {
        if (filePath.isBlank() || durationMs < MIN_TRACK_DURATION_MS) return true

        val normalizedPath = filePath.lowercase(Locale.ROOT).replace('\\', '/')

        // Check excluded folder patterns
        for (token in EXCLUDED_PATH_TOKENS) {
            if (normalizedPath.contains("/$token/") || normalizedPath.contains("/$token") || normalizedPath.contains(token)) {
                return true
            }
        }

        // Validate audio extension
        val file = File(filePath)
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext !in ALLOWED_EXTENSIONS) return true

        // Check junk filename regex (e.g. AUD-20230815-WA0001, PTT-2024...)
        val fileName = file.name
        if (JUNK_FILENAME_REGEX.matches(fileName)) return true

        // Reject .opus files from messaging paths
        if (ext == "opus" && (normalizedPath.contains("voice") || normalizedPath.contains("msg") || normalizedPath.contains("chat"))) {
            return true
        }

        return false
    }
}
