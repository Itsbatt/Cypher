package com.cypher.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the lyrics Cypher already downloaded so they survive a reinstall and travel in the
 * backup ZIP. They are plain `.lrc` text, one file per song, named after the track id: small,
 * greppable and readable by the desktop importer without any parsing of our own format.
 */
object LyricsStore {

    private const val FOLDER = "lyrics"

    private fun dir(context: Context): File =
        File(context.filesDir, FOLDER).apply { if (!exists()) mkdirs() }

    fun save(context: Context, trackId: String, lrc: String) {
        if (trackId.isBlank() || lrc.isBlank()) return
        runCatching { File(dir(context), safeName(trackId) + ".lrc").writeText(lrc, Charsets.UTF_8) }
    }

    fun load(context: Context, trackId: String): String? =
        runCatching { File(dir(context), safeName(trackId) + ".lrc").takeIf { it.isFile }?.readText(Charsets.UTF_8) }
            .getOrNull()?.takeIf { it.isNotBlank() }

    /** Every stored lyric, as the JSON array the backup carries. */
    fun exportJson(context: Context): String {
        val array = JSONArray()
        dir(context).listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (!file.isFile || file.extension != "lrc") return@forEach
            val lrc = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
            if (lrc.isBlank()) return@forEach
            array.put(JSONObject().apply {
                put("trackId", file.nameWithoutExtension)
                put("lrc", lrc)
            })
        }
        return array.toString()
    }

    fun count(context: Context): Int =
        dir(context).listFiles()?.count { it.isFile && it.extension == "lrc" } ?: 0

    /** Track ids are ours, but they can come from an import, so never trust them in a path. */
    private fun safeName(raw: String): String =
        raw.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
            .take(96)
            .ifBlank { "track" }
}
