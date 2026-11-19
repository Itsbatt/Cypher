package com.cypher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cypher.desktop.app.AppState
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.Track
import com.cypher.desktop.ui.CypherColors
import com.cypher.desktop.ui.CypherTheme
import com.cypher.desktop.ui.TrackContextMenuContent
import com.cypher.desktop.ui.TrackRow
import com.cypher.desktop.ui.viewOf
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TrackContextMenuTest {

    @BeforeTest
    fun setUp() {
        Storage.rootOverride = Files.createTempDirectory("cypher-menu-test")
    }

    @AfterTest
    fun tearDown() {
        Storage.rootOverride = null
    }

    @Test
    fun rendersTrackRowAndMenu() {
        val state = AppState()
        val track = Track(
            id = "test-track-1",
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            album = "Starboy",
            durationMs = 230_000L,
            path = "C:/Music/starboy.mp3"
        )
        state.importTracks(listOf(track))
        state.createPlaylist("Chill")?.let { state.addToPlaylist(it, track.id) }
        state.createPlaylist("Mi lista n.º 5")
        state.createPlaylist("La 1ra")
        state.createPlaylist("Favoritas 2026")

        val ui = state.state.value
        val view = viewOf(ui.data, track.id)
        assertTrue(view != null, "TrackView should be created")

        val scene = ImageComposeScene(width = 1000, height = 700, density = Density(1f)) {
            CypherTheme(accent = CypherColors.Lavender) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CypherColors.Background)
                        .padding(24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TrackRow(
                            view = view,
                            data = ui.data,
                            accent = CypherColors.Lavender,
                            state = state,
                            currentId = null
                        )

                        Spacer(Modifier.height(24.dp))

                        // Direct rendering of the compound context menu
                        Box(
                            modifier = Modifier
                                .border(1.dp, CypherColors.Border.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                        ) {
                            TrackContextMenuContent(
                                view = view,
                                data = ui.data,
                                accent = CypherColors.Lavender,
                                state = state,
                                showPlaylists = true,
                                playlistQuery = "",
                                onQueryChange = {},
                                onTogglePlaylists = {},
                                onHoverPlaylists = {},
                                onClose = {}
                            )
                        }
                    }
                }
            }
        }

        try {
            repeat(4) {
                scene.render()
                Thread.sleep(50)
            }
            val bytes = scene.render().encodeToData()?.bytes
            assertTrue(bytes != null && bytes.isNotEmpty(), "Scene should render bytes")

            val outDir = File("C:/Users/bat/.gemini/antigravity/brain/b2656572-24d3-4491-9fc9-97a99ce94138")
            if (outDir.isDirectory) {
                val dest = File(outDir, "track_context_menu_rendered.png")
                dest.writeBytes(bytes)
                println("Rendered screenshot to: ${dest.absolutePath}")
            }
        } finally {
            scene.close()
        }
    }
}
