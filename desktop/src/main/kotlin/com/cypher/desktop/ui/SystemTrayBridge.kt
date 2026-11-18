package com.cypher.desktop.ui

import com.cypher.desktop.core.Track
import java.awt.AWTException
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Windows System Tray (bandeja del sistema) integration.
 * Allows Cypher to minimize to tray when the close button is clicked,
 * providing background controls and status next to the taskbar clock.
 */
class SystemTrayBridge : AutoCloseable {

    private var trayIcon: TrayIcon? = null

    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onRestore: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null
    var onVolumeUp: (() -> Unit)? = null
    var onVolumeDown: (() -> Unit)? = null

    fun initialize(iconImage: Image? = null) {
        if (!SystemTray.isSupported()) return
        if (trayIcon != null) return

        SwingUtilities.invokeLater {
            try {
                val tray = SystemTray.getSystemTray()
                val img = iconImage ?: defaultImage() ?: return@invokeLater

                val popup = PopupMenu()

                val playPauseItem = MenuItem("Reproducir / Pausa").apply {
                    addActionListener { onPlayPause?.invoke() }
                }
                val nextItem = MenuItem("Siguiente").apply {
                    addActionListener { onNext?.invoke() }
                }
                val prevItem = MenuItem("Anterior").apply {
                    addActionListener { onPrevious?.invoke() }
                }
                val openItem = MenuItem("Mostrar Cypher").apply {
                    addActionListener { onRestore?.invoke() }
                }
                val exitItem = MenuItem("Salir").apply {
                    addActionListener { onExit?.invoke() }
                }

                popup.add(openItem)
                popup.addSeparator()
                popup.add(playPauseItem)
                popup.add(nextItem)
                popup.add(prevItem)
                popup.addSeparator()
                popup.add(exitItem)

                val created = TrayIcon(img, "Cypher Desktop", popup).apply {
                    isImageAutoSize = true
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) {
                                onRestore?.invoke()
                            }
                        }
                    })
                }

                tray.add(created)
                trayIcon = created
            } catch (_: AWTException) {
                // System tray unavailable
            }
        }
    }

    fun updateTooltip(track: Track?, playing: Boolean) {
        val icon = trayIcon ?: return
        val text = when {
            track != null -> "Cypher · ${track.title} - ${track.artist} (${if (playing) "▶" else "❚❚"})"
            else -> "Cypher Desktop"
        }
        SwingUtilities.invokeLater {
            icon.toolTip = text.take(127)
        }
    }

    private fun defaultImage(): Image? {
        val url = javaClass.classLoader.getResource("cypher.png") ?: return null
        return Toolkit.getDefaultToolkit().getImage(url)
    }

    override fun close() {
        val icon = trayIcon ?: return
        trayIcon = null
        SwingUtilities.invokeLater {
            runCatching {
                SystemTray.getSystemTray().remove(icon)
            }
        }
    }
}
