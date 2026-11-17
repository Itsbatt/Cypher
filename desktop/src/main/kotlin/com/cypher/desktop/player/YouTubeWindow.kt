package com.cypher.desktop.player

import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * The YouTube window: Chromium with the player page, in a window of its own.
 *
 * It is a separate window because Chromium paints on its own native surface — inside the
 * compose canvas it only produced a blank rectangle. The page is the same loopback one the
 * app talks to, so position, duration and play/pause keep flowing to the rest of the app.
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
                existing.toFront()
                return@invokeLater
            }
            val browser = YouTubeEmbed.openBrowser(videoId)
            if (browser == null) {
                onClosed()
                return@invokeLater
            }
            val created = JFrame("YouTube · $title")
            created.layout = BorderLayout()
            created.add(browser.uiComponent, BorderLayout.CENTER)
            created.setSize(900, 540)
            created.setLocationRelativeTo(null)
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
