package com.cypher.desktop

import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.DataMutations
import com.cypher.desktop.app.SortMode
import com.cypher.desktop.ui.sortTracks
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.RemoteTrack
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.Track
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {

    @BeforeTest
    fun setUp() {
        Storage.rootOverride = Files.createTempDirectory("cypher-state-test")
    }

    @AfterTest
    fun tearDown() {
        Storage.rootOverride = null
    }

    private fun stateWithTracks(): AppState {
        val state = AppState()
        state.importTracks(
            listOf(
                Track("t1", "Zeta", "Artista B", "Álbum 2", 3000, "C:/m/1.mp3", addedAt = 10),
                Track("t2", "Alfa", "Artista A", "Álbum 1", 1000, "C:/m/2.mp3", addedAt = 20),
                Track("t3", "Beta", "Artista A", "Álbum 1", 2000, "C:/m/3.mp3", addedAt = 30)
            )
        )
        return state
    }

    @Test
    fun `playlist lifecycle is persisted`() {
        val state = stateWithTracks()
        val id = state.createPlaylist("Noche", "para dormir")!!
        state.addToPlaylist(id, "t1")
        state.addToPlaylist(id, "t2")
        state.updatePlaylist(id, name = "Noche larga")
        assertEquals(listOf("t1", "t2"), state.snapshot().playlist(id)?.trackIds)
        assertEquals("Noche larga", state.snapshot().playlist(id)?.name)

        val reloaded = AppState()
        assertEquals("Noche larga", reloaded.snapshot().playlist(id)?.name)
        assertEquals(listOf("t1", "t2"), reloaded.snapshot().playlist(id)?.trackIds)
        assertEquals("Noche larga", reloaded.snapshot().playlist(id)?.name)
    }

    @Test
    fun `duplicate tracks are not added twice`() {
        val state = stateWithTracks()
        val id = state.createPlaylist("P")!!
        state.addToPlaylist(id, "t1")
        state.addToPlaylist(id, "t1")
        assertEquals(listOf("t1"), state.snapshot().playlist(id)?.trackIds)
    }

    @Test
    fun `reordering changes the play order and bumps updatedAt`() {
        val state = stateWithTracks()
        val id = state.createPlaylist("P")!!
        listOf("t1", "t2", "t3").forEach { state.addToPlaylist(id, it) }
        val before = state.snapshot().playlist(id)!!
        Thread.sleep(2)
        state.moveInPlaylist(id, 2, 0)
        val after = state.snapshot().playlist(id)!!
        assertEquals(listOf("t3", "t1", "t2"), after.trackIds)
        assertTrue(after.updatedAt > before.updatedAt)
        assertEquals(listOf("t3", "t1", "t2"), state.snapshot().playlist(id)?.trackIds)
    }

    @Test
    fun `removing a track keeps the rest in order`() {
        val state = stateWithTracks()
        val id = state.createPlaylist("P")!!
        listOf("t1", "t2", "t3").forEach { state.addToPlaylist(id, it) }
        state.removeFromPlaylist(id, "t2")
        assertEquals(listOf("t1", "t3"), state.snapshot().playlist(id)?.trackIds)
    }

    @Test
    fun `deleting a playlist queues a tombstone and keeps the tracks`() {
        val state = stateWithTracks()
        val id = state.createPlaylist("P")!!
        state.deletePlaylist(id)
        assertNull(state.snapshot().playlist(id))
        assertEquals(3, state.snapshot().tracks.size)
    }

    @Test
    fun `favoriting is local-first and queues the change`() {
        val state = stateWithTracks()
        state.toggleFavorite("t1")
        assertTrue(state.snapshot().isFavorite("t1"))
        state.toggleFavorite("t1")
        assertTrue(!state.snapshot().isFavorite("t1"))
    }

    @Test
    fun `favorites of remote-only tracks survive a rescan reconcile`() {
        val data = LibraryData(
            tracks = listOf(Track("t1", "Uno", "A", path = "C:/m/1.mp3")),
            favorites = listOf("t1", "cloud-1"),
            remoteTracks = listOf(RemoteTrack("cloud-1", title = "Del móvil"))
        )
        val reconciled = DataMutations.reconcileTracks(data)
        assertEquals(listOf("t1", "cloud-1"), reconciled.favorites)
    }

    @Test
    fun `sorting covers every mode`() {
        val tracks = stateWithTracks().snapshot().tracks
        assertEquals(listOf("Alfa", "Beta", "Zeta"), sortTracks(tracks, SortMode.TITLE).map { it.title })
        assertEquals(listOf("t2", "t3", "t1"), sortTracks(tracks, SortMode.ARTIST).map { it.id })
        assertEquals(listOf("t3", "t2", "t1"), sortTracks(tracks, SortMode.ADDED).map { it.id })
        assertEquals(listOf("t1", "t3", "t2"), sortTracks(tracks, SortMode.DURATION).map { it.id })
    }

    @Test
    fun `albums and artists are grouped from real metadata`() {
        val data = stateWithTracks().snapshot()
        assertEquals(2, data.albums.size)
        assertEquals(2, data.artists.size)
        assertEquals("Artista A", data.albums.first { it.name == "Álbum 1" }.artist)
    }

    @Test
    fun `rename profile and accent are stored`() {
        val state = stateWithTracks()
        state.renameProfile("Alex")
        state.setAccent(0xFF7FC8A9)
        assertEquals("Alex", state.snapshot().profile.displayName)
        assertEquals(0xFF7FC8A9, state.snapshot().preferences.accent)
        assertNotNull(AppState().snapshot().profile.displayName)
    }

    @Test
    fun `search matches title artist and album`() {
        val data = stateWithTracks().snapshot()
        assertEquals(listOf("t2", "t3"), data.search("artista a").map { it.id })
        assertEquals(listOf("t1"), data.search("zeta").map { it.id })
        assertEquals(listOf("t2", "t3"), data.search("álbum 1").map { it.id })
    }

    @Test
    fun `the info panel tabs and the expanded screen are pure view state`() {
        val state = stateWithTracks()
        assertEquals("LETRAS", state.state.value.rightPanelTab)
        assertFalse(state.state.value.playerExpanded)

        state.setRightPanelTab("COLA")
        assertEquals("COLA", state.state.value.rightPanelTab)
        assertTrue(state.state.value.rightPanelOpen, "abrir una pestaña no debe cerrar el panel")

        state.toggleRightPanel()
        assertFalse(state.state.value.rightPanelOpen)
        state.setRightPanelTab("LETRAS")
        assertTrue(state.state.value.rightPanelOpen, "elegir pestaña vuelve a mostrar el panel")

        state.expandPlayer()
        assertTrue(state.state.value.playerExpanded)
        state.collapsePlayer()
        assertFalse(state.state.value.playerExpanded)
        state.toggleExpandedPlayer()
        assertTrue(state.state.value.playerExpanded)
        // Ni abrir ni cerrar la vista ampliada toca la reproducción ni la biblioteca.
        assertEquals(3, state.snapshot().tracks.size)
    }

    @Test
    fun `accent is stored locally and survives a reload`() {
        val state = stateWithTracks()
        state.setAccent(0xFFE06A6A)
        assertEquals(0xFFE06A6A, state.snapshot().preferences.accent)
        assertEquals(0xFFE06A6A, AppState().snapshot().preferences.accent)
    }
}
