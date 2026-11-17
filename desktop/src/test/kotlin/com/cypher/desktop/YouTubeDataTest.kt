package com.cypher.desktop

import com.cypher.desktop.core.YouTubeData
import com.cypher.desktop.core.YouTubePlaylistSummary
import com.cypher.desktop.importers.YouTubeOAuth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parsing and error mapping for the official YouTube Data client, without network. */
class YouTubeDataTest {

    @AfterTest
    fun tearDown() {
        YouTubeData.transport = { _, _ -> error("default transport must not run in tests") }
    }

    @Test
    fun `signed in flow asks for the youtube read only scope`() {
        assertTrue(YouTubeOAuth.SCOPE.endsWith("youtube.readonly"))
        assertTrue(YouTubeOAuth.SCOPE.contains("openid"))
        assertTrue(!YouTubeOAuth.SCOPE.contains("auth/youtube "))
    }

    @Test
    fun `playlists page keeps title cover count and next token`() {
        val raw = """
            {"items":[
              {"id":"PLabc","snippet":{"title":"Noche","description":"dormir","channelTitle":"Cypher",
               "thumbnails":{"default":{"url":"https://img/s.jpg"},"high":{"url":"https://img/l.jpg"}}},
               "contentDetails":{"itemCount":12}},
              {"id":"PLdef","snippet":{"title":"Fiesta","thumbnails":{}}}
            ],"nextPageToken":"TOKEN2"}
        """.trimIndent()
        val page = YouTubeData.parsePlaylists(raw)
        assertEquals(listOf("PLabc", "PLdef"), page.items.map { it.id })
        assertEquals("Noche", page.items.first().title)
        assertEquals("https://img/l.jpg", page.items.first().cover)
        assertEquals(12, page.items.first().itemCount)
        assertEquals("TOKEN2", page.nextPageToken)
        assertEquals(0, page.items[1].itemCount)
    }

    @Test
    fun `items keep order and strip the Topic suffix`() {
        val raw = """
            {"items":[
              {"contentDetails":{"videoId":"aaaaaaaaaaa"},
               "snippet":{"title":"Uno","videoOwnerChannelTitle":"Milo j - Topic","thumbnails":{"high":{"url":"https://i/1.jpg"}}}},
              {"contentDetails":{"videoId":"bbbbbbbbbbb"},
               "snippet":{"title":"Dos","channelTitle":"Otro"}},
              {"contentDetails":{"videoId":"bad"},
               "snippet":{"title":"Ignorada"}}
            ],"nextPageToken":""}
        """.trimIndent()
        val page = YouTubeData.parseItems(raw)
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), page.items.map { it.videoId })
        assertEquals("Milo j", page.items.first().artist)
        assertEquals("Otro", page.items[1].artist)
        assertEquals(null, page.nextPageToken)
    }

    @Test
    fun `pagination walks every page and removes duplicates`() {
        var calls = 0
        YouTubeData.transport = { url, _ ->
            calls++
            if (url.contains("pageToken=P2")) {
                """{"items":[{"id":"PL3","snippet":{"title":"Tres"}}]}"""
            } else if (url.contains("pageToken=P1")) {
                """{"items":[{"id":"PL2","snippet":{"title":"Dos"}},{"id":"PL1","snippet":{"title":"Repetida"}}],"nextPageToken":"P2"}"""
            } else {
                """{"items":[{"id":"PL1","snippet":{"title":"Uno"}}],"nextPageToken":"P1"}"""
            }
        }
        val lists = YouTubeData.allPlaylists("token")
        assertEquals(listOf("PL1", "PL2", "PL3"), lists.map { it.id })
        assertEquals(3, calls)
    }

    @Test
    fun `a missing api is explained not dumped as 403`() {
        val error = YouTubeData.apiError(
            403,
            """{"error":{"code":403,"message":"YouTube Data API has not been used","errors":[{"reason":"accessNotConfigured"}]}}"""
        )
        assertTrue(error.message!!.contains("no está habilitada"))
        assertTrue(error.message!!.contains("YouTube Data API has not been used"))
    }

    @Test
    fun `expired session asks to sign in again`() {
        val error = YouTubeData.apiError(401, """{"error":{"code":401,"message":"Invalid Credentials"}}""")
        assertTrue(error.message!!.contains("caducó"))
    }

    @Test
    fun `api list becomes the same shape the importer already understands`() {
        val summary = YouTubePlaylistSummary("PLabc", "Noche", "dormir", "https://img/l.jpg", 2)
        val page = YouTubeData.parseItems(
            """{"items":[{"contentDetails":{"videoId":"aaaaaaaaaaa"},"snippet":{"title":"Uno","channelTitle":"A"}}]}"""
        )
        val list = YouTubeData.toMusicList(summary, page.items)
        assertEquals("Noche", list.title)
        assertEquals("dormir", list.description)
        assertEquals("https://img/l.jpg", list.cover)
        assertEquals("yt:PLabc", list.sourceKey)
        assertEquals(1, list.hits.size)
    }
}
