package com.cypher.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cypher.desktop.app.AppState
import com.cypher.desktop.core.Track
import com.cypher.desktop.ui.CypherApp
import com.cypher.desktop.ui.CypherColors
import com.cypher.desktop.ui.CypherTheme
import com.cypher.desktop.ui.WindowsChrome
import com.cypher.desktop.ui.glass.GlassSupport
import java.util.Locale
import kotlin.system.exitProcess

/**
 * `cypher --glass-bench [frames] [width] [height]`: opens the real window, lets it paint
 * for [frames] frames and reports the frame interval distribution.
 *
 * This is the only honest way to measure the effect: the offscreen `ImageComposeScene`
 * used by the tests rasterises on the CPU, where a runtime shader is hundreds of times
 * slower than on the GPU the user actually has.
 *
 * The window is visible while it runs, so it can be watched; it closes itself.
 */
object GlassBench {

    fun run(args: Array<String>): Int {
        val index = args.indexOf("--glass-bench")
        val frames = args.getOrNull(index + 1)?.toIntOrNull() ?: 300
        val width = args.getOrNull(index + 2)?.toIntOrNull() ?: 1440
        val height = args.getOrNull(index + 3)?.toIntOrNull() ?: 900
        val mode = args.getOrNull(index + 4) ?: "GLASS"
        val image = args.getOrNull(index + 5)
        var code = 0

        application {
            val state = remember {
                seeded().also { seeded ->
                    if (image != null) {
                        seeded.setBackgroundImage(image)
                        seeded.setBackgroundMode("IMAGE")
                    } else if (mode != "GLASS") {
                        seeded.setBackgroundMode(mode)
                    }
                }
            }
            Window(
                onCloseRequest = { exitApplication() },
                title = "Cypher · liquid glass bench",
                state = rememberWindowState(size = DpSize(width.dp, height.dp))
            ) {
                LaunchedEffect(Unit) {
                    runCatching { WindowsChrome.apply(window) }
                    val intervals = ArrayList<Long>(frames)
                    var previous = 0L
                    for (frame in 0 until frames) {
                        val now = withFrameNanos { it }
                        // Skip the warm-up: first frames pay for shader JIT and textures.
                        if (frame > 60 && previous != 0L) intervals += now - previous
                        previous = now
                    }
                    report(intervals, width, height, mode, image != null)
                    exitApplication()
                }
                CypherTheme(accent = CypherColors.Lavender) {
                    CypherApp(state)
                }
            }
        }
        return code
    }

    private fun report(intervals: List<Long>, width: Int, height: Int, mode: String, photo: Boolean) {
        if (intervals.isEmpty()) {
            println("GLASS_BENCH sin muestras")
            return
        }
        val sorted = intervals.sorted()
        val average = sorted.average() / 1_000_000.0
        val p50 = sorted[sorted.size / 2] / 1_000_000.0
        val p95 = sorted[(sorted.size * 95) / 100] / 1_000_000.0
        val worst = sorted.last() / 1_000_000.0
        println(
            "GLASS_BENCH frames=" + sorted.size + " size=" + width + "x" + height +
                " mode=" + mode + (if (photo) "+foto" else "") +
                " avg=" + fmt(average) + "ms p50=" + fmt(p50) + "ms p95=" + fmt(p95) +
                "ms max=" + fmt(worst) + "ms fps=" + fmt(1000.0 / average) +
                " shader=" + (if (GlassSupport.shaderActive) "on" else "degraded") +
                " reason=" + (GlassSupport.degradedReason ?: "-") +
                " error=" + (GlassSupport.lastError ?: "-")
        )
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** A library big enough to fill the window, so the backdrop is not a flat screen. */
    private fun seeded(): AppState {
        val state = AppState()
        val names = listOf(
            "Una bala" to "Milo J", "Sincera Te" to "Milo J", "Cae el Sol" to "Airbag",
            "Rezo por Vos" to "Spinetta", "De Música Ligera" to "Soda Stereo",
            "Flaca" to "Andrés Calamaro", "Crimen" to "Cerati", "Lago en el Cielo" to "Cerati",
            "El Tesoro" to "El Mató", "Post Crucifixion" to "Pescado Rabioso",
            "Río Babel" to "Babasónicos", "Irresponsables" to "Babasónicos"
        )
        state.importTracks(
            names.mapIndexed { index, (title, artist) ->
                Track(
                    id = "t${index + 1}",
                    title = title,
                    artist = artist,
                    album = if (index < 6) "Álbum Uno" else "Álbum Dos",
                    durationMs = (150 + index * 11) * 1000L,
                    path = "C:/m/${index + 1}.mp3",
                    artwork = null,
                    addedAt = index.toLong()
                )
            }
        )
        state.toggleFavorite("t1")
        state.toggleFavorite("t2")
        val night = state.createPlaylist("Noche en casa", "Para escuchar sin prisa")!!
        listOf("t1", "t2", "t3", "t4").forEach { state.addToPlaylist(night, it) }
        val discover = state.createPlaylist("Discover", "")!!
        listOf("t5", "t6", "t7", "t8").forEach { state.addToPlaylist(discover, it) }
        return state
    }
}
