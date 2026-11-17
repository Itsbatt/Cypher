package com.cypher.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.cypher.desktop.app.AppState
import com.cypher.desktop.app.Screen
import com.cypher.desktop.core.DailyStat
import com.cypher.desktop.core.RemoteTrack
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.Track
import com.cypher.desktop.ui.CypherApp
import com.cypher.desktop.ui.CypherColors
import com.cypher.desktop.ui.CypherTheme
import java.awt.Color as AwtColor
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private fun thumb(videoId: String) = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

/**
 * `cypher --screenshots <dir>`: renders the real window (same CypherApp the user runs)
 * off-screen to PNG for review. No component is captured in isolation.
 */
object ScreenshotTool {

    private val covers = mutableListOf<String>()

    fun run(outputDir: File): Int {
        outputDir.mkdirs()
        Storage.rootOverride = kotlin.io.path.createTempDirectory("cypher-shots")
        covers.clear()
        val artDir = File(Storage.dataDir.toFile(), "art").apply { mkdirs() }
        val palettes = listOf(
            intArrayOf(0xFF2B1B4D.toInt(), 0xFF9D7BB0.toInt()),
            intArrayOf(0xFF14343B.toInt(), 0xFF7FC8A9.toInt()),
            intArrayOf(0xFF3B1F2B.toInt(), 0xFFE0A96D.toInt()),
            intArrayOf(0xFF1B2440.toInt(), 0xFF6FA8DC.toInt()),
            intArrayOf(0xFF331B1B.toInt(), 0xFFE06A6A.toInt()),
            intArrayOf(0xFF241F14.toInt(), 0xFFC9B458.toInt())
        )
        palettes.forEachIndexed { index, colors ->
            covers += cover(File(artDir, "cover-$index.png"), 600, 600, colors[0], colors[1])
        }

        val state = seeded()
        val accent = CypherColors.Lavender

        // First run: the local-name welcome. Captured before the name is saved, because
        // that is the only moment it exists.
        shotWindow(outputDir, "00-bienvenida", state)
        state.saveProfileName("Cypher")

        shotWindow(outputDir, "01-inicio", state)

        state.setLibraryPanel(true)
        shotWindow(outputDir, "02-biblioteca-desplegada", state)
        state.setLibraryPanel(false)

        state.openSearch()
        state.setQuery("")
        shotWindow(outputDir, "03-busqueda-reciente", state)

        state.setQuery("milo")
        state.openSearch()
        shotWindow(outputDir, "04-busqueda-resultados", state)

        state.closeSearch()
        state.setQuery("")
        state.state.value.data.tracks.firstOrNull { it.title == "Sincera Te" }?.let { track ->
            state.player.preview(track, positionMs = 74_000)
        }
        shotWindow(outputDir, "05-panel-derecho", state)

        val lyricTrack = lyricTrack(state)
        state.player.preview(lyricTrack, positionMs = 26_000)
        shotWindow(outputDir, "06-panel-letras", state)

        // The queue tab of the same panel.
        state.setRightPanelTab("COLA")
        shotWindow(outputDir, "06a-panel-cola", state)
        state.setRightPanelTab("LETRAS")

        // The expanded cover + lyrics screen (reference page 4 of the PDF).
        state.expandPlayer()
        shotWindow(outputDir, "06b-vista-ampliada", state)
        state.collapsePlayer()

        state.player.preview(state.state.value.data.tracks.first { it.title == "Sincera Te" }, positionMs = 74_000)
        state.toggleRightPanel()
        shotWindow(outputDir, "07-reproductor-flotante", state)
        state.toggleRightPanel()

        // Playlist system: the mobile-style hero, then the share sheet with its QR.
        val demoPlaylist = state.state.value.data.playlists.firstOrNull { it.trackIds.isNotEmpty() }
        if (demoPlaylist != null) {
            state.select(Screen.PLAYLISTS)
            state.openPlaylist(demoPlaylist.id)
            shotWindow(outputDir, "13-playlist", state)
            state.openPlaylistShare(demoPlaylist.id)
            shotWindow(outputDir, "14-playlist-compartir", state)
            state.closePlaylistShare()
        }

        state.select(Screen.SETTINGS)
        state.setBackgroundMode("ORIGINAL")
        shotWindow(outputDir, "08-ajustes-personalizacion", state)

        state.setBackgroundMode("GLASS")
        shotWindow(outputDir, "09-liquid-glass", state)

        val picture = File(Storage.dataDir.toFile(), "fondo-prueba.png")
        cover(picture, 1600, 1000, 0xFF15233A.toInt(), 0xFFB4796A.toInt())
        state.setBackgroundImage(picture.absolutePath)
        state.setBackgroundMode("IMAGE")
        state.setBackgroundBlur(22)
        state.setBackgroundDim(0.45f)
        state.setBackgroundOpacity(0.95f)
        shotWindow(outputDir, "10-imagen-personalizada", state)

        state.select(Screen.HOME)
        state.setBackgroundMode("ORIGINAL")
        state.player.preview(state.state.value.data.tracks.first { it.title == "Sincera Te" }, positionMs = 74_000)
        shotWindow(outputDir, "11-ventana-estrecha", state, width = 1120, height = 760)
        shotWindow(outputDir, "12-ventana-ancha", state, width = 1920, height = 1080)
        return 0
    }

    private fun seeded(): AppState {
        val state = AppState()
        val names = listOf(
            "Sincera Te" to "Milo J", "La Verdad" to "Milo J", "Cae el Sol" to "Airbag",
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
                    artwork = covers[index % covers.size],
                    addedAt = index.toLong()
                )
            }
        )
        state.toggleFavorite("t1")
        state.toggleFavorite("t2")
        state.toggleFavorite("t5")

        val night = state.createPlaylist("Noche en casa", "Para escuchar sin prisa")!!
        listOf("t1", "t2", "t3", "t4").forEach { state.addToPlaylist(night, it) }
        val discover = state.createPlaylist("Discover", "")!!
        listOf("t5", "t6", "t7", "t8").forEach { state.addToPlaylist(discover, it) }
        val argentina = state.createPlaylist("Argentina en la casa", "")!!
        listOf("t9", "t10", "t11", "t12").forEach { state.addToPlaylist(argentina, it) }
        val radio = state.createPlaylist("Radio en la city", "")!!
        listOf("t2", "t3", "t9").forEach { state.addToPlaylist(radio, it) }
        state.replaceLibrary { data ->
            data.copy(
                playlists = data.playlists.map { playlist ->
                    val cover = when (playlist.id) {
                        night -> covers[0]
                        discover -> covers[3]
                        argentina -> covers[4]
                        else -> covers[2]
                    }
                    playlist.copy(artwork = cover)
                },
                recent = listOf("t1", "t5", "t9", "t3"),
                remoteTracks = listOf(
                    RemoteTrack(
                        id = "yt_cloud000001", videoId = "cloud000001", title = "Una bala",
                        artist = "Milo J, Peso Pluma", album = "Álbum Uno", durationMs = 201_000,
                        artworkUrl = covers[1], source = "YOUTUBE"
                    ),
                    RemoteTrack(
                        id = "yt_cloud000002", videoId = "cloud000002", title = "Bandida",
                        artist = "Bardero$", album = "Disco", durationMs = 188_000,
                        artworkUrl = covers[5], source = "YOUTUBE"
                    )
                ),
                tracks = data.tracks + listOf(
                    Track(
                        id = "yt:9GCyD6HexgM",
                        title = "Bajo La Piel",
                        artist = "Milo J",
                        album = "YouTube Music",
                        durationMs = 210_000,
                        path = "https://www.youtube.com/watch?v=9GCyD6HexgM",
                        artwork = covers[2]
                    )
                )
            )
        }
        state.replaceLibrary { data ->
            val today = java.time.LocalDate.now()
            val stats = (0..40).flatMap { day ->
                (0..2).map { slot ->
                    val trackId = "t${(day + slot) % names.size + 1}"
                    val track = data.tracks.first { it.id == trackId }
                    DailyStat(
                        day = today.minusDays(day.toLong()).toString(),
                        trackId = trackId,
                        listenDurationMs = (120_000L + day * 30_000L + slot * 60_000L),
                        playCount = 1 + (day + slot) % 4,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        lastListenedAt = System.currentTimeMillis() - day * 86_400_000L
                    )
                }
            }
            data.copy(stats = stats)
        }
        // Entrance animations do not advance in an off-screen scene: the screenshots use
        // the real "reduce motion" preference so the captured frame is the settled one.
        state.replaceLibrary { data -> data.copy(preferences = data.preferences.copy(reduceMotion = true)) }
        // Newest first: recorded in reverse of the order the dropdown should show.
        state.recordEntitySearch("TRACK", "t2")
        state.recordEntitySearch("ALBUM", "Álbum Uno")
        state.recordQuerySearch("Hardcore Techno Hardstyle")
        state.recordEntitySearch("PLAYLIST", radio)
        state.recordEntitySearch("TRACK", "t1")
        return state
    }

    /** Creates a real audio path plus a sidecar `.lrc` so the lyrics panel has honest data. */
    private fun lyricTrack(state: AppState): Track {
        val folder = File(Storage.dataDir.toFile(), "lyrics").apply { mkdirs() }
        val audio = File(folder, "cancion.mp3").apply { writeBytes(ByteArray(0)) }
        File(folder, "cancion.lrc").writeText(
            """
            [00:00.00] Cypher · letras de ejemplo del archivo .lrc
            [00:08.00] Tengo un corazón de vagabundo
            [00:14.00] y hoy me quedé sin mi dama
            [00:20.00] ¿Dónde está el amor, tu mirada?
            [00:26.00] Sé que me odia, pero me llama
            [00:32.00] Y aunque duela, sigo en la misma cama
            [00:40.00] Buscando la calle que me nombra
            """.trimIndent()
        )
        return Track(
            id = "lyric_demo",
            title = "Una bala",
            artist = "Milo J, Peso Pluma",
            album = "Álbum Uno",
            durationMs = 201_000,
            path = audio.absolutePath,
            artwork = covers[1]
        )
    }

    /** A real gradient PNG, so covers and backgrounds render without network access. */
    private fun cover(target: File, width: Int, height: Int, top: Int, bottom: Int): String {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.paint = GradientPaint(0f, 0f, AwtColor(top), width.toFloat(), height.toFloat(), AwtColor(bottom))
        g.fillRect(0, 0, width, height)
        g.color = AwtColor(255, 255, 255, 38)
        g.fillOval(width / 5, height / 6, width / 2, height / 2)
        g.color = AwtColor(0, 0, 0, 60)
        g.fillOval(width / 2, height / 2, width / 2, height / 2)
        g.dispose()
        ImageIO.write(image, "png", target)
        return target.absolutePath
    }

    private fun shotWindow(dir: File, name: String, state: AppState, width: Int = 1440, height: Int = 900) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CypherTheme(accent = CypherColors.Lavender) { CypherApp(state) }
        }
        try {
            // Several frames so entrance animations settle before the capture.
            repeat(5) {
                scene.render()
                Thread.sleep(180)
            }
            val bytes = scene.render().encodeToData()?.bytes ?: return
            val file = File(dir, "$name.png")
            file.writeBytes(bytes)
            println("captura: ${file.absolutePath}")
        } finally {
            scene.close()
        }
    }

    @Suppress("unused")
    private fun unused(content: @Composable () -> Unit) = Unit

    fun main(args: Array<String>) {
        val dir = args.getOrNull(1)?.let(::File) ?: File("screenshots")
        exitProcess(run(dir))
    }
}
