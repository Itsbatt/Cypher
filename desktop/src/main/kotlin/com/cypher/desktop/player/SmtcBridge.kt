package com.cypher.desktop.player

import com.cypher.desktop.core.Track
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Windows System Media Transport Controls bridge.
 *
 * Windows has no Java API for SMTC, so this class runs `cypher-smtc.exe`, a tiny .NET
 * Framework helper (see `desktop/native/SmtcHelper.cs`) that owns a
 * `Windows.Media.Playback.MediaPlayer` and therefore a real media session. The helper is
 * bundled as a resource, extracted to the temp folder on first use and driven with a
 * line protocol over stdin/stdout:
 *
 *   meta|title|artist|album|artworkPath
 *   status|Playing|Paused|Stopped|Closed
 *   timeline|positionMs|durationMs
 *   button|Play|Pause|Next|Previous
 *
 * Everything is best effort: if the helper cannot start (non-Windows, missing runtime),
 * [available] stays false and playback keeps working without the Windows integration.
 */
class SmtcBridge {

    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null

    private val lock = Any()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var ready = false
    private var failed = false

    private var lastMeta: String? = null
    private var lastStatus: String? = null

    private var pendingTrack: Track? = null
    private var pendingPlaying = false
    private var pendingStopped = false
    private var pendingError = false
    private var pendingPosition = 0L
    private var pendingDuration = 0L
    private var lastTimelineAt = 0L

    val available: Boolean
        get() = synchronized(lock) { ready && process?.isAlive == true }

    fun update(track: Track?, playing: Boolean, stopped: Boolean, error: Boolean, positionMs: Long, durationMs: Long) {
        pendingTrack = track
        pendingPlaying = playing
        pendingStopped = stopped
        pendingError = error
        pendingPosition = positionMs
        pendingDuration = durationMs
        if (!ensureStarted()) return
        push(forceTimeline = false)
    }

    private fun ensureStarted(): Boolean {
        if (!isWindows()) return false
        synchronized(lock) {
            if (ready && process?.isAlive == true) return true
            if (failed) return false
            val exe = installHelper() ?: run { failed = true; return false }
            val started = runCatching {
                ProcessBuilder(exe.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: run { failed = true; return false }
            process = started
            writer = BufferedWriter(OutputStreamWriter(started.outputStream, StandardCharsets.UTF_8))
            Thread({ readEvents(started) }, "cypher-smtc-reader").apply { isDaemon = true }.start()
            return true
        }
    }

    private fun readEvents(source: Process) {
        runCatching {
            BufferedReader(InputStreamReader(source.inputStream, StandardCharsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    when {
                        line == "ready" -> {
                            synchronized(lock) { ready = true }
                            push(forceTimeline = true)
                        }
                        line.startsWith("button|") -> when (line.removePrefix("button|").trim()) {
                            "Play" -> onPlay?.invoke()
                            "Pause" -> onPause?.invoke()
                            "Next" -> onNext?.invoke()
                            "Previous" -> onPrevious?.invoke()
                        }
                        line.startsWith("position|") -> line.removePrefix("position|").trim().toLongOrNull()?.let { ms -> onSeek?.invoke(ms) }
                        line.startsWith("error|") -> System.err.println("SMTC: ${line.removePrefix("error|")}")
                    }
                }
            }
        }
        synchronized(lock) {
            ready = false
        }
    }

    private fun push(forceTimeline: Boolean) {
        val out = synchronized(lock) { writer } ?: return
        val track = pendingTrack
        if (track == null) {
            sendStatus(out, "Closed")
            return
        }

        val artwork = track.artwork?.let { path ->
            val file = runCatching { File(path) }.getOrNull()
            if (file != null && file.isFile) file.absolutePath else ""
        } ?: ""

        val meta = "meta|${clean(track.title)}|${clean(track.artist)}|${clean(track.album)}|$artwork"
        val status = when {
            pendingPlaying -> "Playing"
            // A track that could not be read is not paused; it never started.
            pendingStopped || pendingError -> "Stopped"
            else -> "Paused"
        }
        synchronized(lock) {
            if (meta != lastMeta) {
                write(out, meta)
                lastMeta = meta
            }
            if (status != lastStatus) {
                write(out, "status|$status")
                lastStatus = status
            }
        }

        val now = System.currentTimeMillis()
        val shouldSendTimeline = pendingDuration > 0 &&
            (forceTimeline || now - lastTimelineAt >= 1_000)
        if (shouldSendTimeline) {
            lastTimelineAt = now
            write(out, "timeline|${pendingPosition.coerceAtLeast(0)}|$pendingDuration")
        }
    }

    private fun sendStatus(out: BufferedWriter, status: String) {
        synchronized(lock) {
            if (status == lastStatus) return
            write(out, "status|$status")
            lastStatus = status
        }
    }

    private fun write(out: BufferedWriter, line: String) {
        runCatching {
            out.write(line)
            out.newLine()
            out.flush()
        }
    }

    /** `|` is the protocol separator and a newline would desynchronise the helper. */
    private fun clean(value: String): String =
        value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun installHelper(): File? {
        val resource = javaClass.getResourceAsStream("/native/cypher-smtc.exe") ?: return null
        val dir = File(System.getProperty("java.io.tmpdir"), "cypher-smtc")
        dir.mkdirs()
        val target = File(dir, "cypher-smtc.exe")
        return runCatching {
            val bytes = resource.use { input -> input.readBytes() }
            // Always overwrite: a cached helper from another build can have the same size
            // but different code, and then the app silently talks to the old helper.
            runCatching { target.writeBytes(bytes) }
            if (!target.isFile) error("no se pudo extraer el puente SMTC")
            target
        }.getOrNull()
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("windows")

    /** Stops the helper so Windows removes the session when Cypher closes. */
    fun close() {
        synchronized(lock) {
            runCatching {
                writer?.let { out ->
                    out.write("quit")
                    out.newLine()
                    out.flush()
                }
            }
            writer = null
            ready = false
            process?.let { proc ->
                runCatching { proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
                if (proc.isAlive) proc.destroy()
            }
            process = null
        }
    }
}
