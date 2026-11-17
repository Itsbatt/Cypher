package com.cypher.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

/** One result from a YouTube Music search. */
data class MusicHit(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnail: String? = null,
    /** False when YouTube lists the entry but it can no longer be played. */
    val available: Boolean = true
)

class YouTubeMusicException(message: String) : Exception(message)

/**
 * YouTube Music through [yt-dlp]: it resolves the stream URL and writes the audio file,
 * because YouTube's own player endpoints need signatures and are blocked from most IPs.
 *
 * Nothing here runs unless the user asks for it: a search, a play or a download is always
 * an explicit click. The binary is never bundled in the repository; it is looked up, or
 * installed on request into the app-data folder.
 */
object YouTubeMusic {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val DOWNLOAD_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"

    /** Prefers an explicit path, then env, then the app-data copy, then the working folder. */
    fun binary(configured: String? = null): File? {
        val candidates = buildList {
            configured?.takeIf { it.isNotBlank() }?.let { add(it) }
            System.getenv("CYPHER_YTDLP")?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(Storage.dataDir.resolve("yt-dlp.exe").toString())
            add(File("tools", "yt-dlp.exe").absolutePath)
            add(File(System.getProperty("user.dir"), "tools/yt-dlp.exe").absolutePath)
        }
        candidates.forEach { path ->
            val file = File(path)
            if (file.isFile) return file
        }
        System.getenv("PATH").orEmpty().split(File.pathSeparator).forEach { dir ->
            listOf("yt-dlp.exe", "yt-dlp").forEach { name ->
                val file = File(dir, name)
                if (file.isFile) return file
            }
        }
        return null
    }

    fun search(query: String, limit: Int = 12, configured: String? = null): List<MusicHit> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        val bin = requireBinary(configured)
        val out = run(
            bin,
            listOf("--flat-playlist", "--no-warnings", "-J", "ytsearch$limit:$clean"),
            timeoutSeconds = 90
        )
        return rank(parseSearch(out), clean)
    }

    /**
     * ytsearch happily returns gameplay, reaction videos and 4-hour compilations before the
     * song. Very short clips and very long uploads are dropped, and titles that look like
     * anything but music are pushed to the end instead of removed (the user may still want them).
     */
    private fun rank(hits: List<MusicHit>, query: String): List<MusicHit> {
        val noise = listOf("roblox", "gameplay", "minecraft", "fortnite", "reaction", "reaccion",
            "compilation", "recopilatorio", "full album", "1 hour", "loop")
        val words = query.lowercase().split(' ').filter { it.length > 2 }
        return hits
            .filter { it.durationMs == 0L || it.durationMs in 45_000..(15 * 60_000L) }
            .distinctBy { it.videoId }
            .sortedByDescending { hit ->
                val title = hit.title.lowercase()
                var score = 0
                if (noise.none { title.contains(it) }) score += 2
                if (words.any { title.contains(it) || hit.artist.lowercase().contains(it) }) score += 2
                score
            }
    }

    /** A playlist or album: title, description, cover and its entries in source order. */
    data class MusicList(
        val title: String,
        val hits: List<MusicHit>,
        val description: String = "",
        val cover: String? = null,
        /** Canonical playlist id/link, used to detect a repeated import. */
        val sourceKey: String? = null
    )

    /**
     * Accepts a full Music/YouTube URL or a bare playlist id. Entries are read flat, so a
     * long playlist does not resolve every stream up front.
     */
    fun playlist(urlOrId: String, configured: String? = null): MusicList {
        val clean = urlOrId.trim()
        if (clean.isEmpty()) throw YouTubeMusicException("Escribe el enlace de una playlist.")
        val id = playlistId(clean)
        if (id == null) throw YouTubeMusicException(
            "Ese enlace no parece una playlist. Se aceptan music.youtube.com/playlist?list=…, " +
                "watch?v=…&list=… o un id de lista."
        )
        val bin = requireBinary(configured)
        val target = "https://music.youtube.com/playlist?list=$id"
        val out = run(bin, listOf("--flat-playlist", "--no-warnings", "-J", target), timeoutSeconds = 240)
        val root = runCatching { json.parseToJsonElement(out) as? JsonObject }.getOrNull()
            ?: throw YouTubeMusicException("YouTube no devolvió datos para esa lista.")
        val title = text(root, "title")?.takeIf { it.isNotBlank() } ?: "Playlist de YouTube Music"
        val description = text(root, "description").orEmpty()
        val hits = parseEntries(out, includeUnavailable = true)
        if (hits.isEmpty()) throw YouTubeMusicException("Esa lista no tiene canciones.")
        val cover = hits.firstNotNullOfOrNull { it.thumbnail }
        return MusicList(title, hits, description, cover, "yt:$id")
    }

    /**
     * Reads the playlist id from a Music/YouTube link, a share link or a bare id.
     * Returns null for anything else, so an album or video link is rejected before
     * starting yt-dlp.
     */
    fun playlistId(raw: String): String? {
        val clean = raw.trim()
        if (clean.isEmpty()) return null
        if (Regex("^[A-Za-z0-9_-]{12,64}$").matches(clean)) return clean
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val allowed = host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("youtube-nocookie.com")
        if (!allowed) return null
        uri.query.orEmpty().split('&').forEach { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key == "list" && Regex("^[A-Za-z0-9_-]{12,64}$").matches(value)) return value
        }
        return null
    }

    /**
     * Audio the player can actually open: a local m4a/mp3 file kept in a small cache.
     *
     * Handing JavaFX the signed googlevideo URL does not work: the request needs
     * YouTube's headers and the stream is often WebM/Opus, which JavaFX cannot decode.
     * Downloading the best audio once and playing the file gives pause, resume and seek,
     * and the URL never outlives the request that produced it.
     */
    fun cachedAudio(videoId: String, configured: String? = null, onStatus: (String) -> Unit = {}): File {
        val dir = Storage.dataDir.resolve("stream-cache").toFile().apply { mkdirs() }
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$videoId.") && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
            ?.let { return it }

        val bin = requireBinary(configured)
        val template = File(dir, "%(id)s.%(ext)s").absolutePath
        onStatus("Descargando audio…")
        run(
            bin,
            listOf(
                "--no-warnings", "--no-playlist", "--no-progress",
                "-f", "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio",
                "-o", template,
                watchUrl(videoId)
            ),
            timeoutSeconds = 300
        )
        val file = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$videoId.") && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
            ?: throw YouTubeMusicException("YouTube no entregó audio para este vídeo.")
        if (file.extension.lowercase() !in setOf("m4a", "mp3", "aac", "wav")) {
            throw YouTubeMusicException(
                "YouTube solo ofreció audio en formato ${file.extension.uppercase()}, que el reproductor no admite. " +
                    "Prueba con otra canción."
            )
        }
        pruneCache()
        return file
    }

    /** Keeps the audio cache bounded: newest 25 files and at most 700 MB. */
    private fun pruneCache(maxFiles: Int = 25, maxBytes: Long = 700L * 1024 * 1024) {
        val dir = Storage.dataDir.resolve("stream-cache").toFile()
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        var bytes = 0L
        files.forEachIndexed { index, file ->
            bytes += file.length()
            if (index >= maxFiles || bytes > maxBytes) file.delete()
        }
    }

    fun clearCache(): Int {
        val dir = Storage.dataDir.resolve("stream-cache").toFile()
        return dir.listFiles()?.count { it.isFile && it.delete() } ?: 0
    }

    /** Direct, time-limited audio URL (diagnostics only; the player uses [cachedAudio]). */
    fun streamUrl(videoId: String, configured: String? = null): String {
        val bin = requireBinary(configured)
        val out = run(
            bin,
            listOf("--no-warnings", "-f", "bestaudio[ext=m4a]/bestaudio", "--no-playlist", "-g", watchUrl(videoId)),
            timeoutSeconds = 90
        )
        return out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?: throw YouTubeMusicException("YouTube no entregó una URL de audio para este vídeo.")
    }

    /** Downloads the best audio as m4a with tags and returns the resulting file. */
    fun download(videoId: String, folder: File, configured: String? = null): File {
        val bin = requireBinary(configured)
        if (!folder.isDirectory && !folder.mkdirs()) {
            throw YouTubeMusicException("No se pudo crear la carpeta de descargas: ${folder.absolutePath}")
        }
        val template = File(folder, "%(title)s [%(id)s].%(ext)s").absolutePath
        // The resulting path is looked up on disk instead of parsed from stdout: titles with
        // accents do not survive every console encoding, the video id always does.
        run(
            bin,
            listOf(
                "--no-warnings", "--no-playlist",
                "-f", "bestaudio[ext=m4a]/bestaudio",
                "-x", "--audio-format", "m4a", "--embed-metadata",
                "--no-progress",
                "-o", template,
                watchUrl(videoId)
            ),
            timeoutSeconds = 900
        )
        return folder.listFiles()
            ?.filter { it.isFile && it.name.contains("[$videoId]") }
            ?.filter { it.extension.lowercase() in setOf("m4a", "mp3", "opus", "webm", "ogg", "flac") }
            ?.maxByOrNull { it.lastModified() }
            ?: throw YouTubeMusicException("La descarga no produjo ningún archivo.")
    }

    /** Downloads yt-dlp.exe into the app-data folder. Only runs when the user asks. */
    fun install(target: File = Storage.dataDir.resolve("yt-dlp.exe").toFile()): File {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URI(DOWNLOAD_URL).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "CypherDesktop")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw YouTubeMusicException("La descarga falló (HTTP $code).")
            connection.inputStream.use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
            if (partial.length() < 1_000_000) throw YouTubeMusicException("El archivo descargado no parece válido.")
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return target
        } finally {
            connection?.disconnect()
            partial.delete()
        }
    }

    /** Separated for tests: the shape yt-dlp prints with `--flat-playlist -J`. */
    fun parseSearch(raw: String): List<MusicHit> = parseEntries(raw, includeUnavailable = false)

    fun parseEntries(raw: String, includeUnavailable: Boolean): List<MusicHit> {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyList()
        val entries = root["entries"] as? JsonArray ?: return emptyList()
        return entries.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val title = text(entry, "title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rawId = text(entry, "id").orEmpty()
            val videoId = rawId.takeIf { it.length == 11 }
            val availability = text(entry, "availability").orEmpty().lowercase()
            val available = videoId != null &&
                !availability.contains("needs_auth") &&
                !availability.contains("premium") &&
                !availability.contains("unavailable") &&
                availability != "private"
            if (!available && !includeUnavailable) return@mapNotNull null
            val artist = listOf("channel", "uploader", "artist")
                .mapNotNull { text(entry, it)?.takeIf { text -> text.isNotBlank() } }
                .firstOrNull().orEmpty()
            val seconds = (entry["duration"] as? JsonPrimitive)?.longOrNull ?: 0L
            val thumbnails = entry["thumbnails"] as? JsonArray
            val thumbnail = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: text(entry, "thumbnail")
            MusicHit(videoId.orEmpty(), title, artist, seconds.coerceAtLeast(0L) * 1000, thumbnail, available)
        }
    }

    private fun text(entry: JsonObject, key: String): String? =
        (entry[key] as? JsonPrimitive)?.contentOrNull

    fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

    private fun requireBinary(configured: String?): File =
        binary(configured) ?: throw YouTubeMusicException(
            "Falta yt-dlp. Instálalo desde esta pantalla o define CYPHER_YTDLP con su ruta."
        )

    private fun run(bin: File, args: List<String>, timeoutSeconds: Long): String {
        val process = ProcessBuilder(listOf(bin.absolutePath) + args)
            .redirectErrorStream(false)
            .start()
        val errors = StringBuilder()
        val drain = Thread {
            runCatching {
                process.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    if (errors.length < 4_000) errors.appendLine(line)
                }
            }
        }.apply { isDaemon = true; start() }

        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw YouTubeMusicException("yt-dlp tardó demasiado y se detuvo.")
        }
        drain.join(1_000)
        if (process.exitValue() != 0) {
            val detail = errors.lineSequence()
                .map { it.removePrefix("ERROR: ").trim() }
                .lastOrNull { it.isNotBlank() && !it.startsWith("WARNING") }
            throw YouTubeMusicException(detail ?: "yt-dlp falló con código ${process.exitValue()}.")
        }
        return output
    }
}
