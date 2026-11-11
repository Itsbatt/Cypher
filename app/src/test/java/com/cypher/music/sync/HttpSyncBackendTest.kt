package com.cypher.music.sync

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transport tests with a stubbed OkHttp interceptor: no socket, no backend.
 *
 * The important contract is that a 4xx reaches SyncEngine as [SyncRejectedException]
 * (not retried, not shown as "no connection") while transport failures and 5xx stay
 * [SyncUnavailableException] (transient).
 */
class HttpSyncBackendTest {

    private val base = "https://sync.test/functions/v1/sync"
    private val sub = "100305844804415678846"
    private val token = "token-de-prueba"
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun responseFor(code: Int, body: String): Response = Response.Builder()
        .request(Request.Builder().url("$base/v1/sync/pull").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("prueba")
        .body(body.toResponseBody(json))
        .build()

    private fun backendWith(handler: () -> Response): Pair<HttpSyncBackend, AtomicInteger> {
        val calls = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { _ ->
                    calls.incrementAndGet()
                    handler()
                }
            )
            .build()
        return HttpSyncBackend(baseUrl = base, client = client) to calls
    }

    @Test
    fun `401 is a rejection, never a network failure, and is not retried`() = runBlocking {
        val (backend, calls) = backendWith { responseFor(401, """{"error":"invalid_azp"}""") }

        val error = runCatching { backend.pull(sub, token, null, 100) }.exceptionOrNull()

        assertTrue("se esperaba SyncRejectedException, llegó $error", error is SyncRejectedException)
        assertEquals(401, (error as SyncRejectedException).code)
        assertTrue(error.message.orEmpty().contains("401"))
        assertFalse("un 401 no puede viajar como error de red", error is SyncUnavailableException)
        assertEquals("no debe reintentar en el transporte", 1, calls.get())
    }

    @Test
    fun `403 on push is a rejection too`() = runBlocking {
        val (backend, calls) = backendWith { responseFor(403, """{"error":"account_mismatch"}""") }
        val record = RemoteRecord(SyncEntityKind.PLAYLIST, "p1", 10L, false, mapOf("name" to "x"))

        val error = runCatching { backend.push(sub, token, listOf(record)) }.exceptionOrNull()

        assertTrue(error is SyncRejectedException)
        assertEquals(403, (error as SyncRejectedException).code)
        assertEquals(1, calls.get())
    }

    @Test
    fun `5xx stays transient so it can be retried`() = runBlocking {
        val (backend, _) = backendWith { responseFor(502, """{"error":"upstream_error"}""") }

        val error = runCatching { backend.pull(sub, token, null, 100) }.exceptionOrNull()

        assertTrue("se esperaba SyncUnavailableException, llegó $error", error is SyncUnavailableException)
    }

    @Test
    fun `transport failure is unavailable and keeps the cause`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { _ -> throw IOException("sin red") })
            .build()
        val backend = HttpSyncBackend(baseUrl = base, client = client)

        val error = runCatching { backend.pull(sub, token, null, 100) }.exceptionOrNull()

        assertTrue(error is SyncUnavailableException)
        assertTrue(error?.cause is IOException)
    }

    @Test
    fun `a 200 page is decoded`() = runBlocking {
        val body = """
            {"records":[{"kind":"FAVORITE","id":"t1","updatedAt":5,"deleted":false,
            "payload":{"title":"Canción"}}],"cursor":"9","hasMore":false}
        """.trimIndent()
        val (backend, _) = backendWith { responseFor(200, body) }

        val page = backend.pull(sub, token, null, 100)

        assertEquals(1, page.records.size)
        assertEquals("t1", page.records.first().id)
        assertEquals("Canción", page.records.first().payload["title"])
        assertEquals("9", page.cursor)
        assertFalse(page.hasMore)
    }

    @Test
    fun `a 200 with invalid JSON is unavailable, not a rejection`() = runBlocking {
        val (backend, _) = backendWith { responseFor(200, "<html>portal cautivo</html>") }

        val error = runCatching { backend.pull(sub, token, null, 100) }.exceptionOrNull()

        assertTrue(error is SyncUnavailableException)
    }

    @Test
    fun `an empty push does not reach the network`() = runBlocking {
        val (backend, calls) = backendWith { responseFor(200, "{}") }

        val result = backend.push(sub, token, emptyList())

        assertEquals(0, calls.get())
        assertTrue(result.accepted.isEmpty())
    }
}
