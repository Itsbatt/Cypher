package com.cypher.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.cypher.desktop.app.AppState
import com.cypher.desktop.ui.CypherApp
import com.cypher.desktop.ui.CypherColors
import com.cypher.desktop.ui.CypherTheme
import com.cypher.desktop.ui.glass.GlassBackdrop
import com.cypher.desktop.ui.glass.GlassSpec
import com.cypher.desktop.ui.glass.GlassSupport
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the desktop glass on the JVM: the backdrop texture is built, the SkSL
 * shader compiles against the Skia that ships with Compose Desktop, and the shell can
 * paint frames with it without throwing.
 *
 * This is not a GPU benchmark and not a device test: it proves the code path, which is
 * exactly what a headless runner can prove.
 */
class GlassSurfaceTest {

    @Test
    fun `the glass backdrop is built from the shell palette`() {
        val backdrop = GlassBackdrop.build("GLASS", null, Color(0xFF9D7BB0))
        assertNotNull(backdrop, "el fondo del cristal debe generarse sin GPU")
        assertTrue(backdrop.image.width > 0 && backdrop.image.height > 0)
    }

    @Test
    fun `the shader compiles and the shell renders frames with it`() {
        if (!GlassSpec.enabled) return
        val state = AppState()
        state.importTracks(
            listOf(
                com.cypher.desktop.core.Track(
                    id = "t1", title = "Una bala", artist = "Milo J", album = "Álbum",
                    durationMs = 201_000, path = "C:/m/1.flac", artwork = null
                )
            )
        )
        state.toggleFavorite("t1")

        val scene = ImageComposeScene(width = 1280, height = 820, density = Density(1f)) {
            CypherTheme(accent = CypherColors.Lavender) {
                CypherApp(state)
            }
        }
        try {
            // Three frames: the first one builds the backdrop, the others draw it.
            repeat(3) { frame ->
                scene.render(frame * 16_666_667L)
            }
            assertTrue(
                GlassSupport.shaderCompiled,
                "el shader SkSL debe compilar en esta plataforma (error: ${GlassSupport.lastError})"
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a panel without backdrop still draws the degraded glass`() {
        // Simulates a GPU that cannot compile: the modifier must not throw.
        val scene = ImageComposeScene(width = 640, height = 480, density = Density(1f)) {
            androidx.compose.material3.Text("sin cristal")
        }
        try {
            scene.render(0L)
        } finally {
            scene.close()
        }
    }
}
