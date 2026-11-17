package com.cypher.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.security.MessageDigest

/**
 * Reads a folder into [Track]s. Tags come from the files themselves, so the library
 * survives a reinstall and can be rebuilt without any server.
 */
object Scanner {

    private val extensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

    suspend fun scan(
        folder: File,
        onProgress: (found: Int) -> Unit = {}
    ): List<Track> = withContext(Dispatchers.IO) {
        if (!folder.isDirectory) return@withContext emptyList()
        val files = folder.walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .toList()
        val tracks = ArrayList<Track>(files.size)
        files.forEachIndexed { index, file ->
            readTrack(file)?.let { tracks.add(it) }
            if (index % 25 == 0) onProgress(tracks.size)
        }
        onProgress(tracks.size)
        tracks
    }

    /** Reads a single file, used when importing a download. */
    fun read(file: File): Track? = readTrack(file)

    private fun readTrack(file: File): Track? = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        val title = tag?.getFirst("TITLE")?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val artist = tag?.getFirst("ARTIST")?.takeIf { it.isNotBlank() } ?: "Artista desconocido"
        val album = tag?.getFirst("ALBUM").orEmpty()
        val seconds = runCatching { audio.audioHeader.trackLength.toLong() }.getOrDefault(0L)
        val artwork = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()
        Track(
            id = stableId(file.absolutePath),
            title = title,
            artist = artist,
            album = album,
            durationMs = seconds * 1000L,
            path = file.absolutePath,
            artwork = artwork?.let { storeArtwork(it, file) },
            addedAt = file.lastModified(),
            updatedAt = file.lastModified()
        )
    }.getOrNull()

    /** Artwork is copied next to the data file so the UI can load it without tags. */
    private fun storeArtwork(bytes: ByteArray, file: File): String? = runCatching {
        val dir = Storage.dataDir.resolve("artwork").also { it.toFile().mkdirs() }
        val target = dir.resolve("${stableId(file.absolutePath)}.img")
        if (!target.toFile().exists()) target.toFile().writeBytes(bytes)
        target.toString()
    }.getOrNull()

    fun stableId(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
