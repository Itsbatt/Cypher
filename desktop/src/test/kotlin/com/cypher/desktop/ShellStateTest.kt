package com.cypher.desktop

import com.cypher.desktop.app.AppState
import com.cypher.desktop.core.Lyrics
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.Track
import com.cypher.desktop.ui.backgroundSettings
import com.cypher.desktop.ui.importBackgroundPicture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

/** The shell's preferences and history must survive a restart, exactly as the UI promises. */
class ShellStateTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("cypher-shell").toFile()
        Storage.rootOverride = root.toPath()
    }

    @AfterTest
    fun tearDown() {
        Storage.rootOverride = null
    }

    private fun AppState.withTrack(): AppState {
        replaceLibrary { data ->
            data.copy(tracks = listOf(Track("t1", "Sincera Te", "Milo J", "Álbum Uno", 201_000, "C:/m/1.mp3")))
        }
        return this
    }

    @Test
    fun `recent searches survive a restart`() {
        val state = AppState().withTrack()
        state.recordQuerySearch("Milo J")
        state.recordEntitySearch("TRACK", "t1")

        val reloaded = Storage.load()
        assertEquals(2, reloaded.recentSearches.size)
        assertEquals("Sincera Te", reloaded.recentSearches.first().title)
        assertEquals("Milo J", reloaded.recentSearches.last().query)
    }

    @Test
    fun `the same entry is not duplicated`() {
        val state = AppState().withTrack()
        state.recordQuerySearch("Airbag")
        state.recordQuerySearch("airbag")
        assertEquals(1, Storage.load().recentSearches.size)
    }

    @Test
    fun `removing and clearing recents is persisted`() {
        val state = AppState().withTrack()
        state.recordQuerySearch("Uno")
        state.recordQuerySearch("Dos")
        val first = Storage.load().recentSearches.first()
        state.removeRecentSearch(first.key)
        assertEquals(listOf("Uno"), Storage.load().recentSearches.map { it.title })
        state.clearRecentSearches()
        assertTrue(Storage.load().recentSearches.isEmpty())
    }

    @Test
    fun `background preferences persist`() {
        val state = AppState()
        state.setBackgroundMode("GLASS")
        state.setBackgroundBlur(26)
        state.setBackgroundDim(0.6f)
        state.setBackgroundOpacity(0.7f)
        state.setBackgroundFit("FIT")

        val prefs = Storage.load().preferences
        assertEquals("GLASS", prefs.backgroundMode)
        assertEquals(26, prefs.backgroundBlur)
        assertEquals(0.6f, prefs.backgroundDim)
        assertEquals(0.7f, prefs.backgroundOpacity)
        assertEquals("FIT", prefs.backgroundFit)
        assertEquals("GLASS", prefs.backgroundSettings().mode)
    }

    @Test
    fun `a chosen picture is copied into the data folder`() {
        val source = File(createTempDirectory("cypher-pic").toFile(), "foto.png")
        source.writeBytes(ByteArray(64))
        val stored = importBackgroundPicture(source)
        assertNotNull(stored)
        val file = File(stored)
        assertTrue(file.isFile)
        assertEquals(Storage.dataDir.toFile().absoluteFile, file.parentFile.absoluteFile)
        assertTrue(source.exists(), "the original file is never moved")
    }

    @Test
    fun `lyrics come from the sidecar lrc with real timestamps`() {
        val folder = createTempDirectory("cypher-lrc").toFile()
        val audio = File(folder, "tema.mp3").apply { writeBytes(ByteArray(0)) }
        File(folder, "tema.lrc").writeText("[00:01.50] Primera\n[00:12.00] Segunda\n[00:20.25] Tercera\n")
        val lines = Lyrics.forTrack(audio.absolutePath)
        assertEquals(3, lines.size)
        assertEquals(1_500, lines[0].atMs)
        assertEquals("Segunda", lines[1].text)
        assertEquals(20_250, lines[2].atMs)
    }

    @Test
    fun `a remote stream has no sidecar lyrics`() {
        assertTrue(Lyrics.forTrack("https://www.youtube.com/watch?v=abc").isEmpty())
    }
}
