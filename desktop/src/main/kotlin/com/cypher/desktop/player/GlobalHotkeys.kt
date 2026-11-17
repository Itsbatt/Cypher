package com.cypher.desktop.player

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global media hotkeys using Win32 `RegisterHotKey` via JNA.
 * Operates system-wide even when Cypher is in background, minimized, or in tray.
 */
class GlobalHotkeys : AutoCloseable {

    companion object {
        private const val HOTKEY_PLAY_PAUSE = 101
        private const val HOTKEY_NEXT = 102
        private const val HOTKEY_PREV = 103
        private const val HOTKEY_VOL_UP = 104
        private const val HOTKEY_VOL_DOWN = 105

        private const val MOD_ALT = 0x0001
        private const val MOD_CONTROL = 0x0002
        private const val VK_SPACE = 0x20
        private const val VK_LEFT = 0x25
        private const val VK_UP = 0x26
        private const val VK_RIGHT = 0x27
        private const val VK_DOWN = 0x28
        private const val WM_HOTKEY = 0x0312
        private const val PM_REMOVE = 0x0001
    }

    private var workerThread: Thread? = null
    private val running = AtomicBoolean(false)

    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onVolumeUp: (() -> Unit)? = null
    var onVolumeDown: (() -> Unit)? = null

    fun start() {
        if (running.getAndSet(true)) return

        val thread = Thread({
            val u32 = User32.INSTANCE
            val mods = MOD_CONTROL or MOD_ALT

            u32.RegisterHotKey(null, HOTKEY_PLAY_PAUSE, mods, VK_SPACE)
            u32.RegisterHotKey(null, HOTKEY_NEXT, mods, VK_RIGHT)
            u32.RegisterHotKey(null, HOTKEY_PREV, mods, VK_LEFT)
            u32.RegisterHotKey(null, HOTKEY_VOL_UP, mods, VK_UP)
            u32.RegisterHotKey(null, HOTKEY_VOL_DOWN, mods, VK_DOWN)

            val msg = WinUser.MSG()
            while (running.get()) {
                val hasMsg = u32.PeekMessage(msg, null, 0, 0, PM_REMOVE)
                if (hasMsg) {
                    if (msg.message == WM_HOTKEY) {
                        when (msg.wParam.toInt()) {
                            HOTKEY_PLAY_PAUSE -> onPlayPause?.invoke()
                            HOTKEY_NEXT -> onNext?.invoke()
                            HOTKEY_PREV -> onPrevious?.invoke()
                            HOTKEY_VOL_UP -> onVolumeUp?.invoke()
                            HOTKEY_VOL_DOWN -> onVolumeDown?.invoke()
                        }
                    }
                } else {
                    try {
                        Thread.sleep(30)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

            u32.UnregisterHotKey(null, HOTKEY_PLAY_PAUSE)
            u32.UnregisterHotKey(null, HOTKEY_NEXT)
            u32.UnregisterHotKey(null, HOTKEY_PREV)
            u32.UnregisterHotKey(null, HOTKEY_VOL_UP)
            u32.UnregisterHotKey(null, HOTKEY_VOL_DOWN)
        }, "cypher-global-hotkeys").apply {
            isDaemon = true
            start()
        }

        workerThread = thread
    }

    override fun close() {
        running.set(false)
        workerThread?.interrupt()
        workerThread = null
    }
}
