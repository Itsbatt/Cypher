package com.cypher.desktop

import com.cypher.desktop.core.BackupImport
import com.cypher.desktop.core.ParseIssue
import com.cypher.desktop.core.Scanner
import com.cypher.desktop.core.SpotifyCsv
import com.cypher.desktop.core.SpotifyMatcher
import com.cypher.desktop.core.SpotifyRow
import com.cypher.desktop.core.SpotifyZip
import com.cypher.desktop.core.Storage
import com.cypher.desktop.core.YouTubeImport
import com.cypher.desktop.core.YouTubeMusic
import com.cypher.desktop.player.AudioPlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.exitProcess

/**
 * `cypher --self-test`: writes a real WAV, scans it like a normal folder and plays it,
 * reporting what actually happened. It runs in the same packaged runtime as the app, so
 * it is the honest answer to "does playback work here?" — the unit tests cannot start a
 * window or open an audio device.
 */
object SelfTest {

    /**
     * `cypher --import <zip|json>`: merges a phone backup into this library and prints what
     * changed. It exists so the phone-to-PC path can be checked with the real file, instead
     * of trusting the parser in isolation.
     */
    fun runImportBackup(path: String): Int {
        val file = File(path)
        if (!file.isFile) {
            System.err.println("No existe el archivo: $path")
            return 2
        }
        val before = Storage.load()
        return runCatching {
            val (merged, summary) = BackupImport.import(file, before)
            Storage.save(merged)
            println("Archivo: ${file.name} (${file.length()} bytes)")
            println("Importado: ${summary.playlists} playlists, ${summary.tracks} canciones, ${summary.favorites} favoritos, ${summary.lyrics} letras")
            println(
                "Biblioteca: ${merged.playlists.size} playlists, ${merged.tracks.size} locales, " +
                    "${merged.remoteTracks.size} remotas, ${merged.favorites.size} favoritos, ${merged.lyrics.size} letras"
            )
            0
        }.getOrElse { error ->
            System.err.println("Fallo al importar: ${error.message}")
            1
        }
    }


    fun run(): Int {
        val dir = kotlin.io.path.createTempDirectory("cypher-selftest").toFile()
        val file = File(dir, "Prueba 440Hz.wav")
        writeWav(file)
        println("archivo: ${file.absolutePath} (${file.length()} bytes)")

        val tracks = runBlockingScan(dir)
        if (tracks.isEmpty()) {
            println("FALLO: el escáner no leyó el WAV")
            return 2
        }
        val track = tracks.first()
        println("escaneado: title='${track.title}' durationMs=${track.durationMs} id=${track.id}")

        val player = AudioPlayer()
        player.initialize()
        println("audio disponible: ${player.available}")
        if (!player.available) {
            println("aviso: motor de audio no disponible en este equipo")
            return 3
        }
        player.play(track)
        val deadline = System.currentTimeMillis() + 6_000
        var position = 0L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            position = player.state.value.positionMs
            if (position > 250) break
        }
        val state = player.state.value
        println("posición=${position}ms duración=${state.durationMs}ms error=${state.error}")
        player.stop()
        if (position > 0) {
            println("OK: reproducción verificada")
            return 0
        }
        println("FALLO: la posición no avanzó")
        return 1
    }

    private fun runBlockingScan(dir: File) = kotlinx.coroutines.runBlocking { Scanner.scan(dir) }

    /** `--yt-selftest <consulta>`: proves the real yt-dlp path, search and stream resolution. */
    fun runOnline(query: String): Int {
        val bin = YouTubeMusic.binary()
        println("yt-dlp: ${bin?.absolutePath ?: "NO encontrado"}")
        if (bin == null) return 2
        val hits = runCatching { YouTubeMusic.search(query, limit = 3) }
        val list = hits.getOrNull()
        if (list == null) {
            println("FALLO búsqueda: ${hits.exceptionOrNull()?.message}")
            return 3
        }
        list.forEach { println("resultado: ${it.title} · ${it.artist} · ${it.durationMs} ms · ${it.videoId}") }
        val first = list.firstOrNull()
        if (first == null) {
            println("FALLO: la búsqueda no devolvió resultados")
            return 4
        }
        val stream = runCatching { YouTubeMusic.streamUrl(first.videoId) }
        val url = stream.getOrNull()
        if (url == null) {
            println("FALLO stream: ${stream.exceptionOrNull()?.message}")
            return 5
        }
        println("stream: ${url.substringBefore('?').take(100)}")
        if (!url.startsWith("https://")) return 6

        val folder = kotlin.io.path.createTempDirectory("cypher-yt-download").toFile()
        val download = runCatching { YouTubeMusic.download(first.videoId, folder) }
        val file = download.getOrNull()
        if (file == null) {
            println("FALLO descarga: ${download.exceptionOrNull()?.message}")
            return 7
        }
        println("descargado: ${file.name} (${file.length()} bytes)")
        val scanned = Scanner.read(file)
        if (scanned == null) {
            println("FALLO: el archivo descargado no se pudo leer")
            return 8
        }
        println("importable: title='${scanned.title}' artist='${scanned.artist}' durationMs=${scanned.durationMs}")
        return 0
    }

    /**
     * `--yt-app-play <consulta>`: the exact path the Play button takes (AppState →
     * cached audio → AudioPlayer), in a temporary library so nothing real is touched.
     */
    fun runAppPlay(query: String): Int {
        Storage.rootOverride = kotlin.io.path.createTempDirectory("cypher-appplay")
        val state = com.cypher.desktop.app.AppState()
        val hits = runCatching { YouTubeMusic.search(query, limit = 3) }.getOrNull().orEmpty()
        hits.forEach { println("resultado: ${it.title} · ${it.artist} · ${it.videoId}") }
        val hit = hits.firstOrNull() ?: run { println("FALLO: sin resultados"); return 2 }
        state.playOnline(hit)

        val deadline = System.currentTimeMillis() + 90_000
        var position = 0L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(300)
            val playback = state.playback.value
            position = playback.positionMs
            if (position > 1_000) break
            val status = state.state.value.onlineStatus.orEmpty()
            if (status.startsWith("No se pudo")) {
                println("FALLO: $status")
                return 3
            }
        }
        val playback = state.playback.value
        println(
            "estado='${state.state.value.onlineStatus}' titulo='${playback.track?.title}' " +
                "posicion=${position}ms duracion=${playback.durationMs}ms playing=${playback.playing} error=${playback.error}"
        )
        if (position <= 0) {
            println("FALLO: el reproductor no avanzó")
            return 4
        }
        state.togglePlayback()
        Thread.sleep(700)
        val paused = state.playback.value.positionMs
        state.togglePlayback()
        Thread.sleep(1_200)
        val resumed = state.playback.value.positionMs
        state.player.stop()
        println("pausa=${paused}ms reanudado=${resumed}ms")
        if (playback.error == null && resumed > paused) {
            println("OK: reproducción de YouTube Music desde la app verificada")
            return 0
        }
        println("FALLO: pausa/reanudación no se comportó como se esperaba")
        return 5
    }

    /**
     * `--yt-audio <consulta>`: proves real audio, not just metadata. Downloads the first
     * search result into the stream cache and checks that the player's position advances.
     */
    /**
     * `cypher --local-audio <texto>`: does what clicking a local result in the search box
     * does - looks the song up in the library and hands it to the same AudioPlayer - and
     * prints the line the dock shows plus the position after a moment.
     */
    /**
     * `cypher --playlist-demo <id> [cover]`: runs the very functions the buttons call
     * (setPlaylistCover + downloadPlaylist) against the current profile and prints what
     * the playlist ends up holding. Same code path as the UI, no window needed.
     */
    fun runPlaylistDemo(playlistId: String, coverPath: String?): Int {
        val state = com.cypher.desktop.app.AppState()
        val ready = System.currentTimeMillis() + 10_000
        while (state.state.value.data.playlists.isEmpty() && System.currentTimeMillis() < ready) {
            Thread.sleep(100)
        }
        state.state.value.data.playlist(playlistId)?.let { println("playlist: ${it.name} (${it.trackIds.size} pistas)") }

        if (coverPath != null) {
            state.setPlaylistCover(playlistId, File(coverPath))
            println("portada asignada: ${state.state.value.data.playlist(playlistId)?.artwork}")
        }

        state.downloadPlaylist(playlistId)
        val deadline = System.currentTimeMillis() + 240_000
        var last = -1
        while (state.state.value.onlineSaving && System.currentTimeMillis() < deadline) {
            val ui = state.state.value
            if (ui.onlineSaved != last) {
                last = ui.onlineSaved
                println("progreso: ${ui.onlineSaved}/${ui.onlineTotal} ${ui.onlineStatus.orEmpty()}")
            }
            Thread.sleep(1500)
        }

        val data = state.state.value.data
        val playlist = data.playlist(playlistId)
        println("resultado: ${playlist?.name} — portada=${playlist?.artwork}")
        playlist?.trackIds?.forEach { id ->
            val local = data.track(id)
            val remote = data.remoteTrack(id)
            println(
                "  " + (local?.title ?: remote?.title ?: id) + " -> " +
                    if (local != null) "DESCARGADA (${local.path})" else "REFERENCIA (${remote?.videoId})"
            )
        }
        val localCount = playlist?.trackIds?.count { data.track(it) != null } ?: 0
        state.shutdown()
        return if (localCount > 0) 0 else 1
    }

    fun runLocalAudio(query: String): Int {
        val data = Storage.load()
        val hits = data.search(query)
        println("búsqueda «$query»: ${hits.size} coincidencia(s)")
        hits.take(5).forEach { println("  - ${it.title} · ${it.artist} (${it.path})") }
        val track = hits.firstOrNull() ?: run { println("FALLO: la biblioteca no tiene coincidencias"); return 2 }

        val player = AudioPlayer()
        player.initialize()
        println("motor disponible: ${player.available}")
        player.play(track)
        val deadline = System.currentTimeMillis() + 6_000
        var state = player.state.value
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            state = player.state.value
            if (state.positionMs > 900) break
        }
        println("DOCK: ${track.title} · ${track.artist}")
        println("DOCK estado: error=${state.error ?: "ninguno"} playing=${state.playing}")
        println(
            "DOCK tiempo: ${format(state.positionMs)} / ${format(state.durationMs)} " +
                "(posición real ${state.positionMs} ms)"
        )
        player.stop()
        return if (state.positionMs > 500 && state.error == null) 0 else 1
    }

    private fun format(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    fun runAudio(query: String): Int {
        println("yt-dlp: ${YouTubeMusic.binary()?.absolutePath ?: "NO encontrado"}")
        val hits = runCatching { YouTubeMusic.search(query, limit = 3) }.getOrNull().orEmpty()
        hits.forEach { println("resultado: ${it.title} · ${it.artist} · ${it.videoId}") }
        val hit = hits.firstOrNull() ?: run { println("FALLO: sin resultados"); return 2 }

        val download = runCatching {
            YouTubeMusic.cachedAudio(hit.videoId) { println("estado: $it") }
        }
        val file = download.getOrNull()
        if (file == null) {
            println("FALLO audio: ${download.exceptionOrNull()?.message}")
            return 3
        }
        println("audio: ${file.absolutePath} (${file.length()} bytes, .${file.extension})")

        val player = AudioPlayer()
        player.initialize()
        println("motor disponible: ${player.available}")
        if (!player.available) return 4
        player.play(
            com.cypher.desktop.core.Track(
                id = "yt:${hit.videoId}",
                title = hit.title,
                artist = hit.artist,
                album = "YouTube Music",
                durationMs = hit.durationMs,
                path = file.absolutePath,
                artwork = hit.thumbnail
            )
        )
        val deadline = System.currentTimeMillis() + 15_000
        var position = 0L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            position = player.state.value.positionMs
            if (position > 1_500) break
        }
        val playing = player.state.value
        println("posición=${position}ms duración=${playing.durationMs}ms playing=${playing.playing} error=${playing.error}")
        // Pause/resume, the same calls the UI makes.
        player.toggle()
        Thread.sleep(700)
        val pausedAt = player.state.value.positionMs
        player.toggle()
        Thread.sleep(1_200)
        val resumedAt = player.state.value.positionMs
        player.stop()
        println("pausa=${pausedAt}ms reanudado=${resumedAt}ms")
        if (position > 0 && playing.error == null && resumedAt > pausedAt) {
            println("OK: audio real verificado")
            return 0
        }
        println("FALLO: el audio no avanzó como se esperaba")
        return 1
    }

    /** `--yt-playlist <url>`: proves a playlist is read into the streaming queue. */
    fun runPlaylist(url: String): Int {
        val list = runCatching { YouTubeMusic.playlist(url) }
        val value = list.getOrNull()
        if (value == null) {
            println("FALLO playlist: ${list.exceptionOrNull()?.message}")
            return 2
        }
        println("playlist: '${value.title}' · ${value.hits.size} canciones")
        value.hits.take(3).forEach { println("  ${it.videoId} · ${it.title} · ${it.artist}") }
        return if (value.hits.isNotEmpty()) 0 else 3
    }

    /**
     * `--yt-save <url> <n>`: downloads n songs of a list into a temporary library and
     * creates the local playlist there, without touching the real one.
     */
    fun runSave(url: String, limit: Int): Int {
        val root = kotlin.io.path.createTempDirectory("cypher-save")
        Storage.rootOverride = root
        val folder = File(root.toFile(), "descargas")
        val state = com.cypher.desktop.app.AppState()
        state.setOnlineLink(url)
        state.loadOnlinePlaylist()
        val loadDeadline = System.currentTimeMillis() + 180_000
        while (state.state.value.onlineHits.isEmpty() && System.currentTimeMillis() < loadDeadline) Thread.sleep(200)
        val hits = state.state.value.onlineHits
        if (hits.isEmpty()) {
            println("FALLO: no se cargó la lista (${state.state.value.onlineStatus})")
            return 2
        }
        println("lista: '${state.state.value.onlineListName}' · ${hits.size} canciones")
        state.saveOnlinePlaylist(limit = limit, folder = folder)
        val saveDeadline = System.currentTimeMillis() + 600_000
        while (state.state.value.onlineSaving && System.currentTimeMillis() < saveDeadline) Thread.sleep(500)
        val data = Storage.load()
        val playlist = data.playlists.firstOrNull()
        if (playlist == null) {
            println("FALLO: no se creó la playlist (${state.state.value.onlineStatus})")
            return 3
        }
        println("playlist local: '${playlist.name}' · ${playlist.trackIds.size} canciones · portada=${playlist.artwork ?: "ninguna"}")
        playlist.trackIds.forEach { id ->
            data.track(id)?.let { println("  ${it.title} · ${it.artist} · ${File(it.path).length()} bytes") }
        }
        val filesOk = playlist.trackIds.isNotEmpty() && playlist.trackIds.all { id ->
            data.track(id)?.let { File(it.path).isFile } == true
        }
        println("archivos presentes: $filesOk")
        println("estado: ${state.state.value.onlineStatus}")
        return if (playlist.trackIds.isNotEmpty() && filesOk) 0 else 4
    }

    /** `--yt-install-test`: checks the install button's path (download + lookup). */
    /** Real CSV/ZIP analysis against the real library; prints the coverage percentage. */
    fun runSpotifyPlan(paths: List<String>): Int {
        val files = paths.map(::File).filter { it.isFile }
        if (files.isEmpty()) {
            println("FALLO: no se indicó ningún CSV o ZIP existente")
            return 2
        }
        val issues = mutableListOf<ParseIssue>()
        val rows = mutableListOf<SpotifyRow>()
        files.forEach { file ->
            val bytes = runCatching { file.readBytes() }.getOrElse {
                issues += ParseIssue(file.name, "No se pudo leer: ${it.message}")
                return@forEach
            }
            if (file.name.lowercase().endsWith(".zip")) {
                val zip = SpotifyZip.inspect(bytes)
                issues += zip.issues
                zip.csvs.forEach { csv ->
                    val parsed = SpotifyCsv.parseFile(csv.name, csv.bytes)
                    parsed.error?.let { issues += ParseIssue(csv.name, it) }
                    rows += parsed.rows
                }
            } else {
                val parsed = SpotifyCsv.parseFile(file.name, bytes)
                parsed.error?.let { issues += ParseIssue(file.name, it) }
                rows += parsed.rows
            }
        }
        val plan = SpotifyMatcher.plan(rows, Storage.load(), issues, files.map { it.name })
        val coverage = if (plan.songs == 0) 0 else plan.recognized * 100 / plan.songs
        println(
            "archivos=${files.size} listas=${plan.playlists.size} canciones=${plan.songs} " +
                "reconocidas=${plan.recognized} dudosas=${plan.doubtful} sin_coincidencia=${plan.unmatched} cobertura=$coverage%"
        )
        plan.playlists.forEach { playlist ->
            println(
                " - ${playlist.name}: ${playlist.total} canciones, ${playlist.recognized} reconocidas, " +
                    "${playlist.doubtful} dudosas, ${playlist.unmatched} sin coincidencia, ${playlist.duplicates} repetidas"
            )
        }
        plan.issues.forEach { println(" ! ${it.file}: ${it.message}") }
        return if (plan.songs == 0) 3 else 0
    }

    /** Reads a real YouTube Music playlist and prints what an import would contain. */
    fun runPlaylistImport(url: String): Int {
        val result = runCatching {
            YouTubeMusic.playlist(url, Storage.load().preferences.ytDlpPath)
        }
        val list = result.getOrElse {
            println("FALLO: ${it.message}")
            return 2
        }
        val plan = YouTubeImport.plan(list)
        println(
            "lista='${plan.name}' total=${plan.total} no_disponibles=${plan.unavailable} " +
                "portada=${plan.cover != null} origen=${plan.sourceKey}"
        )
        plan.hits.take(10).forEachIndexed { index, hit ->
            println(
                "  ${index + 1}. ${hit.title} — ${hit.artist} ${hit.durationMs / 1000}s " +
                    "id=${hit.videoId.ifBlank { "—" }} disponible=${hit.available}"
            )
        }
        return 0
    }

    /** Real call to the YouTube Data endpoint with a deliberately invalid token. */
    fun runYouTubeProbe(): Int {
        return try {
            val lists = com.cypher.desktop.core.YouTubeData.allPlaylists("cypher-invalid-probe")
            println("inesperado: la API aceptó un token inválido y devolvió ${lists.size} listas")
            3
        } catch (error: com.cypher.desktop.core.YouTubeApiException) {
            println("api_status=${error.status} reason=${error.reason}")
            println("mensaje=${error.message}")
            0
        } catch (error: Exception) {
            println("FALLO: ${error.message}")
            2
        }
    }

    /**
     * `cypher --cef-check`: proves the embedded Chromium starts from wherever it was found
     * (the folder shipped in the installer, or the downloaded one) without opening a
     * window. It prints the resolution, so "YouTube no funciona" can be answered with
     * facts instead of a guess.
     */
    fun runCefCheck(): Int {
        val dataCef = File(Storage.dataDir.toFile(), "cef")
        val existedBefore = dataCef.isDirectory
        println("resources=${System.getProperty("compose.application.resources.dir") ?: "no definido"}")
        println("jpackage=${System.getProperty("jpackage.app-path") ?: "no definido"}")
        println("carpeta de datos=${dataCef.absolutePath} (existía: $existedBefore)")
        println("chromium en uso=${com.cypher.desktop.player.YouTubeEmbed.resolvedInstallDir()?.absolutePath ?: "ninguno"}")

        val started = com.cypher.desktop.player.YouTubeEmbed.prepare()
        println("chromium arrancado=$started")
        println("error=${com.cypher.desktop.player.YouTubeEmbed.error ?: "ninguno"}")
        com.cypher.desktop.player.YouTubeEmbed.diagnostic?.let { issue ->
            println("diagnóstico: ${issue.headline}")
            println("detalle: ${issue.detail.replace("\n", " ")}")
            println("pista: ${issue.hint}")
        }
        val createdNow = !existedBefore && dataCef.isDirectory
        println("descarga/instalación en la carpeta de datos: ${if (createdNow) "sí" else "no"}")
        return if (started) 0 else 1
    }

    fun runInstall(): Int {
        println("destino: ${YouTubeMusic.install()}")
        val found = YouTubeMusic.binary()
        println("detectado: ${found?.absolutePath ?: "ninguno"}")
        if (found == null) return 2
        val version = runCatching {
            ProcessBuilder(found.absolutePath, "--version").redirectErrorStream(true).start()
                .inputStream.bufferedReader().readText().trim()
        }.getOrNull()
        println("versión: $version")
        return if (version.isNullOrBlank()) 3 else 0
    }

    fun main(args: Array<String>) {
        exitProcess(run())
    }

    private fun writeWav(file: File, seconds: Double = 1.0, sampleRate: Int = 44_100) {
        val samples = (sampleRate * seconds).toInt()
        val dataSize = samples * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        for (i in 0 until samples) {
            val value = (Math.sin(2 * Math.PI * 440.0 * i / sampleRate) * 8000).toInt().toShort()
            buffer.putShort(value)
        }
        file.writeBytes(buffer.array())
    }
}
