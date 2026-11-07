package com.cypher.music.network.innertube

import com.cypher.music.data.ArtistNormalizer
import com.cypher.music.model.AudioSourceType
import com.cypher.music.network.TitleCleaner
import com.cypher.music.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses a single YouTube Music (WEB_REMIX) browse response.
 *
 * YouTube Music splits long playlists into pages of roughly 100 items; every page but
 * the last ends with a `continuationItemRenderer` carrying a token. The parser extracts
 * that token together with the songs of the page so [InnerTubePager] can request the rest.
 */
object InnerTubePageParser {
    private const val FALLBACK_DURATION_MS = 210_000L

    private val DURATION_PATTERN = Regex("^\\d{1,3}:[0-5]\\d(:[0-5]\\d)?$")
    private val TOTAL_PATTERN = Regex(
        "([\\d][\\d.,\\u00A0\\u202F ]*)\\s*(canciones|canción|cancion|songs?|pistas?|títulos?|titulos?|temas?|tracks?|vídeos?|videos?|items?)",
        RegexOption.IGNORE_CASE
    )

    fun parseTracksPage(root: JSONObject): PageSlice<Track> {
        val tracks = ArrayList<Track>()
        val seenIds = HashSet<String>()
        forEachObject(root, "musicResponsiveListItemRenderer") { renderer ->
            val track = parseTrack(renderer) ?: return@forEachObject
            if (seenIds.add(track.id)) tracks.add(track)
        }
        return PageSlice(tracks, findContinuationToken(root), parseTotalCount(root))
    }

    fun parsePlaylistPage(root: JSONObject): PageSlice<RemotePlaylist> {
        val playlists = ArrayList<RemotePlaylist>()
        val seenIds = HashSet<String>()
        forEachObject(root, "musicTwoRowItemRenderer") { renderer ->
            val playlist = parseTwoRowPlaylist(renderer) ?: return@forEachObject
            if (seenIds.add(playlist.id)) playlists.add(playlist)
        }
        if (playlists.isEmpty()) {
            forEachObject(root, "musicResponsiveListItemRenderer") { renderer ->
                val playlist = parseResponsivePlaylist(renderer) ?: return@forEachObject
                if (seenIds.add(playlist.id)) playlists.add(playlist)
            }
        }
        return PageSlice(playlists, findContinuationToken(root), parseTotalCount(root))
    }

    /**
     * Reads the next-page token. A playlist page keeps it as the last entry of
     * `musicPlaylistShelfRenderer.contents` (and, in some responses, as a legacy
     * `nextContinuationData` beside it); the canonical slot is checked first so a
     * "show more" token from another section is never followed by mistake.
     */
    fun findContinuationToken(root: JSONObject): String? {
        val sections = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
        if (sections != null) {
            for (index in sections.length() - 1 downTo 0) {
                val section = sections.optJSONObject(index) ?: continue
                tokenOf(section.optJSONObject("continuationItemRenderer"))?.let { return it }

                val shelf = section.optJSONObject("musicPlaylistShelfRenderer")
                    ?: section.optJSONObject("musicShelfRenderer")
                val shelfContents = shelf?.optJSONArray("contents")
                if (shelfContents != null) {
                    for (itemIndex in shelfContents.length() - 1 downTo 0) {
                        tokenOf(shelfContents.optJSONObject(itemIndex)?.optJSONObject("continuationItemRenderer"))
                            ?.let { return it }
                    }
                }
            }
        }

        var found: String? = null
        forEachObject(root, "continuationItemRenderer") { renderer ->
            if (found == null) found = tokenOf(renderer)
        }
        if (found != null) return found

        forEachObject(root, "nextContinuationData") { data ->
            if (found == null) found = data.optString("continuation").takeIf { it.isNotBlank() }
        }
        return found
    }

    /** Reads "420 canciones" / "1.234 canciones" from the playlist header when present. */
    fun parseTotalCount(root: JSONObject): Int? {
        var total: Int? = null
        forEachObject(root, "musicResponsiveHeaderRenderer") { header ->
            if (total == null) total = totalFromHeader(header)
        }
        if (total == null) {
            forEachObject(root, "musicDetailHeaderRenderer") { header ->
                if (total == null) total = totalFromHeader(header)
            }
        }
        return total
    }

    private fun tokenOf(continuationItem: JSONObject?): String? = continuationItem
        ?.optJSONObject("continuationEndpoint")
        ?.optJSONObject("continuationCommand")
        ?.optString("token")
        ?.takeIf { it.isNotBlank() }

    private fun totalFromHeader(header: JSONObject): Int? {
        val texts = ArrayList<String>()
        collectRuns(header.optJSONObject("secondSubtitle"), texts)
        if (texts.isEmpty()) collectRuns(header.optJSONObject("subtitle"), texts)

        for (text in texts) {
            val match = TOTAL_PATTERN.find(text) ?: continue
            val value = match.groupValues[1].filter { it.isDigit() }.toIntOrNull()
            if (value != null && value > 0) return value
        }
        return null
    }

    private fun collectRuns(textObject: JSONObject?, into: MutableList<String>) {
        val runs = textObject?.optJSONArray("runs") ?: return
        for (index in 0 until runs.length()) {
            runs.optJSONObject(index)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?.let(into::add)
        }
    }

    private fun parseTrack(renderer: JSONObject): Track? {
        val videoId = videoIdOf(renderer)
        if (videoId.isBlank()) return null

        val columns = renderer.optJSONArray("flexColumns")
        val title = columns?.optJSONObject(0)?.columnTexts()?.firstOrNull().orEmpty()
        if (title.isBlank()) return null

        val artistRuns = columns?.optJSONObject(1)?.columnTexts().orEmpty()
        val artist = artistRuns.firstOrNull { run ->
            !DURATION_PATTERN.matches(run) && run != "•"
        }.orEmpty()

        var durationMs = FALLBACK_DURATION_MS
        searchDuration@ for (index in 0 until (columns?.length() ?: 0)) {
            for (run in columns?.optJSONObject(index)?.columnTexts().orEmpty()) {
                if (DURATION_PATTERN.matches(run)) {
                    durationMs = parseDurationMs(run)
                    break@searchDuration
                }
            }
        }

        val thumbnails = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val artworkUrl = if (thumbnails != null && thumbnails.length() > 0) {
            thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        return Track(
            id = "yt_$videoId",
            title = TitleCleaner.clean(title),
            artist = ArtistNormalizer.normalize(artist.ifBlank { "YouTube Music" }),
            album = "YouTube Music",
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            streamUrl = videoId,
            sourceType = AudioSourceType.YOUTUBE
        )
    }

    private fun videoIdOf(renderer: JSONObject): String =
        renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            .ifBlank { renderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty() }
            .ifBlank {
                renderer.optJSONObject("overlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId").orEmpty()
            }

    private fun parseTwoRowPlaylist(renderer: JSONObject): RemotePlaylist? {
        val browseId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId").orEmpty()
        val title = renderer.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text").orEmpty()
            .ifBlank { renderer.optJSONObject("title")?.optString("simpleText").orEmpty() }
        if (browseId.isBlank() || title.isBlank()) return null
        if (title.equals("Nueva lista", true) || title.equals("New playlist", true)) return null

        val thumbnails = renderer.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val coverUrl = if (thumbnails != null && thumbnails.length() > 0) {
            thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
        } else {
            null
        }

        return RemotePlaylist(
            id = browseId,
            title = title,
            description = "Sincronizado desde YouTube Music",
            coverUrl = coverUrl
        )
    }

    private fun parseResponsivePlaylist(renderer: JSONObject): RemotePlaylist? {
        val browseId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId").orEmpty()
        val title = renderer.optJSONArray("flexColumns")
            ?.optJSONObject(0)
            ?.columnTexts()
            ?.firstOrNull().orEmpty()
        if (browseId.isBlank() || title.isBlank()) return null
        if (!browseId.startsWith("VLPL") && !browseId.startsWith("FEmusic_")) return null

        return RemotePlaylist(
            id = browseId,
            title = title,
            description = "Sincronizado desde YouTube Music"
        )
    }

    private fun JSONObject.columnTexts(): List<String> {
        val runs = optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?: return emptyList()
        val texts = ArrayList<String>(runs.length())
        for (index in 0 until runs.length()) {
            runs.optJSONObject(index)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?.let(texts::add)
        }
        return texts
    }

    private fun parseDurationMs(text: String): Long {
        var seconds = 0L
        for (part in text.split(":")) {
            seconds = seconds * 60 + (part.toLongOrNull() ?: 0L)
        }
        return seconds * 1000L
    }

    private fun forEachObject(node: Any?, key: String, action: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val value = node.opt(name)
                    if (name == key && value is JSONObject) action(value)
                    if (value is JSONObject || value is JSONArray) forEachObject(value, key, action)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    forEachObject(node.opt(index), key, action)
                }
            }
        }
    }
}
