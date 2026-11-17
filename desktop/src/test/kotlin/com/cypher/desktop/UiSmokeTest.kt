package com.cypher.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.LibrarySection
import com.cypher.desktop.app.Screen
import com.cypher.desktop.core.DailyStat
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.Track
import com.cypher.desktop.ui.CypherTheme
import com.cypher.desktop.ui.FavoritesView
import com.cypher.desktop.ui.HomeView
import com.cypher.desktop.ui.LibraryView
import com.cypher.desktop.ui.PlaylistsView
import com.cypher.desktop.ui.SettingsView
import com.cypher.desktop.ui.StatsView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cypher.desktop.ui.CypherColors
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Composes every screen off-screen. This catches layout crashes that a compile check
 * cannot: vertical lists inside a Column, grids with unbounded height, missing state.
 */
class UiSmokeTest {

    @BeforeTest
    fun setUp() {
        Storage.rootOverride = Files.createTempDirectory("cypher-ui-test")
    }

    @AfterTest
    fun tearDown() {
        Storage.rootOverride = null
    }

    private fun seededState(): AppState {
        val state = AppState()
        state.importTracks(
            (1..12).map {
                Track(
                    id = "t$it",
                    title = "Canción $it",
                    artist = if (it % 2 == 0) "Artista A" else "Artista B",
                    album = if (it <= 6) "Álbum Uno" else "Álbum Dos",
                    durationMs = it * 20_000L,
                    path = "C:/m/$it.mp3",
                    addedAt = it.toLong()
                )
            }
        )
        state.toggleFavorite("t1")
        state.toggleFavorite("t3")
        val id = state.createPlaylist("Mezcla", "para probar")!!
        listOf("t1", "t2", "t3").forEach { state.addToPlaylist(id, it) }
        state.replaceLibrary { data ->
            data.copy(
                stats = listOf(
                    DailyStat("2026-11-11", "t1", 600_000, 4, "Canción 1", "Artista B", "Álbum Uno"),
                    DailyStat("2026-11-12", "t2", 300_000, 2, "Canción 2", "Artista A", "Álbum Uno")
                )
            )
        }
        state.openPlaylist(id)
        return state
    }

    private fun render(label: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(width = 1280, height = 820, density = Density(1f)) {
            CypherTheme(accent = CypherColors.Lavender) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(CypherColors.Background)) {
                    content()
                }
            }
        }
        val image = scene.render()
        assertTrue(image.width > 0 && image.height > 0, "la pantalla $label no renderizó")
        scene.close()
    }

    @Test
    fun `every screen composes without crashing`() {
        val state = seededState()
        render("inicio") { HomeView(state.state.value, CypherColors.Lavender, state) }
        render("biblioteca canciones") {
            state.setLibrarySection(LibrarySection.SONGS)
            LibraryView(state.state.value, CypherColors.Lavender, state)
        }
        render("biblioteca albumes") {
            state.setLibrarySection(LibrarySection.ALBUMS)
            LibraryView(state.state.value, CypherColors.Lavender, state)
        }
        render("biblioteca artistas") {
            state.setLibrarySection(LibrarySection.ARTISTS)
            LibraryView(state.state.value, CypherColors.Lavender, state)
        }
        render("album") {
            state.openAlbum("Álbum Uno")
            LibraryView(state.state.value, CypherColors.Lavender, state)
        }
        render("favoritos") { FavoritesView(state.state.value, CypherColors.Lavender, state) }
        render("playlist detalle") {
            state.openPlaylist(state.snapshot().playlists.first().id)
            PlaylistsView(state.state.value, CypherColors.Lavender, state)
        }
        render("playlists") { PlaylistsView(state.state.value.copy(selectedPlaylistId = null), CypherColors.Lavender, state) }
        render("estadisticas") { StatsView(state.state.value, CypherColors.Lavender, state) }
        render("wrapped") { com.cypher.desktop.ui.WrappedView(state.state.value, CypherColors.Lavender, state) }
        render("descargas") { com.cypher.desktop.ui.DownloadsView(state.state.value, CypherColors.Lavender, state) }
        render("youtube") { com.cypher.desktop.ui.YouTubeView(state.state.value, CypherColors.Lavender, state) }
        render("ajustes") { SettingsView(state.state.value, CypherColors.Lavender, state) }
    }

    @Test
    fun `empty library and empty states render`() {
        val empty = AppState()
        render("inicio vacio") { HomeView(empty.state.value, CypherColors.Lavender, empty) }
        render("biblioteca vacia") { LibraryView(empty.state.value, CypherColors.Lavender, empty) }
        render("favoritos vacios") { FavoritesView(empty.state.value, CypherColors.Lavender, empty) }
        render("playlists vacias") { PlaylistsView(empty.state.value, CypherColors.Lavender, empty) }
        render("stats vacias") { StatsView(empty.state.value, CypherColors.Lavender, empty) }
        render("wrapped vacio") { com.cypher.desktop.ui.WrappedView(empty.state.value, CypherColors.Lavender, empty) }
        render("descargas vacias") { com.cypher.desktop.ui.DownloadsView(empty.state.value, CypherColors.Lavender, empty) }
        render("youtube vacio") { com.cypher.desktop.ui.YouTubeView(empty.state.value, CypherColors.Lavender, empty) }
    }

    @Test
    fun `screen enum covers every destination`() {
        assertTrue(Screen.entries.size >= 7)
    }
}
