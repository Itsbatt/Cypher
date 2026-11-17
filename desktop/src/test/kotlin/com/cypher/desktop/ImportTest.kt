package com.cypher.desktop

import com.cypher.desktop.core.ImportPlan
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.MatchQuality
import com.cypher.desktop.core.MusicHit
import com.cypher.desktop.core.Playlist
import com.cypher.desktop.core.RemoteTrack
import com.cypher.desktop.core.SpotifyCsv
import com.cypher.desktop.core.SpotifyImportApply
import com.cypher.desktop.core.SpotifyMatcher
import com.cypher.desktop.core.SpotifyRow
import com.cypher.desktop.core.SpotifyZip
import com.cypher.desktop.core.Track
import com.cypher.desktop.core.YouTubeImport
import com.cypher.desktop.core.YouTubeMusic
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportTest {

    private fun csv(text: String, name: String = "playlist.csv") = SpotifyCsv.table(name, text.toByteArray())

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun library(vararg tracks: Track) = LibraryData(tracks = tracks.toList())

    private fun track(id: String, title: String, artist: String, durationMs: Long = 200_000) =
        Track(id, title, artist, "Album", durationMs, "C:\\music\\$id.mp3")

    // ------------------------------------------------------------------ CSV

    @Test
    fun `utf8 csv with bom and commas inside quotes`() {
        val raw = "\uFEFFTrack Name,Artist Name(s),Album Name,Duration (ms),ISRC\n" +
            "\"Hello, World\",\"A, B\",Album,215000,US1234567890\n"
        val table = SpotifyCsv.table("list.csv", raw.toByteArray(Charsets.UTF_8))
        assertNotNull(table)
        val rows = SpotifyCsv.rowsOf(table, "Lista")
        assertEquals(1, rows.size)
        assertEquals("Hello, World", rows[0].title)
        assertEquals("A, B", rows[0].artist)
        assertEquals(215000, rows[0].durationMs)
        assertEquals("US1234567890", rows[0].isrc)
    }

    @Test
    fun `semicolon and tab separators are detected`() {
        listOf(';', '\t').forEach { separator ->
            val text = "Track Name${separator}Artist Name${separator}Album\nSong${separator}Artist${separator}Disc"
            val rows = SpotifyCsv.rowsOf(SpotifyCsv.table("x.csv", text.toByteArray())!!, "X")
            assertEquals("Song", rows.single().title, "separator $separator")
            assertEquals("Artist", rows.single().artist)
        }
    }

    @Test
    fun `doubled quotes survive`() {
        val text = "Track Name,Artist\n\"Say \"\"Hi\"\"\",Artist"
        val rows = SpotifyCsv.rowsOf(SpotifyCsv.table("q.csv", text.toByteArray())!!, "Q")
        assertEquals("Say \"Hi\"", rows.single().title)
    }

    @Test
    fun `windows-1252 bytes are decoded`() {
        val bytes = "Track Name,Artist\nCanción,José".toByteArray(charset("windows-1252"))
        val table = SpotifyCsv.table("latin.csv", bytes)!!
        val rows = SpotifyCsv.rowsOf(table, "Latin")
        assertEquals("Canción", rows.single().title)
        assertEquals("José", rows.single().artist)
    }

    @Test
    fun `missing optional columns and incomplete rows are kept`() {
        val text = "Track Name\nOnly a title\n,No title here\nAnother"
        val rows = SpotifyCsv.rowsOf(SpotifyCsv.table("m.csv", text.toByteArray())!!, "Minimal")
        assertEquals(listOf("Only a title", "Another"), rows.map { it.title })
        assertTrue(rows.all { it.artist.isEmpty() })
    }

    @Test
    fun `special characters and emoji are preserved`() {
        val text = "Track Name,Artist Name\n日本 の 歌,Ωmega 🎵"
        val rows = SpotifyCsv.rowsOf(SpotifyCsv.table("s.csv", text.toByteArray())!!, "S")
        assertEquals("日本 の 歌", rows.single().title)
        assertEquals("Ωmega 🎵", rows.single().artist)
    }

    @Test
    fun `playlist column groups rows and keeps file order`() {
        val text = "Playlist Name,Track Name,Artist Name\nChill,One,A\nChill,Two,B\nWork,Three,C"
        val rows = SpotifyCsv.rowsOf(SpotifyCsv.table("p.csv", text.toByteArray())!!, "Fallback")
        val plan = SpotifyMatcher.plan(rows, LibraryData())
        assertEquals(listOf("Chill", "Work"), plan.playlists.map { it.name })
        assertEquals(listOf("One", "Two"), plan.playlists.first().entries.map { it.row.title })
        assertEquals(0, plan.playlists.first().entries.first().row.position)
        assertEquals(1, plan.playlists.first().entries.last().row.position)
    }

    @Test
    fun `filename is the playlist when there is no column`() {
        val rows = SpotifyCsv.rowsOf(
            SpotifyCsv.table("Liked Songs.csv", "Track Name,Artist\nA,B".toByteArray())!!,
            SpotifyCsv.fallbackName("Liked Songs.csv")
        )
        assertEquals("Canciones que te gustan", rows.single().playlist)
    }

    @Test
    fun `duration accepts columns and clock formats`() {
        assertEquals(215000, SpotifyCsv.parseDuration("215000"))
        assertEquals(225000, SpotifyCsv.parseDuration("3:45"))
        assertEquals(3_723_000, SpotifyCsv.parseDuration("1:02:03"))
        assertEquals(0, SpotifyCsv.parseDuration(""))
    }

    // ------------------------------------------------------------------ ZIP

    @Test
    fun `valid zip exposes only csv entries`() {
        val zip = zipOf(
            "playlists/Chill.csv" to "Track Name,Artist\nA,B",
            "__MACOSX/._Chill.csv" to "junk",
            "cover.jpg" to "not a csv"
        )
        val inspected = SpotifyZip.inspect(zip)
        assertEquals(listOf("Chill.csv"), inspected.csvs.map { it.name })
        assertTrue(inspected.issues.isEmpty())
        assertTrue(inspected.ignored.any { it.contains("cover.jpg") })
    }

    @Test
    fun `zip without csv yields no playlists`() {
        val inspected = SpotifyZip.inspect(zipOf("readme.txt" to "hi"))
        assertTrue(inspected.csvs.isEmpty())
    }

    @Test
    fun `zip slip paths are rejected`() {
        assertTrue(SpotifyZip.unsafeName("../../evil.csv"))
        assertTrue(SpotifyZip.unsafeName("/etc/passwd.csv"))
        assertTrue(SpotifyZip.unsafeName("C:/Windows/evil.csv"))
        assertFalse(SpotifyZip.unsafeName("folder/ok.csv"))
        val inspected = SpotifyZip.inspect(zipOf("../evil.csv" to "Track Name\nA"))
        assertTrue(inspected.csvs.isEmpty())
        assertTrue(inspected.issues.any { it.message.contains("Zip Slip") })
    }

    @Test
    fun `corrupt zip is reported not thrown`() {
        val inspected = SpotifyZip.inspect("not a zip".toByteArray())
        assertTrue(inspected.issues.isNotEmpty())
    }

    // ------------------------------------------------------------- matching

    @Test
    fun `isrc match wins over a different title`() {
        val doc = LibraryData(
            remoteTracks = listOf(
                RemoteTrack(id = "yt_abc", title = "Otra cosa", artist = "X", isrc = "USABC1234567", source = "YOUTUBE")
            )
        )
        val rows = listOf(SpotifyRow("P", "Titulo distinto", "Y", isrc = "usabc1234567"))
        val plan = SpotifyMatcher.plan(rows, doc)
        assertEquals("yt_abc", plan.playlists.single().entries.single().targetId)
        assertEquals(MatchQuality.HIGH, plan.playlists.single().entries.single().quality)
    }

    @Test
    fun `exact title and artist is high, partial artist is medium`() {
        val doc = library(track("t1", "Song", "Artist"), track("t2", "Other", "Artist Tribute"))
        val plan = SpotifyMatcher.plan(
            listOf(
                SpotifyRow("P", "Song", "Artist"),
                SpotifyRow("P", "Other", "Artist")
            ),
            doc
        )
        // The second one shares the title and the artist name is a prefix, so it is
        // imported but flagged for review instead of being treated as certain.
        assertEquals(listOf(MatchQuality.HIGH, MatchQuality.MEDIUM), plan.playlists.single().entries.map { it.quality })
    }

    @Test
    fun `live version is not matched to the studio version`() {
        val doc = library(track("t1", "Song", "Artist"))
        val plan = SpotifyMatcher.plan(listOf(SpotifyRow("P", "Song - Live", "Artist")), doc)
        assertEquals(MatchQuality.NONE, plan.playlists.single().entries.single().quality)
        assertTrue(plan.playlists.single().entries.single().targetId.startsWith("sp_"))
    }

    @Test
    fun `unmatched song keeps its metadata and a stable reference`() {
        val plan = SpotifyMatcher.plan(
            listOf(SpotifyRow("P", "Unknown", "Nobody", "Disc", 1234, uri = "spotify:track:abc")),
            LibraryData()
        )
        val entry = plan.playlists.single().entries.single()
        assertEquals(MatchQuality.NONE, entry.quality)
        assertTrue(entry.targetId.startsWith("sp_"))
        assertEquals(1, plan.unmatched)
        assertEquals("Disc", entry.row.album)
    }

    @Test
    fun `duplicate rows inside a playlist are counted and removed`() {
        val doc = library(track("t1", "Song", "Artist"))
        val plan = SpotifyMatcher.plan(
            listOf(SpotifyRow("P", "Song", "Artist"), SpotifyRow("P", "Song", "Artist")),
            doc
        )
        assertEquals(1, plan.playlists.single().total)
        assertEquals(1, plan.playlists.single().duplicates)
    }

    // --------------------------------------------------------------- apply

    @Test
    fun `apply creates a spotify playlist in order with unresolved references`() {
        val doc = library(track("t1", "Known", "Artist"))
        val plan = SpotifyMatcher.plan(
            listOf(SpotifyRow("Mix", "Unknown", "Nobody"), SpotifyRow("Mix", "Known", "Artist")),
            doc
        )
        val result = SpotifyImportApply.apply(doc, plan, updateExisting = true)
        val playlist = result.created.single()
        assertEquals("SPOTIFY", playlist.origin)
        assertEquals("spotify:mix", playlist.sourceUrl)
        assertEquals("t1", playlist.trackIds[1])
        assertTrue(playlist.trackIds[0].startsWith("sp_"))
        assertEquals(1, result.remote)
        assertEquals(2, playlist.trackIds.size)
    }

    @Test
    fun `cancelling creates nothing`() {
        val doc = library(track("t1", "Known", "Artist"))
        val plan = SpotifyMatcher.plan(listOf(SpotifyRow("Mix", "Known", "Artist")), doc)
        val result = SpotifyImportApply.apply(doc, plan, updateExisting = true, selected = emptySet())
        assertTrue(result.created.isEmpty())
        assertEquals(doc, result.data)
    }

    @Test
    fun `importing the same file twice updates instead of duplicating`() {
        val doc = library(track("t1", "Known", "Artist"))
        val plan = SpotifyMatcher.plan(listOf(SpotifyRow("Mix", "Known", "Artist")), doc)
        val first = SpotifyImportApply.apply(doc, plan, updateExisting = true)
        val second = SpotifyImportApply.apply(first.data, plan, updateExisting = true)
        assertEquals(1, second.data.playlists.size)
        assertEquals(first.created.single().id, second.updated.single().id)
        assertEquals(1, second.data.playlists.single().trackIds.size)
    }

    @Test
    fun `second import as new keeps both with a distinct name`() {
        val doc = library(track("t1", "Known", "Artist"))
        val plan = SpotifyMatcher.plan(listOf(SpotifyRow("Mix", "Known", "Artist")), doc)
        val first = SpotifyImportApply.apply(doc, plan, updateExisting = false)
        val second = SpotifyImportApply.apply(first.data, plan, updateExisting = false)
        assertEquals(2, second.data.playlists.size)
        assertTrue(second.data.playlists.any { it.name.contains("importada") })
    }

    // -------------------------------------------------------------- youtube

    @Test
    fun `playlist id is read from music youtube and share links`() {
        assertEquals("PL1234567890abcdef", YouTubeMusic.playlistId("https://music.youtube.com/playlist?list=PL1234567890abcdef&si=x"))
        assertEquals("PL1234567890abcdef", YouTubeMusic.playlistId("https://www.youtube.com/watch?v=abc&list=PL1234567890abcdef"))
        assertEquals("PL1234567890abcdef", YouTubeMusic.playlistId("PL1234567890abcdef"))
        assertNull(YouTubeMusic.playlistId("https://open.spotify.com/playlist/abc"))
        assertNull(YouTubeMusic.playlistId("https://music.youtube.com/watch?v=onlyvideo"))
        assertNull(YouTubeMusic.playlistId(""))
    }

    @Test
    fun `flat playlist keeps order, unavailable entries and cover`() {
        val raw = """
            {"title":"Road trip","description":"para el coche","entries":[
              {"id":"aaaaaaaaaaa","title":"One","channel":"A","duration":100,"thumbnails":[{"url":"https://img/1.jpg"}]},
              {"id":"bbbbbbbbbbb","title":"Gone","channel":"B","availability":"unavailable","duration":200},
              {"id":"ccccccccccc","title":"Two","channel":"C","duration":300}
            ]}
        """.trimIndent()
        val list = YouTubeMusic.MusicList(
            YouTubeMusic.parseEntries(raw, includeUnavailable = true).first().title,
            YouTubeMusic.parseEntries(raw, includeUnavailable = true),
            "para el coche",
            "https://img/1.jpg",
            "yt:PL1234567890abcdef"
        )
        assertEquals("One", list.title)
        val plan = YouTubeImport.plan(list.copy(title = "Road trip"))
        assertEquals(3, plan.total)
        assertEquals(1, plan.unavailable)
        assertEquals(listOf("yt_aaaaaaaaaaa", "yt_bbbbbbbbbbb", "yt_ccccccccccc"), plan.hits.map(YouTubeImport::remoteId))
        assertEquals("yt:PL1234567890abcdef", plan.sourceKey)
    }

    @Test
    fun `youtube import creates playlist and second import updates it`() {
        val hits = listOf(
            MusicHit("aaaaaaaaaaa", "One", "A", 100_000, "https://img/1.jpg"),
            MusicHit("", "Gone", "B", 200_000, null, available = false)
        )
        val plan = YouTubeImport.Plan("Road trip", "", "https://img/1.jpg", "yt:PL1234567890abcdef", hits)
        val first = YouTubeImport.apply(LibraryData(), plan, updateExisting = true)
        assertEquals("YOUTUBE", first.playlist.origin)
        assertEquals(2, first.playlist.trackIds.size)
        assertTrue(first.playlist.trackIds[1].startsWith("yt_missing_"))
        val second = YouTubeImport.apply(first.data, plan, updateExisting = true)
        assertEquals(1, second.data.playlists.size)
        assertEquals(0, second.remoteAdded)
    }

    @Test
    fun `imported playlist keeps youtube ids and never local files`() {
        val plan = YouTubeImport.Plan(
            "Y", "", null, "yt:PL1234567890abcdef",
            listOf(MusicHit("aaaaaaaaaaa", "One", "A", 1_000))
        )
        val result = YouTubeImport.apply(LibraryData(), plan, updateExisting = true)
        assertTrue(result.data.tracks.isEmpty())
        assertEquals("yt_aaaaaaaaaaa", result.playlist.trackIds.single())
        assertEquals("YOUTUBE", result.data.remoteTrack("yt_aaaaaaaaaaa")?.source)
    }

    @Test
    fun `youtube refinement keeps position and remote metadata`() {
        val doc = library(track("t1", "Known", "Artist"))
        val plan = SpotifyMatcher.plan(
            listOf(SpotifyRow("Mix", "Unknown", "Nobody"), SpotifyRow("Mix", "Known", "Artist")),
            doc
        )
        val unresolved = plan.playlists.single().entries.first()
        assertEquals(1, plan.pendingYoutube().size)
        val remote = RemoteTrack(
            id = "yt_9GCyD6HexgM", videoId = "9GCyD6HexgM", title = "Unknown", artist = "Nobody",
            source = "YOUTUBE", artworkUrl = "https://img/1.jpg", confidence = "MEDIUM"
        )
        val refined = plan.repoint(unresolved.key, remote.id, MatchQuality.MEDIUM, remote, "Coincidencia en YouTube Music")
        assertEquals(listOf("yt_9GCyD6HexgM", "t1"), refined.playlists.single().entries.map { it.targetId })
        assertEquals(0, refined.pendingYoutube().size)
        val applied = SpotifyImportApply.apply(doc, refined, updateExisting = true)
        val stored = applied.data.remoteTrack("yt_9GCyD6HexgM")
        assertNotNull(stored)
        assertEquals("9GCyD6HexgM", stored.videoId)
        assertEquals("YOUTUBE", stored.source)
        assertTrue(stored.available)
        assertEquals(listOf("yt_9GCyD6HexgM", "t1"), applied.created.single().trackIds)
    }

    @Test
    fun `a refined duplicate video is only stored once`() {
        val doc = library(track("t1", "Known", "Artist"))
        var plan = SpotifyMatcher.plan(
            listOf(SpotifyRow("Mix", "One", "A"), SpotifyRow("Mix", "Two", "B")),
            doc
        )
        val remote = RemoteTrack(id = "yt_aaaaaaaaaaa", videoId = "aaaaaaaaaaa", title = "One", source = "YOUTUBE")
        plan.playlists.single().entries.forEach { entry ->
            plan = plan.repoint(entry.key, remote.id, MatchQuality.MEDIUM, remote, "x")
        }
        val applied = SpotifyImportApply.apply(doc, plan, updateExisting = true)
        assertEquals(listOf("yt_aaaaaaaaaaa"), applied.created.single().trackIds)
        assertEquals(1, applied.data.remoteTracks.size)
    }

    private fun buildPlan(name: String = "Mix"): ImportPlan =
        SpotifyMatcher.plan(listOf(SpotifyRow(name, "Known", "Artist")), library(track("t1", "Known", "Artist")))

    @Test
    fun `playlist plan source key is stable for the same name`() {
        assertEquals(buildPlan().playlists.single().sourceKey, buildPlan("  MIX ").playlists.single().sourceKey)
    }

    @Test
    fun `playlist model defaults keep old documents readable`() {
        val playlist = Playlist("id", "name")
        assertEquals("LOCAL", playlist.origin)
        assertNull(playlist.sourceUrl)
    }
}
