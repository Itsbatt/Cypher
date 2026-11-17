package com.cypher.desktop.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.min

/**
 * Spotify CSV / ZIP import.
 *
 * Everything here is offline and pure: no Spotify login, no network, no extraction to
 * disk. A ZIP is read entry by entry into memory under hard limits, and nothing is
 * written until the user confirms the preview in [ImportPlan].
 */

data class SpotifyRow(
    val playlist: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val isrc: String? = null,
    val uri: String? = null,
    val addedAt: String? = null,
    /** Row order inside its playlist, as it appeared in the file. */
    val position: Int = 0
)

data class CsvTable(
    val name: String,
    val header: List<String>,
    val rows: List<List<String>>
)

data class ParseIssue(val file: String, val message: String)

data class NamedCsv(val name: String, val bytes: ByteArray)

data class ZipInspection(
    val csvs: List<NamedCsv> = emptyList(),
    val issues: List<ParseIssue> = emptyList(),
    val ignored: List<String> = emptyList()
)

enum class MatchQuality { HIGH, MEDIUM, LOW, NONE }

data class ImportedEntry(
    val row: SpotifyRow,
    val targetId: String,
    val quality: MatchQuality,
    /** Human-readable reason, shown in the preview. */
    val reason: String,
    /** Stable identity of the row inside the plan, used when a match is refined later. */
    val key: String = "",
    /** YouTube result found for a row that was not in the library, with its metadata. */
    val remote: RemoteTrack? = null
)

data class PlaylistPlan(
    val name: String,
    val sourceKey: String,
    val entries: List<ImportedEntry>,
    val duplicates: Int
) {
    val total: Int get() = entries.size
    val recognized: Int get() = entries.count { it.quality != MatchQuality.NONE }
    val doubtful: Int get() = entries.count { it.quality == MatchQuality.LOW }
    val unmatched: Int get() = entries.count { it.quality == MatchQuality.NONE }

    /** Replaces one row's match after a YouTube search, keeping its position. */
    fun repoint(key: String, targetId: String, quality: MatchQuality, remote: RemoteTrack?, reason: String): PlaylistPlan =
        copy(
            entries = entries.map { entry ->
                if (entry.key != key) entry
                else entry.copy(targetId = targetId, quality = quality, remote = remote, reason = reason)
            }
        )
}

data class ImportPlan(
    val playlists: List<PlaylistPlan> = emptyList(),
    val issues: List<ParseIssue> = emptyList(),
    val files: List<String> = emptyList()
) {
    val songs: Int get() = playlists.sumOf { it.total }
    val recognized: Int get() = playlists.sumOf { it.recognized }
    val doubtful: Int get() = playlists.sumOf { it.doubtful }
    val unmatched: Int get() = playlists.sumOf { it.unmatched }

    fun repoint(key: String, targetId: String, quality: MatchQuality, remote: RemoteTrack?, reason: String): ImportPlan =
        copy(playlists = playlists.map { it.repoint(key, targetId, quality, remote, reason) })

    /** Rows still worth asking YouTube about, in preview order. */
    fun pendingYoutube(): List<ImportedEntry> = playlists.flatMap { playlist ->
        playlist.entries.filter { it.quality == MatchQuality.NONE && it.remote == null }
    }
}

object SpotifyCsv {

    private val utf8 = Charsets.UTF_8

    /** Strips a UTF-8 BOM; falls back to Windows-1252 when the bytes are not UTF-8. */
    fun decode(bytes: ByteArray): String {
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val body = if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
        val utf8Text = runCatching { utf8.newDecoder().decode(java.nio.ByteBuffer.wrap(body)).toString() }.getOrNull()
        if (utf8Text != null) return utf8Text
        return runCatching { Charset.forName("windows-1252").decode(java.nio.ByteBuffer.wrap(body)).toString() }
            .getOrElse { String(body, utf8) }
    }

    fun detectDelimiter(headerLine: String): Char {
        val candidates = listOf(',', ';', '\t', '|')
        val outsideQuotes = StringBuilder()
        var quoted = false
        headerLine.forEach { ch ->
            if (ch == '"') quoted = !quoted
            if (!quoted) outsideQuotes.append(ch)
        }
        return candidates.maxByOrNull { candidate -> outsideQuotes.count { it == candidate } } ?: ','
    }

    /** RFC 4180-ish reader: quoted fields, doubled quotes and newlines inside quotes. */
    fun splitRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var record = mutableListOf<String>()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                quoted && ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"'); index++
                }
                ch == '"' -> quoted = !quoted
                !quoted && ch == delimiter -> {
                    record.add(field.toString()); field = StringBuilder()
                }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    record.add(field.toString()); field = StringBuilder()
                    if (record.any { it.isNotBlank() }) records.add(record)
                    record = mutableListOf()
                }
                else -> field.append(ch)
            }
            index++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record.add(field.toString())
            if (record.any { it.isNotBlank() }) records.add(record)
        }
        return records
    }

    /** Returns null when the file has no usable header (not a CSV we understand). */
    fun table(name: String, bytes: ByteArray): CsvTable? {
        val text = decode(bytes).trimStart('\uFEFF')
        if (text.isBlank()) return null
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = detectDelimiter(firstLine)
        val records = splitRecords(text, delimiter)
        if (records.size < 2) return null
        val header = records.first().map { it.trim() }
        val rows = records.drop(1).filter { row -> row.any { it.isNotBlank() } }
        return CsvTable(name, header, rows)
    }

    private fun normalizeHeader(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[áàä]"), "a").replace(Regex("[éèë]"), "e")
            .replace(Regex("[íìï]"), "i").replace(Regex("[óòö]"), "o")
            .replace(Regex("[úùü]"), "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private val titleKeys = listOf("track name", "song", "title", "track", "nombre de la cancion", "cancion", "name")
    private val artistKeys = listOf("artist name s", "artist names", "artist name", "artists", "artist", "artista", "interpretes")
    private val albumKeys = listOf("album name", "album", "album title", "disco")
    private val playlistKeys = listOf("playlist name", "playlist", "playlist title", "lista", "nombre de la lista")
    private val durationKeys = listOf("duration ms", "track duration ms", "duration", "duracion", "length ms", "duration milliseconds")
    private val isrcKeys = listOf("isrc", "isrc code", "isrcs")
    private val uriKeys = listOf("track uri", "uri", "spotify uri", "spotify track id", "track id", "spotify id", "url")
    private val addedKeys = listOf("added at", "added date", "added", "fecha de añadido", "fecha agregada")
    private val positionKeys = listOf("position", "index", "track number", "no", "number", "pos")

    private fun indexOf(header: List<String>, keys: List<String>): Int {
        val normalized = header.map(::normalizeHeader)
        // Exact match first, then "starts with" so "Artist Name(s)" and "Track URI" work.
        keys.forEach { key ->
            val exact = normalized.indexOf(key)
            if (exact >= 0) return exact
        }
        keys.forEach { key ->
            val prefix = normalized.indexOfFirst { it == key || it.startsWith("$key ") }
            if (prefix >= 0) return prefix
        }
        return -1
    }

    fun rowsOf(table: CsvTable, fallbackPlaylist: String): List<SpotifyRow> {
        val title = indexOf(table.header, titleKeys)
        val artist = indexOf(table.header, artistKeys)
        if (title < 0) return emptyList()
        val album = indexOf(table.header, albumKeys)
        val playlist = indexOf(table.header, playlistKeys)
        val duration = indexOf(table.header, durationKeys)
        val isrc = indexOf(table.header, isrcKeys)
        val uri = indexOf(table.header, uriKeys)
        val added = indexOf(table.header, addedKeys)
        val position = indexOf(table.header, positionKeys)

        val counters = mutableMapOf<String, Int>()
        return table.rows.mapNotNull { row ->
            fun cell(index: Int): String = if (index >= 0 && index < row.size) row[index].trim() else ""
            val songTitle = cell(title)
            if (songTitle.isBlank()) return@mapNotNull null
            val listName = if (playlist >= 0) cell(playlist).ifBlank { fallbackPlaylist } else fallbackPlaylist
            val order = counters.merge(listName, 1, Int::plus)?.minus(1) ?: 0
            SpotifyRow(
                playlist = listName,
                title = songTitle,
                artist = cell(artist),
                album = cell(album),
                durationMs = parseDuration(cell(duration)),
                isrc = cell(isrc).takeIf { it.isNotBlank() },
                uri = cell(uri).takeIf { it.isNotBlank() },
                addedAt = cell(added).takeIf { it.isNotBlank() },
                position = if (position >= 0) cell(position).toIntOrNull() ?: order else order
            )
        }
    }

    fun parseDuration(raw: String): Long {
        if (raw.isBlank()) return 0
        val clean = raw.trim().replace(" ", "")
        clean.toLongOrNull()?.let { return if (it in 1..86_400_000L) it else 0 } // ms column
        val parts = clean.split(':')
        if (parts.size in 2..3) {
            val seconds = parts.mapNotNull { it.toDoubleOrNull() }
            if (seconds.size == parts.size) {
                val total = when (seconds.size) {
                    2 -> seconds[0] * 60 + seconds[1]
                    else -> seconds[0] * 3600 + seconds[1] * 60 + seconds[2]
                }
                return (total * 1000).toLong()
            }
        }
        val seconds = clean.toDoubleOrNull() ?: return 0
        return (seconds * 1000).toLong()
    }

    data class FileParse(val rows: List<SpotifyRow> = emptyList(), val error: String? = null)

    /** Reads one CSV file, reporting why it was skipped instead of failing silently. */
    fun parseFile(name: String, bytes: ByteArray): FileParse {
        val table = table(name, bytes) ?: return FileParse(error = "No parece un CSV de Spotify.")
        val rows = rowsOf(table, fallbackName(name))
        return if (rows.isEmpty()) FileParse(error = "No se encontró una columna con el nombre de la canción.")
        else FileParse(rows)
    }

    /** Playlist name for exports that only contain songs, e.g. "Liked Songs.csv". */
    fun fallbackName(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val withoutExtension = base.removeSuffix(".csv").removeSuffix(".CSV").trim()
        val cleaned = withoutExtension.replace('_', ' ').replace('-', ' ').trim()
        return when {
            cleaned.isBlank() -> "Spotify"
            cleaned.equals("your library", true) -> "Biblioteca de Spotify"
            cleaned.equals("liked songs", true) -> "Canciones que te gustan"
            cleaned.equals("saved songs", true) -> "Canciones guardadas"
            else -> cleaned
        }
    }
}

object SpotifyZip {

    const val MAX_ENTRIES = 200
    const val MAX_UNCOMPRESSED_BYTES = 40L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

    /** Reads a ZIP from memory: no files are created, nothing outside it is touched. */
    fun inspect(zip: ByteArray): ZipInspection {
        val isZip = zip.size >= 4 && zip[0] == 'P'.code.toByte() && zip[1] == 'K'.code.toByte() &&
            (zip[2].toInt() and 0xFF) == 3 && (zip[3].toInt() and 0xFF) == 4
        if (!isZip) return ZipInspection(issues = listOf(ParseIssue("zip", "El archivo no es un ZIP válido.")))
        val csvs = mutableListOf<NamedCsv>()
        val issues = mutableListOf<ParseIssue>()
        val ignored = mutableListOf<String>()
        var entries = 0
        var total = 0L
        runCatching {
            ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
                while (true) {
                    val entry = stream.nextEntry ?: break
                    entries++
                    if (entries > MAX_ENTRIES) {
                        issues += ParseIssue("zip", "El ZIP tiene más de $MAX_ENTRIES archivos; no se abrió.")
                        break
                    }
                    val rawName = entry.name.replace('\\', '/')
                    if (entry.isDirectory) continue
                    if (unsafeName(rawName)) {
                        issues += ParseIssue(rawName, "Ruta no permitida dentro del ZIP (Zip Slip).")
                        continue
                    }
                    if (rawName.startsWith("__MACOSX/") || rawName.substringAfterLast('/').startsWith(".")) {
                        ignored += rawName
                        continue
                    }
                    if (!rawName.lowercase().endsWith(".csv")) {
                        ignored += rawName
                        continue
                    }
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var entryBytes = 0L
                    while (true) {
                        val read = stream.read(chunk)
                        if (read <= 0) break
                        entryBytes += read
                        total += read
                        if (entryBytes > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
                            issues += ParseIssue(
                                rawName,
                                "El ZIP supera el límite de ${MAX_UNCOMPRESSED_BYTES / (1024 * 1024)} MB descomprimidos."
                            )
                            return ZipInspection(csvs, issues, ignored)
                        }
                        buffer.write(chunk, 0, read)
                    }
                    csvs += NamedCsv(rawName.substringAfterLast('/'), buffer.toByteArray())
                }
            }
        }.onFailure { error ->
            issues += ParseIssue("zip", "El archivo no es un ZIP válido: ${error.message ?: "error desconocido"}")
        }
        return ZipInspection(csvs, issues, ignored)
    }

    fun unsafeName(name: String): Boolean {
        if (name.startsWith("/")) return true
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return true
        return name.split('/').any { it == ".." }
    }
}

object SpotifyMatcher {

    private val bracket = Regex("\\([^)]*\\)|\\[[^]]*]")
    private val noise = Regex("\\s+")

    /** Lowercase, no accents, no "(feat. …)" / "(Remastered 2011)" brackets, no punctuation. */
    fun normalize(raw: String): String {
        val withoutMarks = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutMarks.lowercase()
            .replace(bracket, " ")
            .replace(Regex("\\bfeat\\b.*$"), " ")
            .replace(Regex("\\bft\\b.*$"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(noise, " ")
    }

    fun isrcKey(raw: String?): String? = raw?.trim()?.uppercase()?.takeIf { it.length >= 8 }

    fun hashKey(title: String, artist: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("${normalize(title)}|${normalize(artist)}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun durationClose(first: Long, second: Long): Boolean {
        if (first <= 0 || second <= 0) return true
        return abs(first - second) <= 4_000 || abs(first - second) <= min(first, second) / 10
    }

    /**
     * True when one title says live/remix/acoustic and the other does not. Those are
     * different recordings and must not be silently swapped.
     */
    fun variantMismatch(first: String, second: String): Boolean {
        val markers = listOf("live", "remix", "acoustic", "instrumental", "cover", "sped up", "slowed")
        val a = normalize(first)
        val b = normalize(second)
        return markers.any { marker -> a.contains(marker) != b.contains(marker) }
    }

    data class Candidate(val id: String, val title: String, val artist: String, val durationMs: Long, val isrc: String? = null)

    fun candidates(data: LibraryData): List<Candidate> =
        data.tracks.map { Candidate(it.id, it.title, it.artist, it.durationMs) } +
            data.remoteTracks.filter { it.source == "YOUTUBE" }.map {
                Candidate(it.id, it.title, it.artist, it.durationMs, it.isrc)
            }

    fun match(row: SpotifyRow, pool: List<Candidate>): Pair<Candidate, MatchQuality>? {
        val isrc = isrcKey(row.isrc)
        if (isrc != null) {
            pool.firstOrNull { isrcKey(it.isrc) == isrc }?.let { return it to MatchQuality.HIGH }
        }
        val wanted = normalize(row.title)
        if (wanted.isBlank()) return null
        val artist = normalize(row.artist)
        var best: Pair<Candidate, MatchQuality>? = null
        pool.forEach { candidate ->
            val title = normalize(candidate.title)
            if (title != wanted) return@forEach
            if (variantMismatch(row.title, candidate.title)) return@forEach
            val candidateArtist = normalize(candidate.artist)
            val quality = when {
                artist.isNotBlank() && candidateArtist == artist && durationClose(row.durationMs, candidate.durationMs) ->
                    MatchQuality.HIGH
                artist.isNotBlank() && (candidateArtist.contains(artist) || artist.contains(candidateArtist)) &&
                    durationClose(row.durationMs, candidate.durationMs) -> MatchQuality.MEDIUM
                artist.isBlank() && durationClose(row.durationMs, candidate.durationMs) -> MatchQuality.LOW
                else -> MatchQuality.LOW
            }
            val current = best
            if (current == null || quality.ordinal < current.second.ordinal) best = candidate to quality
        }
        return best
    }

    /**
     * Builds the preview: one entry per row, in file order, duplicates removed inside a
     * playlist. Unresolved rows keep a `sp_` reference with their metadata.
     */
    fun plan(rows: List<SpotifyRow>, data: LibraryData, issues: List<ParseIssue> = emptyList(), files: List<String> = emptyList()): ImportPlan {
        val pool = candidates(data)
        val grouped = rows.groupBy { it.playlist.ifBlank { "Spotify" } }
        val plans = grouped.map { (name, list) ->
            val seen = mutableSetOf<String>()
            var duplicates = 0
            val entries = mutableListOf<ImportedEntry>()
            val playlistKey = "spotify:${normalize(name)}"
            list.sortedBy { it.position }.forEach { row ->
                val match = match(row, pool)
                val targetId = match?.first?.id ?: "sp_${hashKey(row.title, row.artist)}"
                if (!seen.add(targetId)) {
                    duplicates++
                    return@forEach
                }
                entries += ImportedEntry(
                    row = row,
                    targetId = targetId,
                    quality = match?.second ?: MatchQuality.NONE,
                    key = "$playlistKey#${entries.size}",
                    reason = when {
                        match == null -> "Sin coincidencia en la biblioteca"
                        match.second == MatchQuality.HIGH -> "ISRC" .takeIf { row.isrc != null } ?: "Título y artista exactos"
                        match.second == MatchQuality.MEDIUM -> "Título exacto, artista compatible"
                        else -> "Solo coincide el título"
                    }
                )
            }
            PlaylistPlan(
                name = name,
                sourceKey = playlistKey,
                entries = entries,
                duplicates = duplicates
            )
        }.sortedBy { it.name.lowercase() }
        return ImportPlan(plans, issues, files)
    }
}

/** Applies a confirmed Spotify plan to the library. Pure: returns the new document. */
object SpotifyImportApply {

    data class Result(val data: LibraryData, val created: List<Playlist>, val updated: List<Playlist>, val remote: Int)

    /**
     * Creates one playlist per plan. When a playlist with the same origin and name
     * already exists, [updateExisting] decides between replacing its contents and
     * creating a second one.
     */
    fun apply(
        data: LibraryData,
        plan: ImportPlan,
        updateExisting: Boolean,
        selected: Set<String> = plan.playlists.map { it.sourceKey }.toSet(),
        now: Long = System.currentTimeMillis()
    ): Result {
        var doc = data
        val created = mutableListOf<Playlist>()
        val updated = mutableListOf<Playlist>()
        var remote = 0
        plan.playlists.filter { it.sourceKey in selected }.forEach { planned ->
            // Keep every Spotify row as a remote reference; resolved rows reuse the
            // matched song, unresolved ones stay visible and can be fixed later.
            val newRemote = planned.entries
                .filter { entry -> doc.track(entry.targetId) == null && doc.remoteTrack(entry.targetId) == null }
                .map { entry ->
                    // A YouTube match found during the preview keeps its video id and
                    // cover; an unresolved Spotify row keeps its own metadata instead.
                    entry.remote?.copy(
                        id = entry.targetId,
                        isrc = entry.row.isrc ?: entry.remote.isrc,
                        confidence = entry.quality.name,
                        updatedAt = now
                    ) ?: RemoteTrack(
                        id = entry.targetId,
                        title = entry.row.title,
                        artist = entry.row.artist,
                        album = entry.row.album,
                        durationMs = entry.row.durationMs,
                        source = "SPOTIFY",
                        available = false,
                        isrc = entry.row.isrc,
                        confidence = entry.quality.name,
                        updatedAt = now
                    )
                }
                .distinctBy { it.id }
            doc = doc.copy(remoteTracks = doc.remoteTracks + newRemote)
            remote += newRemote.size
            val ids = planned.entries.map { it.targetId }.distinct()
            val existing = doc.playlists.firstOrNull {
                it.origin == "SPOTIFY" && it.sourceUrl == planned.sourceKey
            }
            if (existing != null && updateExisting) {
                val merged = (existing.trackIds + ids).distinct()
                val changed = existing.copy(trackIds = merged, updatedAt = now)
                doc = doc.copy(playlists = doc.playlists.map { if (it.id == existing.id) changed else it })
                updated += changed
            } else {
                val targetName = if (existing != null) "${planned.name} (importada)" else planned.name
                val playlist = Playlist(
                    id = java.util.UUID.randomUUID().toString(),
                    name = targetName,
                    description = "Importada de Spotify · ${planned.total} canciones",
                    trackIds = ids,
                    createdAt = now,
                    updatedAt = now,
                    origin = "SPOTIFY",
                    sourceUrl = planned.sourceKey
                )
                doc = doc.copy(playlists = doc.playlists + playlist)
                created += playlist
            }
        }
        return Result(doc, created, updated, remote)
    }
}
