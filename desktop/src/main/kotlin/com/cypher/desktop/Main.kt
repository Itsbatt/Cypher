package com.cypher.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cypher.desktop.app.AppState
import com.cypher.desktop.ui.CypherApp
import com.cypher.desktop.ui.CypherTheme
import com.cypher.desktop.ui.WindowsChrome
import java.awt.Dimension
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Compose shows a dialog for UI exceptions but hides the stack trace; print it so a
    // real failure can be diagnosed instead of guessed from a class name.
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        System.err.println("Fallo no controlado en ${thread.name}: ${error.stackTraceToString()}")
    }
    if (args.contains("--self-test")) {
        exitProcess(SelfTest.run())
    }
    if (args.contains("--cef-check")) {
        exitProcess(SelfTest.runCefCheck())
    }
    if (args.contains("--screenshots")) {
        ScreenshotTool.main(args)
    }
    if (args.contains("--glass-bench")) {
        exitProcess(GlassBench.run(args))
    }
    if (args.contains("--yt-save")) {
        val index = args.indexOf("--yt-save")
        val url = args.getOrNull(index + 1).orEmpty()
        val limit = args.getOrNull(index + 2)?.toIntOrNull() ?: 2
        exitProcess(SelfTest.runSave(url, limit))
    }
    if (args.contains("--yt-api-probe")) {
        exitProcess(SelfTest.runYouTubeProbe())
    }
    if (args.contains("--spotify-plan")) {
        val index = args.indexOf("--spotify-plan")
        val paths = args.drop(index + 1).filterNot { it.startsWith("--") }
        exitProcess(SelfTest.runSpotifyPlan(paths))
    }
    if (args.contains("--yt-import")) {
        val index = args.indexOf("--yt-import")
        exitProcess(SelfTest.runPlaylistImport(args.getOrNull(index + 1) ?: ""))
    }
    if (args.contains("--yt-playlist")) {
        val index = args.indexOf("--yt-playlist")
        exitProcess(SelfTest.runPlaylist(args.getOrNull(index + 1) ?: ""))
    }
    if (args.contains("--yt-install-test")) {
        exitProcess(SelfTest.runInstall())
    }
    if (args.contains("--yt-app-play")) {
        val index = args.indexOf("--yt-app-play")
        exitProcess(SelfTest.runAppPlay(args.getOrNull(index + 1) ?: "milo j"))
    }
    if (args.contains("--playlist-demo")) {
        val index = args.indexOf("--playlist-demo")
        val playlistId = args.getOrNull(index + 1).orEmpty()
        val cover = args.getOrNull(index + 2)
        exitProcess(SelfTest.runPlaylistDemo(playlistId, cover))
    }
    if (args.contains("--local-audio")) {
        val index = args.indexOf("--local-audio")
        exitProcess(SelfTest.runLocalAudio(args.getOrNull(index + 1) ?: "tono"))
    }
    if (args.contains("--yt-audio")) {
        val index = args.indexOf("--yt-audio")
        val query = args.getOrNull(index + 1) ?: "milo j"
        exitProcess(SelfTest.runAudio(query))
    }
    if (args.contains("--yt-selftest")) {
        val index = args.indexOf("--yt-selftest")
        val query = args.getOrNull(index + 1) ?: "milo j"
        exitProcess(SelfTest.runOnline(query))
    }
    if (args.contains("--import")) {
        val index = args.indexOf("--import")
        exitProcess(SelfTest.runImportBackup(args.getOrNull(index + 1).orEmpty()))
    }
    application {
        val state = remember { AppState() }
        val accent = state.state.value.data.preferences.accent
        var isVisible by remember { androidx.compose.runtime.mutableStateOf(true) }

        androidx.compose.runtime.DisposableEffect(state) {
            state.onRestoreWindow = {
                isVisible = true
            }
            state.onExitRequested = {
                state.shutdown()
                exitApplication()
            }
            onDispose { }
        }

        Window(
            visible = isVisible,
            onCloseRequest = {
                if (state.state.value.data.preferences.minimizeToTray) {
                    isVisible = false
                } else {
                    state.shutdown()
                    exitApplication()
                }
            },
            title = "Cypher",
            icon = painterResource("cypher.png"),
            state = rememberWindowState(size = DpSize(1320.dp, 860.dp))
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(1120, 720)
                // Native dark caption, same palette as the app, with Windows keeping
                // drag, snap, maximize and resize behaviour.
                WindowsChrome.apply(window)
            }
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    window.toFront()
                }
            }
            CypherTheme(accent = androidx.compose.ui.graphics.Color(accent)) {
                CypherApp(state)
            }
        }
    }
}
