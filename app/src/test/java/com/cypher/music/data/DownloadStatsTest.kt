package com.cypher.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatsTest {

    @Test
    fun `sizes below one kilobyte stay in bytes`() {
        assertEquals("0 B", formatDownloadSize(0))
        assertEquals("975 B", formatDownloadSize(975))
        assertEquals("1023 B", formatDownloadSize(1023))
    }

    @Test
    fun `sizes step up by 1024`() {
        assertEquals("1.0 KB", formatDownloadSize(1024))
        assertEquals("500 KB", formatDownloadSize(512_000))
        assertEquals("1.0 MB", formatDownloadSize(1024L * 1024))
        assertEquals("3.5 MB", formatDownloadSize((3.5 * 1024 * 1024).toLong()))
        assertEquals("1.5 GB", formatDownloadSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `negative sizes are treated as empty`() {
        assertEquals("0 B", formatDownloadSize(-42))
    }

    @Test
    fun `summary describes empty and filled states`() {
        assertTrue(DownloadStats().isEmpty)
        assertEquals("Sin descargas", describeDownloadStats(DownloadStats()))

        val one = DownloadStats(fileCount = 1, totalBytes = 2048)
        assertFalse(one.isEmpty)
        assertEquals("1 archivo · 2.0 KB", describeDownloadStats(one))

        val many = DownloadStats(fileCount = 4, totalBytes = 5L * 1024 * 1024)
        assertEquals("4 archivos · 5.0 MB", describeDownloadStats(many))
    }

    @Test
    fun `active downloads are never deletable`() {
        val downloaded = listOf("yt_aaa", "yt_bbb", "yt_ccc")
        val active = setOf("yt_bbb")
        assertEquals(listOf("yt_aaa", "yt_ccc"), deletableDownloadIds(downloaded, active))
    }

    @Test
    fun `deletable list is distinct and empty when everything is in flight`() {
        assertEquals(
            listOf("yt_a", "yt_b"),
            deletableDownloadIds(listOf("yt_a", "yt_b", "yt_a"), emptySet())
        )
        assertTrue(deletableDownloadIds(listOf("yt_a"), setOf("yt_a")).isEmpty())
        assertTrue(deletableDownloadIds(emptyList(), emptySet()).isEmpty())
    }
}
