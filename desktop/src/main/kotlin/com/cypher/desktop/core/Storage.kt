package com.cypher.desktop.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Local-first storage: a single JSON document in the user's app-data folder.
 *
 * Writes go to a temporary file and are moved into place, so a crash mid-save can
 * never leave a half-written library. The file belongs to the user's device, not to
 * any account.
 */
object Storage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Tests point this at a temporary folder; production leaves it null. */
    @Volatile
    var rootOverride: Path? = null

    val dataDir: Path
        get() {
            val override = rootOverride
            if (override != null) return override.also { Files.createDirectories(it) }
            // Lets a test instance run against a disposable profile without touching the
            // real library (used by the UI audit and by packaging smoke tests).
            System.getenv("CYPHER_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { dir ->
                return Path.of(dir).also { Files.createDirectories(it) }
            }
            val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home")
            return Path.of(base, "Cypher Desktop").also { Files.createDirectories(it) }
        }

    val libraryFile: Path get() = dataDir.resolve("library.json")

    fun load(file: Path = libraryFile): LibraryData {
        if (!Files.exists(file)) return LibraryData()
        val raw = runCatching { Files.readString(file) }.getOrNull() ?: return LibraryData()
        val migrated = runCatching { migrateToCurrent(raw) }.getOrDefault(raw)
        val data = runCatching { json.decodeFromString<LibraryData>(migrated) }.getOrElse { return LibraryData() }
        if (migrated != raw) {
            // The previous document is kept verbatim beside the new one, so a failed
            // migration is never a one-way door.
            runCatching {
                Files.writeString(file.resolveSibling("library-v2-${System.currentTimeMillis()}.json.bak"), raw)
                save(data, file)
            }
        }
        return data
    }

    fun save(data: LibraryData, file: Path = libraryFile) {
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, json.encodeToString(data))
        runCatching {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Timestamped copy before risky bulk edits (scan, import). */
    fun backup(tag: String): Path? = runCatching {
        if (!Files.exists(libraryFile)) return null
        val target = dataDir.resolve("library-$tag-${System.currentTimeMillis()}.json.bak")
        Files.copy(libraryFile, target, StandardCopyOption.REPLACE_EXISTING)
        target
    }.getOrNull()

    /**
     * v2 → v3. The document no longer keeps a copy of the library that belonged to the
     * old cloud account. Favourites that came from another device are preserved as
     * remote references whenever they still carry a YouTube identity, so nothing the
     * phone marked is silently lost; anything without one is dropped (there is no
     * audio for it here anyway).
     */
    internal fun migrateToCurrent(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return raw
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 2
        if (version >= 3) return raw

        val cloud = root["cloudTracks"] as? JsonArray ?: JsonArray(emptyList())
        val remote = ((root["remoteTracks"] as? JsonArray)?.toList() ?: emptyList()).toMutableList()
        // Imported references may carry the id only; fill the video id so they play.
        cloud.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val sourceType = item["sourceType"]?.jsonPrimitive?.contentOrNull ?: "YOUTUBE"
            val fromId = id.removePrefix("yt_")
            val fromUrl = (item["streamUrl"]?.jsonPrimitive?.contentOrNull.orEmpty())
                .substringAfter("v=", "").take(11)
            val videoId = fromId.takeIf { it.length == 11 } ?: fromUrl.takeIf { it.length == 11 } ?: ""
            if (sourceType != "YOUTUBE" || videoId.length != 11) return@forEach
            if (remote.any { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == id }) return@forEach
            remote += buildJsonObject {
                put("id", JsonPrimitive(id))
                put("videoId", JsonPrimitive(videoId))
                put("title", JsonPrimitive(item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()))
                put("artist", JsonPrimitive(item["artist"]?.jsonPrimitive?.contentOrNull.orEmpty()))
                put("album", JsonPrimitive(item["album"]?.jsonPrimitive?.contentOrNull.orEmpty()))
                put("durationMs", JsonPrimitive(item["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L))
                item["artworkUrl"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put("artworkUrl", JsonPrimitive(it)) }
                put("source", JsonPrimitive("YOUTUBE"))
                put("available", JsonPrimitive(true))
                put("updatedAt", JsonPrimitive(item["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L))
            }
        }

        val cleaned = root.toMutableMap()
        cleaned.remove("cloudTracks")
        cleaned.remove("outbox")
        cleaned.remove("sync")
        cleaned.remove("remoteThemeJson")
        cleaned["remoteTracks"] = JsonArray(remote)
        cleaned["version"] = JsonPrimitive(3)
        return JsonObject(cleaned).toString()
    }
}
