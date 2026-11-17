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
                existing.title = title
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
            // Positioned offscreen with 1x1 size so no popup window interrupts the user
            created.setSize(1, 1)
            created.setLocation(-32000, -32000)
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
            frame?.dispose()
            frame = null
        }
    }
}
