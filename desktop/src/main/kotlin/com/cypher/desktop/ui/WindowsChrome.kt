package com.cypher.desktop.ui

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Window
import javax.swing.Timer

/**
 * Integrates the native Windows frame with Cypher's palette.
 *
 * The window stays decorated on purpose: dragging, snapping, resizing and the system
 * menu keep working exactly as Windows expects. Only the caption, border and text
 * colours change to the app's near-black and lavender. Every call is best effort and
 * Windows-only: on another system, or an older Windows build, the default frame stays.
 */
object WindowsChrome {

    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

    /** 0x00BBGGRR, the byte order DWM expects. */
    private fun colorRef(rgb: Int): Int =
        ((rgb shr 16) and 0xFF) or (rgb and 0x00FF00) or ((rgb and 0xFF) shl 16)

    fun apply(window: Window) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        val attempting = intArrayOf(0)
        // The native handle only exists once AWT has created the peer, which happens
        // after the first composition. Retry a few frames instead of giving up silently.
        val timer = Timer(400) { event ->
            attempting[0]++
            val ok = runCatching { paint(window) }.getOrElse {
                System.err.println("DWM chrome skipped: ${it.message}")
                false
            }
            if (ok || attempting[0] >= 6) (event.source as Timer).stop()
        }
        timer.isRepeats = true
        timer.start()
    }

    private fun paint(window: Window): Boolean {
        val title = (window as? java.awt.Frame)?.title ?: return false
        val hwnd = User32.INSTANCE.FindWindow(null, title) ?: return false
        val dwm = Native.load("dwmapi", DwmApi::class.java)
        // Windows 10 1809+ understands dark mode; Windows 11 also paints the exact
        // Cypher colours. Attributes unknown to the build are ignored by DWM.
        set(dwm, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1)
        set(dwm, hwnd, DWMWA_CAPTION_COLOR, colorRef(0x0D0B11))
        set(dwm, hwnd, DWMWA_TEXT_COLOR, colorRef(0xF7F5FA))
        set(dwm, hwnd, DWMWA_BORDER_COLOR, colorRef(0x2C2738))
        System.err.println("DWM dark caption applied")
        return true
    }

    private fun set(dwm: DwmApi, hwnd: WinDef.HWND, attribute: Int, value: Int) {
        val data = com.sun.jna.Memory(4)
        data.setInt(0, value)
        dwm.DwmSetWindowAttribute(hwnd, attribute, data, 4)
    }

    private interface DwmApi : com.sun.jna.win32.StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }
}
