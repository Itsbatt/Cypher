package com.cypher.desktop

import com.cypher.desktop.core.DailyStat
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.Track
import com.cypher.desktop.core.WrappedBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WrappedTest {

    private fun stat(day: String, id: String, artist: String, plays: Int, ms: Long, title: String = "T$id") =
        DailyStat(day, id, ms, plays, title, artist)

    private val data = LibraryData(
        tracks = listOf(Track("t1", "Uno", "A", path = "C:/m/1.mp3")),
        stats = listOf(
            stat("2026-01-02", "t1", "Milo J", 4, 600_000),
            stat("2026-03-10", "t2", "Milo J", 2, 300_000),
            stat("2026-03-10", "t3", "Cerati", 5, 900_000),
            stat("2025-12-31", "t4", "Viejo", 9, 9_000_000)
        )
    )

    @Test
    fun `only the requested year is counted`() {
        val summary = WrappedBuilder.build(data, 2026)
        assertEquals(1_800_000, summary.totalListeningMs)
        assertEquals(11, summary.totalPlays)
        assertEquals(3, summary.uniqueTracks)
        assertTrue(summary.hasData)
    }

    @Test
    fun `top artist accumulates and carries its share`() {
        val summary = WrappedBuilder.build(data, 2026)
        // Milo J and Cerati tie on time (900 000 ms each); the tie-break is play count.
        assertEquals("Milo J", summary.topArtist?.name)
        assertEquals(1, summary.topArtist?.rank)
        assertEquals(0.5f, summary.topArtist!!.share, 0.001f)
        assertEquals(2, summary.topArtists.size)
        assertEquals(900_000, summary.topArtists.last().totalTimeMs)
    }

    @Test
    fun `top tracks are ranked by listening time`() {
        val summary = WrappedBuilder.build(data, 2026)
        assertEquals(listOf("t3", "t1", "t2"), summary.topTracks.map { it.trackId })
        assertEquals(1, summary.topTracks.first().rank)
    }

    @Test
    fun `most repeated and peak day come from real rows`() {
        val summary = WrappedBuilder.build(data, 2026)
        assertEquals("t3", summary.mostRepeated?.trackId)
        assertEquals("2026-03-10", summary.peakDay?.day)
        assertEquals(1_200_000, summary.peakDay?.totalTimeMs)
    }

    @Test
    fun `metadata falls back to the library when the stat has no title`() {
        val withBlankStat = data.copy(stats = listOf(stat("2026-05-05", "t1", "A", 1, 1000, title = "")))
        val summary = WrappedBuilder.build(withBlankStat, 2026)
        assertEquals("Uno", summary.topTracks.first().title)
        assertEquals("A", summary.topTracks.first().artist)
        assertNotNull(summary.topArtist)
    }

    @Test
    fun `empty year is not invented`() {
        val summary = WrappedBuilder.build(data, 2024)
        assertTrue(!summary.hasData)
        assertTrue(summary.topTracks.isEmpty())
    }

    @Test
    fun `available years are real and newest first`() {
        assertEquals(listOf(2026, 2025), WrappedBuilder.availableYears(data.stats))
    }
}
