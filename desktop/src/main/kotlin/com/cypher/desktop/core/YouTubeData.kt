package com.cypher.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** A playlist that belongs to the signed-in Google account. */
data class YouTubePlaylistSummary(
    val id: String,
    val title: String,
    val description: String = "",
    val cover: String? = null,
    val itemCount: Int = 0,
    val channel: String = ""
)

data class YouTubePage<T>(val items: List<T> = emptyList(), val nextPageToken: String? = null)

class YouTubeApiException(
    val status: Int,
    val reason: String?,
    message: String
) : Exception(message)

/**
 * Official YouTube Data API v3 client for the user's own playlists.
 *
 * This is the Desktop counterpart of Android's InnerTube call: the same Google account,
 * the same playlists, but through a documented endpoint and an OAuth bearer token. It
 * never reads cookies, never stores the token on disk and never uses an API key.
 */
object YouTubeData {

    private const val BASE = "https://www.googleapis.com/youtube/v3"
    private val json = Json { ignoreUnknownKeys = true }

    /** Injected in tests so no network is needed. */
    internal var transport: (url: String, token: String) -> String = ::httpGet

    fun playlistsPage(accessToken: String, pageToken: String? = null): YouTubePage<YouTubePlaylistSummary> {
        val url = buildString {
            append("$BASE/playlists?part=snippet,contentDetails&mine=true&maxResults=50")
            if (!pageToken.isNullOrBlank()) append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
        }
        return parsePlaylists(transport(url, accessToken))
    }

    fun itemsPage(accessToken: String, playlistId: String, pageToken: String? = null): YouTubePage<MusicHit> {
        val url = buildString {
            append("$BASE/playlistItems?part=snippet,contentDetails&maxResults=50&playlistId=")
            append(URLEncoder.encode(playlistId, "UTF-8"))
            if (!pageToken.isNullOrBlank()) append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
        }
        return parseItems(transport(url, accessToken))
    }

    /** Every playlist of the account, bounded so a broken page token cannot loop. */
    fun allPlaylists(accessToken: String, maxPages: Int = 20): List<YouTubePlaylistSummary> {
        val result = mutableListOf<YouTubePlaylistSummary>()
        var token: String? = null
        var pages = 0
        do {
            val page = playlistsPage(accessToken, token)
            result += page.items
            token = page.nextPageToken
            pages++
        } while (!token.isNullOrBlank() && pages < maxPages)
        return result.distinctBy { it.id }
    }

    /** Every song of one playlist, in the order YouTube returns it. */
    fun allItems(accessToken: String, playlistId: String, maxPages: Int = 40): List<MusicHit> {
        val result = mutableListOf<MusicHit>()
        var token: String? = null
        var pages = 0
        do {
            val page = itemsPage(accessToken, playlistId, token)
            result += page.items
            token = page.nextPageToken
            pages++
        } while (!token.isNullOrBlank() && pages < maxPages)
        return result.distinctBy { it.videoId }.filter { it.videoId.isNotBlank() }
    }

    fun parsePlaylists(raw: String): YouTubePage<YouTubePlaylistSummary> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return YouTubePage()
        val items = (root["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val id = text(entry, "id") ?: return@mapNotNull null
            val snippet = entry["snippet"] as? JsonObject ?: JsonObject(emptyMap())
            val title = text(snippet, "title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val count = (entry["contentDetails"] as? JsonObject)
                ?.get("itemCount")?.jsonPrimitive?.longOrNull?.toInt() ?: 0
            YouTubePlaylistSummary(
                id = id,
                title = title,
                description = text(snippet, "description").orEmpty(),
                cover = thumbnail(snippet),
                itemCount = count,
                channel = text(snippet, "channelTitle").orEmpty()
            )
        }
        return YouTubePage(items, nextToken(root))
    }

    fun parseItems(raw: String): YouTubePage<MusicHit> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return YouTubePage()
        val items = (root["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val snippet = entry["snippet"] as? JsonObject ?: return@mapNotNull null
            val videoId = (snippet["resourceId"] as? JsonObject)?.let { text(it, "videoId") }
                ?: (entry["contentDetails"] as? JsonObject)?.let { text(it, "videoId") }
                ?: return@mapNotNull null
            if (videoId.length != 11) return@mapNotNull null
            val title = text(snippet, "title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = listOf("videoOwnerChannelTitle", "channelTitle")
                .mapNotNull { text(snippet, it)?.takeIf { value -> value.isNotBlank() } }
                .firstOrNull().orEmpty()
                .removeSuffix(" - Topic")
            MusicHit(videoId, title, artist, 0, thumbnail(snippet))
        }
        return YouTubePage(items, nextToken(root))
    }

    /** Turns an API page into the same list the link reader produces. */
    fun toMusicList(summary: YouTubePlaylistSummary, hits: List<MusicHit>): YouTubeMusic.MusicList =
        YouTubeMusic.MusicList(
            title = summary.title,
            hits = hits,
            description = summary.description,
            cover = summary.cover ?: hits.firstNotNullOfOrNull { it.thumbnail },
            sourceKey = "yt:${summary.id}"
        )

    private fun nextToken(root: JsonObject): String? = text(root, "nextPageToken")?.takeIf { it.isNotBlank() }

    private fun text(entry: JsonObject, key: String): String? =
        (entry[key] as? JsonPrimitive)?.contentOrNull

    private fun thumbnail(snippet: JsonObject): String? {
        val thumbs = snippet["thumbnails"] as? JsonObject ?: return null
        return listOf("maxres", "standard", "high", "medium", "default")
            .firstNotNullOfOrNull { key ->
                (thumbs[key] as? JsonObject)?.let { text(it, "url") }
            }
    }

    private fun httpGet(url: String, token: String): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) throw apiError(status, body)
        return body
    }

    fun apiError(status: Int, raw: String): YouTubeApiException {
        val error = runCatching { json.parseToJsonElement(raw).jsonObject["error"] as? JsonObject }.getOrNull()
        val reason = error?.let { body ->
            (body["errors"] as? JsonArray)?.firstOrNull()?.let { item ->
                ((item as? JsonObject)?.get("reason") as? JsonPrimitive)?.contentOrNull
            }
        }
        val detail = error?.let { text(it, "message") }.orEmpty()
        val friendly = when {
            status == 401 -> "La sesión de Google caducó. Vuelve a conectar la cuenta."
            reason == "accessNotConfigured" ->
                "La API de YouTube Data no está habilitada en el proyecto OAuth. " +
                    "Actívala en Google Cloud y vuelve a iniciar sesión."
            reason == "insufficientPermissions" || reason == "forbidden" ->
                "La cuenta no concedió el permiso de YouTube. Vuelve a iniciar sesión y acepta el acceso."
            reason == "quotaExceeded" ->
                "Se agotó la cuota diaria de la API de YouTube Data."
            status == 403 -> "Google rechazó la petición (403${reason?.let { ": $it" } ?: ""})."
            else -> "YouTube Data respondió $status${reason?.let { " ($it)" } ?: ""}."
        }
        return YouTubeApiException(status, reason, if (detail.isBlank()) friendly else "$friendly $detail")
    }
}
