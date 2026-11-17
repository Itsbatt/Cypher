package com.cypher.music.network.streaming

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxiedStreamResolverTest {

    @Test
    fun `resolve returns null when instanceUrl is blank`() = runBlocking {
        val resolver = ProxiedStreamResolver(instanceProvider = { "" })
        assertNull(resolver.resolve("video123"))
    }

    @Test
    fun `resolve returns null when videoId is blank`() = runBlocking {
        val resolver = ProxiedStreamResolver(instanceProvider = { "https://piped.example" })
        assertNull(resolver.resolve(""))
    }

    @Test
    fun `resolve returns Piped stream when streams endpoint responds 200`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath == "/streams/pipedVideo") {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"audioStreams":[{"url":"https://audio.piped/play","bitrate":160000}]}"""
                                .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val resolver = ProxiedStreamResolver(
            instanceProvider = { "https://piped.example" },
            client = client
        )

        val result = resolver.resolve("pipedVideo")
        assertEquals("https://audio.piped/play", result?.url)
        assertEquals(StreamSource.PIPED, result?.source)
        assertEquals(4, result?.tier)
    }

    @Test
    fun `resolve falls back to Invidious when Piped returns 404`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath == "/api/v1/videos/invidiousVideo") {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"adaptiveFormats":[{"type":"audio/webm","bitrate":"160000","url":"https://audio.invidious/play"}]}"""
                                .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val resolver = ProxiedStreamResolver(
            instanceProvider = { "https://invidious.example" },
            client = client
        )

        val result = resolver.resolve("invidiousVideo")
        assertEquals("https://audio.invidious/play", result?.url)
        assertEquals(StreamSource.INVIDIOUS, result?.source)
        assertEquals(4, result?.tier)
    }

    @Test
    fun `resolve returns null without throwing when network fails`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor {
                throw java.io.IOException("Connection refused")
            }
            .build()

        val resolver = ProxiedStreamResolver(
            instanceProvider = { "https://broken.instance" },
            client = client
        )

        assertNull(resolver.resolve("videoFail"))
    }
}
