package com.cypher.music.network.innertube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class InnerTubePagerTest {

    private fun ids(count: Int, start: Int = 0): List<String> = (start until start + count).map { "yt_$it" }

    private class FakeFetcher(private val pages: List<List<String>>, private val totalCount: Int? = null) {
        var calls = 0
        val requestedContinuations = mutableListOf<String?>()

        suspend fun fetch(continuation: String?): PageSlice<String> {
            calls++
            requestedContinuations.add(continuation)
            val index = continuation?.removePrefix("page_")?.toIntOrNull() ?: 0
            require(index < pages.size) { "página fuera de rango: $index" }
            val next = if (index + 1 < pages.size) "page_${index + 1}" else null
            return PageSlice(pages[index], next, totalCount)
        }
    }

    @Test
    fun `imports every page of playlists with 50, 100, 150, 420 and 1000 tracks`() = runTest {
        for (count in listOf(50, 100, 150, 420, 1000)) {
            val pageList = ids(count).chunked(100)
            val fetcher = FakeFetcher(pageList, count)
            val result = InnerTubePager(sleep = {}).paginate(fetcher::fetch, { it })

            assertEquals("tamaño para $count", count, result.items.size)
            assertEquals("orden para $count", ids(count), result.items)
            assertEquals("páginas para $count", pageList.size, result.pagesFetched)
            assertEquals("total para $count", count, result.totalCount)
            assertTrue("completa para $count", result.complete)
            assertEquals(null, result.error)
        }
    }

    @Test
    fun `stops at the last page instead of requesting a fixed number of pages`() = runTest {
        val fetcher = FakeFetcher(ids(120).chunked(50), 120)
        val result = InnerTubePager(sleep = {}).paginate(fetcher::fetch, { it })

        assertEquals(3, result.pagesFetched)
        assertEquals(3, fetcher.calls)
        assertEquals(listOf(null, "page_1", "page_2"), fetcher.requestedContinuations)
        assertEquals(120, result.items.size)
    }

    @Test
    fun `deduplicates overlapping pages without changing the original order`() = runTest {
        val pages = listOf(
            listOf("a", "b", "c"),
            listOf("b", "c", "d", "e"),
            listOf("a", "f")
        )
        val result = InnerTubePager(sleep = {}).paginate(FakeFetcher(pages)::fetch, { it })

        assertEquals(listOf("a", "b", "c", "d", "e", "f"), result.items)
        assertTrue(result.complete)
    }

    @Test
    fun `retries a transient failure and keeps fetching the following pages`() = runTest {
        var attempts = 0
        val delays = mutableListOf<Long>()
        val pager = InnerTubePager(maxAttemptsPerPage = 3, retryDelayMs = 100, sleep = { delays += it })

        val result = pager.paginate({ continuation ->
            attempts++
            if (attempts == 1) throw IOException("sin conexión")
            if (continuation == null) PageSlice(listOf("a", "b"), "next") else PageSlice(listOf("c"))
        }, { it })

        assertEquals(3, attempts)
        assertEquals(listOf(100L), delays)
        assertEquals(listOf("a", "b", "c"), result.items)
        assertTrue(result.complete)
    }

    @Test
    fun `keeps the fetched pages and reports an incomplete sync when a page keeps failing`() = runTest {
        val result = InnerTubePager(maxAttemptsPerPage = 2, sleep = {}).paginate({ continuation ->
            if (continuation == null) PageSlice(listOf("a", "b"), "next") else throw IOException("sin red")
        }, { it })

        assertEquals(listOf("a", "b"), result.items)
        assertEquals(1, result.pagesFetched)
        assertFalse(result.complete)
        assertEquals("sin red", result.error)
    }

    @Test
    fun `flags a repeated continuation token instead of looping forever`() = runTest {
        val result = InnerTubePager(sleep = {}).paginate({ PageSlice(listOf("a"), "same") }, { it })

        assertEquals(listOf("a"), result.items)
        assertEquals(2, result.pagesFetched)
        assertFalse(result.complete)
        assertTrue(result.error!!.contains("repetido"))
    }

    @Test
    fun `reports processed tracks against the total for the progress label`() = runTest {
        val progress = mutableListOf<Pair<Int, Int?>>()
        val pages = ids(120).chunked(40)

        InnerTubePager(sleep = {}).paginate(
            fetchPage = FakeFetcher(pages, 120)::fetch,
            idOf = { it },
            onProgress = { processed, total -> progress += processed to total }
        )

        assertEquals(listOf(40 to 120, 80 to 120, 120 to 120), progress)
    }

    @Test
    fun `propagates cancellation instead of retrying it`() = runTest {
        var calls = 0
        try {
            InnerTubePager(sleep = {}).paginate<String>({
                calls++
                throw CancellationException("cancelado")
            }, { it })
            fail("La cancelación debe propagarse")
        } catch (expected: CancellationException) {
            assertEquals(1, calls)
        }
    }
}
