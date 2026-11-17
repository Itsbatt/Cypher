package com.cypher.desktop.player

import java.awt.BorderLayout
import java.awt.Window
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Background host for embedded Chromium (JCEF).
 *
 * Chromium requires a native window/surface to attach its browser component and execute
 * the loopback player page. We host it in an off-screen, undecorated utility window so that
 * audio playback and timecode synchronization stream smoothly without popping up an ugly
 * window in the user's workspace.
 */
object YouTubeWindow {

    private var frame: JFrame? = null

    val isOpen: Boolean get() = frame?.isDisplayable == true

    fun open(videoId: String, title: String, onClosed: () -> Unit) {
        SwingUtilities.invokeLater {
            val existing = frame
            if (existing != null && existing.isDisplayable) {
                YouTubeEmbed.openBrowser(videoId)
                existing.title = "YouTube · $title"
                return@invokeLater
            }
            val browser = YouTubeEmbed.openBrowser(videoId)
            if (browser == null) {
                onClosed()
                return@invokeLater
            }
            val created = JFrame("YouTube · $title")
            created.isUndecorated = true
            created.type = Window.Type.UTILITY
            created.layout = BorderLayout()
            created.add(browser.uiComponent, BorderLayout.CENTER)
            // Real 16:9 dimensions satisfy YouTube's iframe player, positioned off-screen to avoid visual disturbance
            created.setSize(640, 360)
            created.setLocation(-10000, -10000)
            created.isAutoRequestFocus = false
            created.focusableWindowState = false
            created.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosing(event: java.awt.event.WindowEvent?) {
                    YouTubeEmbed.command("pause")
                    onClosed()
                }
            })
            created.isVisible = true
            frame = created
        }
    }

    fun close() {
        SwingUtilities.invokeLater {
            YouTubeEmbed.closeBrowser()
            frame?.dispose()
            frame = null
        }
    }
}
