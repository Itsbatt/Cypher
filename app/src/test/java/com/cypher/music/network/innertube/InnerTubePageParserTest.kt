package com.cypher.music.network.innertube

import com.cypher.music.model.AudioSourceType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubePageParserTest {

    @Test
    fun `parses tracks with title, artist, duration and artwork in order`() {
        val page = pageOf(
            track("aaaaaaaaaaa", "Canción Uno (Official Video)", "Milo J Music", "3:45"),
            track("bbbbbbbbbbb", "Canción Dos", "Duki", "4:02")
        )

        val parsed = InnerTubePageParser.parseTracksPage(page)

        assertEquals(listOf("yt_aaaaaaaaaaa", "yt_bbbbbbbbbbb"), parsed.items.map { it.id })
        assertEquals(listOf("Canción Uno", "Canción Dos"), parsed.items.map { it.title })
        assertEquals(listOf("Milo J", "Duki"), parsed.items.map { it.artist })
        assertEquals(listOf(225_000L, 242_000L), parsed.items.map { it.durationMs })
        assertEquals("https://img.test/aaaaaaaaaaa.jpg", parsed.items[0].artworkUrl)
        assertEquals("aaaaaaaaaaa", parsed.items[0].streamUrl)
        assertEquals(AudioSourceType.YOUTUBE, parsed.items[0].sourceType)
        assertNull(parsed.continuation)
        assertNull(parsed.totalCount)
    }

    @Test
    fun `extracts the continuation token of a full page`() {
        val page = pageOf(track("aaaaaaaaaaa", "Uno", "Artista", "3:00"), token = "TOKEN-NEXT")

        val parsed = InnerTubePageParser.parseTracksPage(page)

        assertEquals("TOKEN-NEXT", parsed.continuation)
        assertEquals(1, parsed.items.size)
    }

    @Test
    fun `reads the playlist total from the header`() {
        val page = pageOf(track("aaaaaaaaaaa", "Uno", "Artista", "3:00"), total = "420 canciones")
        assertEquals(420, InnerTubePageParser.parseTracksPage(page).totalCount)

        val withSeparator = pageOf(total = "1.234 canciones")
        assertEquals(1234, InnerTubePageParser.parseTracksPage(withSeparator).totalCount)
    }

    @Test
    fun `deduplicates repeated video ids on the same page`() {
        val page = pageOf(
            track("aaaaaaaaaaa", "Uno", "Artista", "3:00"),
            track("aaaaaaaaaaa", "Uno repetida", "Artista", "3:00"),
            track("bbbbbbbbbbb", "Dos", "Artista", "3:10")
        )

        val parsed = InnerTubePageParser.parseTracksPage(page)

        assertEquals(listOf("yt_aaaaaaaaaaa", "yt_bbbbbbbbbbb"), parsed.items.map { it.id })
        assertEquals("Uno", parsed.items[0].title)
    }

    @Test
    fun `ignores playlist cards when parsing a track page`() {
        val page = pageOf(playlistItem("VLPLfake", "Mix de prueba"))

        assertTrue(InnerTubePageParser.parseTracksPage(page).items.isEmpty())
    }

    @Test
    fun `reads the real playlist shelf shape and its pistas total`() {
        val page = playlistShelfPage(
            totalText = "200 pistas",
            token = "SHELF-TOKEN",
            renderers = arrayOf(
                track("aaaaaaaaaaa", "Uno", "Artista", "3:00"),
                track("bbbbbbbbbbb", "Dos", "Artista", "3:10")
            )
        )

        val parsed = InnerTubePageParser.parseTracksPage(page)

        assertEquals("SHELF-TOKEN", parsed.continuation)
        assertEquals(200, parsed.totalCount)
        assertEquals(2, parsed.items.size)
    }

    @Test
    fun `falls back to the legacy nextContinuationData token`() {
        val page = playlistShelfPage(
            totalText = "50 pistas",
            token = null,
            legacyToken = "LEGACY-TOKEN",
            renderers = arrayOf(track("aaaaaaaaaaa", "Uno", "Artista", "3:00"))
        )

        assertEquals("LEGACY-TOKEN", InnerTubePageParser.parseTracksPage(page).continuation)
    }

    @Test
    fun `parses playlist metadata pages with cover, title and continuation`() {
        val page = pageOf(
            playlistItem("VLPLuno", "Playlist Uno"),
            playlistItem("VLPLdos", "Playlist Dos"),
            token = "PLAYLISTS-NEXT"
        )

        val parsed = InnerTubePageParser.parsePlaylistPage(page)

        assertEquals(listOf("VLPLuno", "VLPLdos"), parsed.items.map { it.id })
        assertEquals(listOf("Playlist Uno", "Playlist Dos"), parsed.items.map { it.title })
        assertEquals("https://img.test/cover.jpg", parsed.items[0].coverUrl)
        assertEquals("PLAYLISTS-NEXT", parsed.continuation)
    }

    private fun pageOf(
        vararg renderers: JSONObject,
        token: String? = null,
        total: String? = null
    ): JSONObject {
        val contents = JSONArray()
        if (total != null) contents.put(header(total))

        val shelfContents = JSONArray()
        for (renderer in renderers) shelfContents.put(renderer)
        contents.put(JSONObject().put("musicShelfRenderer", JSONObject().put("contents", shelfContents)))
        if (token != null) contents.put(continuation(token))

        return JSONObject().put(
            "contents",
            JSONObject().put(
                "singleColumnBrowseResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put("contents", contents)
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun playlistShelfPage(
        totalText: String,
        token: String?,
        legacyToken: String? = null,
        renderers: Array<JSONObject>
    ): JSONObject {
        val shelfContents = JSONArray()
        for (renderer in renderers) shelfContents.put(renderer)
        if (token != null) shelfContents.put(continuation(token))

        val shelf = JSONObject().put("contents", shelfContents)
        if (legacyToken != null) {
            shelf.put(
                "continuations",
                JSONArray().put(
                    JSONObject().put(
                        "nextContinuationData",
                        JSONObject().put("continuation", legacyToken)
                    )
                )
            )
        }

        val contents = JSONArray()
            .put(header(totalText))
            .put(JSONObject().put("musicPlaylistShelfRenderer", shelf))

        return JSONObject().put(
            "contents",
            JSONObject().put(
                "singleColumnBrowseResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put("contents", contents)
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun header(totalText: String): JSONObject = JSONObject().put(
        "musicResponsiveHeaderRenderer",
        JSONObject().put(
            "secondSubtitle",
            JSONObject().put("runs", JSONArray().put(JSONObject().put("text", totalText)))
        )
    )

    private fun continuation(token: String): JSONObject = JSONObject().put(
        "continuationItemRenderer",
        JSONObject().put(
            "continuationEndpoint",
            JSONObject().put("continuationCommand", JSONObject().put("token", token))
        )
    )

    private fun column(text: String): JSONObject = JSONObject().put(
        "musicResponsiveListItemFlexColumnRenderer",
        JSONObject().put(
            "text",
            JSONObject().put("runs", JSONArray().put(JSONObject().put("text", text)))
        )
    )

    private fun track(videoId: String, title: String, artist: String, duration: String): JSONObject =
        JSONObject().put(
            "musicResponsiveListItemRenderer",
            JSONObject()
                .put("playlistItemData", JSONObject().put("videoId", videoId))
                .put(
                    "flexColumns",
                    JSONArray().put(column(title)).put(column(artist)).put(column(duration))
                )
                .put(
                    "thumbnail",
                    JSONObject().put(
                        "musicThumbnailRenderer",
                        JSONObject().put(
                            "thumbnail",
                            JSONObject().put(
                                "thumbnails",
                                JSONArray().put(JSONObject().put("url", "https://img.test/$videoId.jpg"))
                            )
                        )
                    )
                )
        )

    private fun playlistItem(browseId: String, title: String): JSONObject = JSONObject().put(
        "musicTwoRowItemRenderer",
        JSONObject()
            .put("title", JSONObject().put("runs", JSONArray().put(JSONObject().put("text", title))))
            .put(
                "navigationEndpoint",
                JSONObject().put("browseEndpoint", JSONObject().put("browseId", browseId))
            )
            .put(
                "thumbnailRenderer",
                JSONObject().put(
                    "musicThumbnailRenderer",
                    JSONObject().put(
                        "thumbnail",
                        JSONObject().put(
                            "thumbnails",
                            JSONArray().put(JSONObject().put("url", "https://img.test/cover.jpg"))
                        )
                    )
                )
            )
    )
}
