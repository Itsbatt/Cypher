package com.cypher.desktop.core

import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val atMs: Long, val text: String)

/**
 * Sidecar lyrics: a `song.lrc` next to the audio file, with `[mm:ss.xx]` timestamps.
 * Cypher never fabricates lyrics; when the file is absent the player says so.
 */
object Lyrics {

    private val cache = ConcurrentHashMap<String, List<LyricLine>>()
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun forTrack(path: String?): List<LyricLine> {
        if (path.isNullOrBlank() || path.startsWith("http")) return emptyList()
        return cache.getOrPut(path) { runCatching { parse(File(path)) }.getOrDefault(emptyList()) }
    }

    /** Parses LRC text held in the library (a document that came from another device). */
    fun fromText(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        return runCatching { parseText(lrc) }.getOrDefault(emptyList())
    }

    fun isSynced(lrc: String?): Boolean = !lrc.isNullOrBlank() && timeTag.containsMatchIn(lrc)

    /** LRC text for the given (possibly imported) track, or null when there is none. */
    fun fromFile(file: File): List<LyricLine> = runCatching {
        parseText(file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    fun parseText(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.replace(timeTag, "").trim()
            if (text.isEmpty()) return@forEach
            tags.forEach { tag ->
                val minutes = tag.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = tag.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = tag.groupValues[3].takeIf { it.isNotEmpty() }
                    ?.let { it.padEnd(3, '0').take(3).toLong() } ?: 0
                lines += LyricLine(minutes * 60_000 + seconds * 1_000 + fraction, text)
            }
        }
        return lines.sortedBy { it.atMs }
    }

    private fun parse(audio: File): List<LyricLine> {
        val base = audio.nameWithoutExtension
        val candidates = listOf(File(audio.parentFile, "$base.lrc"), File(audio.parentFile, "$base.LRC"))
        val file = candidates.firstOrNull { it.isFile } ?: return emptyList()
        val lines = mutableListOf<LyricLine>()
        file.forEachLine { raw ->
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) return@forEachLine
            val text = raw.replace(timeTag, "").trim()
            if (text.isEmpty()) return@forEachLine
            tags.forEach { tag ->
                val minutes = tag.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = tag.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = tag.groupValues[3].takeIf { it.isNotEmpty() }
                    ?.let { it.padEnd(3, '0').take(3).toLong() } ?: 0
                lines += LyricLine(minutes * 60_000 + seconds * 1_000 + fraction, text)
            }
        }
        return lines.sortedBy { it.atMs }
    }
}
