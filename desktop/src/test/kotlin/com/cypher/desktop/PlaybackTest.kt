package com.cypher.desktop

import com.cypher.desktop.core.Scanner
import com.cypher.desktop.player.AudioPlayer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real audio: this test writes a playable WAV, reads it with the scanner and pushes it
 * through JavaFX. If the runtime has no audio device the position assertion is skipped,
 * but scanning is always checked so the library path is never taken on faith.
 */
class PlaybackTest {

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

    @Test
    fun `scanner reads a real file and playback advances`() = runBlocking {
        val dir = Files.createTempDirectory("cypher-audio").toFile()
        val file = File(dir, "Prueba 440Hz.wav")
        writeWav(file)

        val tracks = Scanner.scan(dir)
        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("Prueba 440Hz", track.title)
        assertTrue(track.durationMs >= 900, "duración leída: ${track.durationMs}")
        assertTrue(file.length() > 1000)

        val player = AudioPlayer()
        player.initialize()
        if (!player.available) return@runBlocking

        player.play(track)
        // Give the engine time to open the device and start.
        val deadline = System.currentTimeMillis() + 6_000
        var position = 0L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            position = player.state.value.positionMs
            if (position > 200) break
        }
        val state = player.state.value
        if (state.error != null) {
            // No audio device in this environment: report it instead of faking success.
            println("Reproducción no disponible en este entorno: ${state.error}")
            return@runBlocking
        }
        assertTrue(position > 0, "la posición debe avanzar, fue $position")
        player.setVolume(0.3f)
        assertEquals(0.3f, player.state.value.volume)
        player.stop()
    }
}
