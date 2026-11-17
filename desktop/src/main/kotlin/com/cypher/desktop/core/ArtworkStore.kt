package com.cypher.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap


/**
 * One place that answers "which picture belongs to this song", with a persistent cache.
 *
 * Before this, every screen decoded its own bytes and kept them only in memory: covers
 * vanished on restart, were downloaded again on every screen, and a dead URL left a
 * blank square. Here the rules are explicit:
 *
 *  1. A local file wins: it is the user's own artwork and never goes away.
 *  2. Otherwise a copy in `artwork-cache/` is used, so the cover keeps working offline
 *     even if the service removed the image.
 *  3. Otherwise the network is tried once; whatever comes back valid is written to the
 *     cache atomically (temp file + move), so a crash cannot leave a half image.
 *  4. A file that cannot be decoded is deleted and reported as corrupt instead of being
 *     retried forever.
 *
 * The store never invents a cover: when there is no real image the caller paints the
 * placeholder. It also never downloads audio, and it only reads http(s) URLs that the
 * service itself provided.
 */
object ArtworkStore {

    enum class Status {
        /** Being read right now. */
        LOADING,
        /** A real image is available. */
        AVAILABLE,
        /** No source at all, or the file/URL does not exist. */
        MISSING,
        /** The bytes exist but cannot be decoded; the cache entry was discarded. */
        CORRUPT,
        /** Network failed and there was no cached copy. */
        OFFLINE
    }

    data class Load(val bitmap: ImageBitmap?, val status: Status, val source: String?)

    private val memory = ConcurrentHashMap<String, Load>()
    private val locks = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    private val cacheDir: File
        get() = File(Storage.dataDir.toFile(), "artwork-cache").also { it.mkdirs() }

    /** What is already known about a source, without starting any work. */
    fun peek(source: String?): Load? = source?.takeIf { it.isNotBlank() }?.let { memory[it] }

    /** Reads (or downloads) a cover. Safe to call from several screens at once. */
    suspend fun load(source: String?): Load {
        if (source.isNullOrBlank()) return Load(null, Status.MISSING, null)
        memory[source]?.takeIf { it.status != Status.LOADING }?.let { return it }
        // One lock per source: two screens asking for the same cover download it once.
        val lock = locks.getOrPut(source) { kotlinx.coroutines.sync.Mutex() }
        return lock.withLock {
            memory[source]?.takeIf { it.status != Status.LOADING } ?: run {
                val result = withContext(Dispatchers.IO) { resolve(source) }
                memory[source] = result
                result
            }
        }
    }

    private val folderCache = ConcurrentHashMap<String, String>()

    /**
     * The cover that ships with an album folder. Local libraries rarely embed art in every
     * file, but almost always keep a cover.jpg next to the audio; using it is reading real
     * metadata, not inventing a picture.
     */
    fun folderArtworkFor(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val folder = File(path).parentFile ?: return null
        val key = folder.absolutePath
        folderCache[key]?.let { return it }
        val found = listOf("cover", "folder", "front", "albumart", "Cover", "Folder", "Front")
            .flatMap { base -> listOf("jpg", "jpeg", "png", "webp").map { ext -> File(folder, "$base.$ext") } }
            .firstOrNull { it.isFile }
            ?.absolutePath
            ?: return null
        folderCache[key] = found
        return found
    }

    /**
     * The file this source would be drawn from, without touching the network: a local
     * path, or the entry already in the cache. Used by the glass backdrop.
     */
    fun cachedFile(source: String?): String? {
        if (source.isNullOrBlank()) return null
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            return source.takeIf { File(it).isFile }
        }
        return cacheFile(source).takeIf { it.isFile }?.absolutePath
    }

    /** Removes a cached copy so the next request can fetch a fresh one. */
    fun invalidate(source: String?) {
        if (source.isNullOrBlank()) return
        memory.remove(source)
        runCatching { cacheFile(source).delete() }
    }

    private fun resolve(source: String): Load {
        val cached = cacheFile(source)

        // 1. A real local file chosen by the user.
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            val file = File(source)
            if (!file.isFile) return Load(null, Status.MISSING, source)
            val load = decode(file.readBytes(), source)
            return when (load.status) {
                Status.AVAILABLE -> load
                else -> Load(null, Status.CORRUPT, source)
            }
        }

        // 2. The offline copy.
        if (cached.isFile) {
            val load = decode(runCatching { cached.readBytes() }.getOrNull(), source)
            if (load.status == Status.AVAILABLE) return load
            // Corrupt cache entry: drop it and try the network once.
            runCatching { cached.delete() }
        }

        // 3. The service URL.
        val bytes = runCatching { download(source) }.getOrNull()
            ?: return Load(null, Status.OFFLINE, source)
        val load = decode(bytes, source)
        if (load.status == Status.AVAILABLE) store(cached, bytes)
        return load
    }

    private fun decode(bytes: ByteArray?, source: String): Load {
        if (bytes == null || bytes.isEmpty()) return Load(null, Status.CORRUPT, source)
        return runCatching {
            val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
            if (image.width <= 0 || image.height <= 0) Load(null, Status.CORRUPT, source)
            else Load(image.toComposeImageBitmap(), Status.AVAILABLE, source)
        }.getOrElse { Load(null, Status.CORRUPT, source) }
    }

    private fun download(source: String): ByteArray {
        val connection = URI(source).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "CypherDesktop")
        connection.setRequestProperty("Accept", "image/*")
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            // 12 MB is far beyond any cover; this stops a wrong URL from eating memory.
            connection.inputStream.use { stream ->
                val buffer = ByteArray(64 * 1024)
                val out = java.io.ByteArrayOutputStream()
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > 12 * 1024 * 1024) error("imagen demasiado grande")
                }
                return out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Atomic write, so an interrupted download is never mistaken for a valid cover. */
    private fun store(target: File, bytes: ByteArray) {
        runCatching {
            val temp = File(target.parentFile, target.name + ".tmp")
            Files.write(temp.toPath(), bytes)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun cacheFile(source: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(source.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$name.img")
    }
}
