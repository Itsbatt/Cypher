package com.cypher.desktop.player

import com.cypher.desktop.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Pure Kotlin Discord Rich Presence client over local named pipes (`\\.\pipe\discord-ipc-0`..`9`).
 * No external native DLLs or JNI required.
 */
object DiscordRpc : AutoCloseable {

    private const val DEFAULT_CLIENT_ID = "974333933670006834"
    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1
    private const val OP_CLOSE = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    private var pipe: RandomAccessFile? = null
    private var activeClientId: String = DEFAULT_CLIENT_ID
    private var connected: Boolean = false
    private var lastAttempt: Long = 0L

    @Volatile var enabled: Boolean = true
    @Volatile var customClientId: String? = null

    private var currentTrack: Track? = null
    private var currentPlaying: Boolean = false
    private var currentPositionMs: Long = 0L
    private var currentDurationMs: Long = 0L

    init {
        start()
    }

    private fun start() {
        workerJob?.cancel()
        workerJob = scope.launch {
            while (isActive) {
                delay(2000)
                if (!enabled) {
                    disconnect()
                    continue
                }
                if (currentTrack != null && currentPlaying) {
                    ensureConnected()
                    if (connected) {
                        pushActivity()
                    }
                }
            }
        }
    }

    fun update(track: Track?, playing: Boolean, positionMs: Long, durationMs: Long) {
        currentTrack = track
        currentPlaying = playing
        currentPositionMs = positionMs
        currentDurationMs = durationMs

        if (!enabled) {
            disconnect()
            return
        }

        if (track == null || !playing) {
            if (connected) clearActivity()
            return
        }

        scope.launch {
            ensureConnected()
            if (connected) {
                pushActivity()
            }
        }
    }

    private fun ensureConnected() {
        if (connected && pipe != null) return
        val now = System.currentTimeMillis()
        if (now - lastAttempt < 10_000) return
        lastAttempt = now

        val targetClientId = customClientId?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
        activeClientId = targetClientId

        for (i in 0..9) {
            val pipePath = """\\.\pipe\discord-ipc-$i"""
            if (!File(pipePath).exists()) continue
            try {
                val raf = RandomAccessFile(pipePath, "rw")
                val handshakeJson = """{"v":1,"client_id":"$activeClientId"}"""
                writePacket(raf, OP_HANDSHAKE, handshakeJson)
                val resp = readPacket(raf)
                if (resp != null && resp.first == OP_FRAME) {
                    pipe = raf
                    connected = true
                    return
                } else {
                    raf.close()
                }
            } catch (_: Exception) {
                // Try next pipe
            }
        }
        connected = false
    }

    private fun pushActivity() {
        val raf = pipe ?: return
        val track = currentTrack ?: return
        val now = System.currentTimeMillis()
        val startTs = now - currentPositionMs
        val endTs = if (currentDurationMs > 0) startTs + currentDurationMs else null

        val videoId = track.path.removePrefix("embed:").takeIf { track.path.startsWith("embed:") }
            ?: track.id.removePrefix("yt:").takeIf { track.id.startsWith("yt:") }

        val artwork = track.artwork?.takeIf { it.isNotBlank() }
            ?: videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
            ?: "https://raw.githubusercontent.com/Itsbatt/Cypher/main/desktop/packaging/cypher.ico"

        val displayAlbum = track.album
            .takeIf { it.isNotBlank() }
            ?: "YouTube Music"

        val activity = buildJsonObject {
            put("type", 2) // 2 = Listening to
            put("details", track.title.take(128))
            put("state", track.artist.take(128))
            putJsonObject("timestamps") {
                put("start", startTs)
                if (endTs != null && endTs > now) {
                    put("end", endTs)
                }
            }
            putJsonObject("assets") {
                put("large_image", artwork)
                put("large_text", displayAlbum.take(128))
                put("small_image", if (currentPlaying) "play" else "pause")
                put("small_text", if (currentPlaying) "Reproduciendo" else "En pausa")
            }
            if (!videoId.isNullOrBlank()) {
                put("buttons", buildJsonArray {
                    add(buildJsonObject {
                        put("label", "Escuchar en Cypher")
                        put("url", "https://music.youtube.com/watch?v=$videoId")
                    })
                })
            }
        }

        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid().toInt())
                put("activity", activity)
            }
            put("nonce", UUID.randomUUID().toString())
        }.toString()

        try {
            writePacket(raf, OP_FRAME, payload)
            readPacket(raf)
        } catch (_: Exception) {
            disconnect()
        }
    }

    fun clearActivity() {
        val raf = pipe ?: return
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid().toInt())
            }
            put("nonce", UUID.randomUUID().toString())
        }.toString()
        try {
            writePacket(raf, OP_FRAME, payload)
            readPacket(raf)
        } catch (_: Exception) {
            disconnect()
        }
    }

    private fun writePacket(raf: RandomAccessFile, op: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(op)
        header.putInt(bytes.size)
        raf.write(header.array())
        raf.write(bytes)
    }

    private fun readPacket(raf: RandomAccessFile): Pair<Int, String>? {
        val headerBytes = ByteArray(8)
        raf.readFully(headerBytes)
        val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val op = header.int
        val len = header.int
        if (len < 0 || len > 64 * 1024) return null
        val payloadBytes = ByteArray(len)
        raf.readFully(payloadBytes)
        return op to String(payloadBytes, Charsets.UTF_8)
    }

    private fun disconnect() {
        connected = false
        try {
            pipe?.close()
        } catch (_: Exception) {}
        pipe = null
    }

    override fun close() {
        workerJob?.cancel()
        clearActivity()
        disconnect()
    }
}
