package com.cypher.music.network.innertube

import com.cypher.music.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Opt-in check against the real YouTube Music API. It is skipped unless
 * CYPHER_LIVE_YTMUSIC=1, so the normal suite stays offline and deterministic.
 */
class LiveInnerTubePaginationTest {

    private val playlistId = "PLMC9KNkIncKtPzgY-5rmhvj7fax8fdxoj"

    @Test
    fun `walks a public playlist past its first page`() = runTest {
        assumeTrue("Define CYPHER_LIVE_YTMUSIC=1 para la prueba real", System.getenv("CYPHER_LIVE_YTMUSIC") == "1")

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val result = InnerTubePager(retryDelayMs = 1_500, sleep = { delay(it) })
            .paginate({ continuation -> fetchPage(client, continuation) }, { it.id })

        println(
            "LIVE playlist=$playlistId tracks=${result.items.size} pages=${result.pagesFetched} " +
                "complete=${result.complete} total=${result.totalCount} error=${result.error}"
        )

        assertTrue("La playlist debe superar la primera página", result.items.size > 100)
        assertTrue("La paginación debe terminar sola", result.complete)
        assertTrue("Debe haber más de una página", result.pagesFetched >= 2)
        assertEquals(
            "Sin duplicados",
            result.items.size,
            result.items.map { it.id }.distinct().size
        )
    }

    private fun fetchPage(client: OkHttpClient, continuation: String?): PageSlice<Track> {
        val body = JSONObject().apply {
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240901.01.00")
                        put("hl", "es")
                        put("gl", "ES")
                    }
                )
            )
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("browseId", "VL$playlistId")
            }
        }

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20240901.01.00")
            .header("X-Goog-AuthUser", "0")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            )
            .header("Cookie", "SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; CONSENT=YES+")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            assertTrue("HTTP ${response.code}", response.isSuccessful)
            val text = response.body?.string().orEmpty()
            assertTrue("Respuesta vacía", text.isNotBlank())
            return InnerTubePageParser.parseTracksPage(JSONObject(text))
        }
    }
}
