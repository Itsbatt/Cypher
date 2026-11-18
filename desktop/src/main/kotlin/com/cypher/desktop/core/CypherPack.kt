package com.cypher.desktop.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
data class CypherPackManifest(
    val format: String = "cypherpack",
    val version: Int = 1,
    val app: String = "Cypher",
    val appVersion: String = "1.3.0",
    val createdAt: Long = System.currentTimeMillis(),
    val device: String = "Windows",
    val tracksCount: Int = 0,
    val playlistsCount: Int = 0,
    val description: String = "Copia de seguridad y sincronización de Cypher"
)

/**
 * Universal backup & synchronization engine for Cypher.
 * Produces and reads cross-platform `.cypherpack` bundles interoperable between
 * Windows PC and Android.
 */
object CypherPack {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Inspects a .cypherpack archive and returns its manifest without full decompression.
     */
    fun inspect(file: File): CypherPackManifest? {
        if (!file.isFile || file.length() < 32) return null
        return runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json")
                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        val text = input.bufferedReader(Charsets.UTF_8).readText()
                        json.decodeFromString<CypherPackManifest>(text)
                    }
                } else {
                    // Fallback for legacy zip backups
                    CypherPackManifest(
                        tracksCount = 0,
                        playlistsCount = 0,
                        description = "Paquete importado de Cypher"
                    )
                }
            }
        }.getOrNull()
    }

    /**
     * Exports the given [data] to a standalone [.cypherpack] file.
     */
    fun export(data: LibraryData, targetFile: File, onStatus: (String) -> Unit = {}): File {
        targetFile.parentFile?.mkdirs()
        val manifest = CypherPackManifest(
            tracksCount = data.tracks.size,
            playlistsCount = data.playlists.size,
            createdAt = System.currentTimeMillis()
        )
        val manifestJson = json.encodeToString(manifest)
        val libraryJson = json.encodeToString(data)

        onStatus("Empaquetando biblioteca...")
        val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zos ->
            // 1. Manifest
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Full Library (Desktop format)
            zos.putNextEntry(ZipEntry("library.json"))
            zos.write(libraryJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Android-compatible library entry
            zos.putNextEntry(ZipEntry("cypher-library.json"))
            zos.write(libraryJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. Playlists directory
            data.playlists.forEach { pl ->
                val safeName = pl.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
                val plEntry = ZipEntry("playlists/${pl.id}_$safeName.json")
                zos.putNextEntry(plEntry)
                zos.write(json.encodeToString(pl).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 5. Cached cover artwork
            val artDir = Storage.dataDir.resolve("artwork-cache").toFile()
            if (artDir.isDirectory) {
                artDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.take(100)?.forEach { cover ->
                    zos.putNextEntry(ZipEntry("covers/${cover.name}"))
                    FileInputStream(cover).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tmp.renameTo(targetFile)) {
            tmp.copyTo(targetFile, overwrite = true)
            tmp.delete()
        }
        onStatus("Paquete creado: ${targetFile.name}")
        return targetFile
    }

    /**
     * Imports a .cypherpack archive into [current].
     * Supports both desktop and Android exported archives.
     */
    fun importPack(file: File, current: LibraryData, merge: Boolean = true, onStatus: (String) -> Unit = {}): LibraryData {
        require(file.isFile) { "El archivo .cypherpack no existe: ${file.absolutePath}" }
        onStatus("Descomprimiendo paquete Cypher...")

        var incomingData: LibraryData? = null
        val artCache = Storage.dataDir.resolve("artwork-cache").toFile().apply { mkdirs() }

        ZipFile(file).use { zip ->
            val libEntry = zip.getEntry("library.json") ?: zip.getEntry("cypher-library.json")
                ?: throw IllegalArgumentException("El archivo no es un .cypherpack válido (falta library.json)")

            zip.getInputStream(libEntry).use { input ->
                val raw = input.bufferedReader(Charsets.UTF_8).readText()
                incomingData = runCatching {
                    json.decodeFromString<LibraryData>(raw)
                }.getOrElse {
                    parseAndroidBackup(raw, current)
                }
            }

            // Extract covers into local cache
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("covers/") && !entry.isDirectory) {
                    val coverName = entry.name.removePrefix("covers/")
                    val dest = File(artCache, coverName)
                    if (!dest.exists()) {
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(dest).use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        }

        val incoming = incomingData ?: throw IllegalStateException("No se pudo leer la biblioteca del paquete.")

        return if (merge) {
            val existingTrackIds = current.tracks.map { it.id }.toSet()
            val newTracks = incoming.tracks.filter { it.id !in existingTrackIds }

            val existingPlIds = current.playlists.map { it.id }.toSet()
            val newPlaylists = incoming.playlists.filter { it.id !in existingPlIds }

            val combinedFavorites = (current.favorites + incoming.favorites).distinct()
            val combinedRemote = (current.remoteTracks + incoming.remoteTracks).distinctBy { it.id }
            val combinedLyrics = (current.lyrics + incoming.lyrics).distinctBy { it.trackId }

            current.copy(
                tracks = current.tracks + newTracks,
                playlists = current.playlists + newPlaylists,
                favorites = combinedFavorites,
                remoteTracks = combinedRemote,
                lyrics = combinedLyrics,
                lastScanAt = System.currentTimeMillis()
            )
        } else {
            incoming.copy(
                preferences = current.preferences,
                lastScanAt = System.currentTimeMillis()
            )
        }
    }

    private fun parseAndroidBackup(raw: String, current: LibraryData): LibraryData {
        val root = json.parseToJsonElement(raw).jsonObject
        val playlistsJson = root["playlists"]?.jsonArray ?: emptyList()
        val favoritesJson = root["favorites"]?.jsonArray ?: emptyList()

        val parsedPlaylists = mutableListOf<Playlist>()
        val parsedTracks = mutableListOf<Track>()

        for (pElem in playlistsJson) {
            val pObj = pElem.jsonObject
            val id = pObj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString()
            val name = pObj["name"]?.jsonPrimitive?.content ?: "Playlist"
            val desc = pObj["description"]?.jsonPrimitive?.content
            val coverUri = pObj["coverUri"]?.jsonPrimitive?.content
            val trackIds = mutableListOf<String>()

            pObj["tracks"]?.jsonArray?.forEach { tElem ->
                val tObj = tElem.jsonObject
                val tid = tObj["id"]?.jsonPrimitive?.content ?: return@forEach
                trackIds.add(tid)
                parsedTracks.add(
                    Track(
                        id = tid,
                        title = tObj["title"]?.jsonPrimitive?.content ?: "Desconocido",
                        artist = tObj["artist"]?.jsonPrimitive?.content ?: "Desconocido",
                        album = tObj["album"]?.jsonPrimitive?.content ?: "",
                        durationMs = tObj["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        artwork = tObj["artworkUrl"]?.jsonPrimitive?.content,
                        path = tObj["streamUrl"]?.jsonPrimitive?.content ?: ""
                    )
                )
            }

            parsedPlaylists.add(
                Playlist(
                    id = id,
                    name = name,
                    description = desc ?: "",
                    artwork = coverUri,
                    trackIds = trackIds
                )
            )
        }

        val parsedFavorites = mutableListOf<String>()
        for (fElem in favoritesJson) {
            val fObj = fElem.jsonObject
            val fid = fObj["id"]?.jsonPrimitive?.content ?: continue
            parsedFavorites.add(fid)
            parsedTracks.add(
                Track(
                    id = fid,
                    title = fObj["title"]?.jsonPrimitive?.content ?: "Desconocido",
                    artist = fObj["artist"]?.jsonPrimitive?.content ?: "Desconocido",
                    album = fObj["album"]?.jsonPrimitive?.content ?: "",
                    durationMs = fObj["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                    artwork = fObj["artworkUrl"]?.jsonPrimitive?.content,
                    path = fObj["streamUrl"]?.jsonPrimitive?.content ?: ""
                )
            )
        }

        return LibraryData(
            tracks = parsedTracks.distinctBy { it.id },
            playlists = parsedPlaylists,
            favorites = parsedFavorites.distinct(),
            preferences = current.preferences
        )
    }
}
