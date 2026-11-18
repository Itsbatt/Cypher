package com.cypher.desktop.ui

import com.cypher.desktop.core.Track
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

/**
 * High-definition (1080p) aesthetic lyric card generator.
 * Produces polished 1080x1350 social share cards ready for Discord, Twitter, or Instagram,
 * with copy-to-clipboard and save-to-PNG capabilities.
 */
object LyricCardRenderer {

    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1350

    fun renderCard(
        track: Track,
        lyricLines: List<String>,
        accentColor: Color = Color(0xC0, 0x84, 0xFC) // Modern lilac / neon purple
    ): BufferedImage {
        val image = BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

            // 1. Deep ambient dark background with subtle gradient
            val bgGradient = GradientPaint(
                0f, 0f, Color(0x0E, 0x0A, 0x14),
                CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), Color(0x18, 0x12, 0x24)
            )
            g.paint = bgGradient
            g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT)

            // 2. Load artwork if available, draw blurred / ambient glow
            val artImage = loadArtwork(track.artwork)
            if (artImage != null) {
                // Background artistic blurred flare
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
                g.drawImage(artImage, -100, -100, CARD_WIDTH + 200, CARD_WIDTH + 200, null)
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)

                // Dark vignette over the background art
                val darkVignette = GradientPaint(
                    0f, 0f, Color(14, 10, 20, 180),
                    0f, CARD_HEIGHT.toFloat(), Color(14, 10, 20, 245)
                )
                g.paint = darkVignette
                g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT)
            }

            // 3. Central Glassmorphic Card Container
            val cardPaddingX = 72f
            val cardPaddingY = 90f
            val cardW = CARD_WIDTH - (cardPaddingX * 2)
            val cardH = CARD_HEIGHT - (cardPaddingY * 2)

            // Outer card subtle drop shadow
            g.color = Color(0, 0, 0, 120)
            g.fill(RoundRectangle2D.Float(cardPaddingX + 6, cardPaddingY + 8, cardW, cardH, 54f, 54f))

            // Card background (Dark translucent surface)
            g.color = Color(24, 18, 32, 230)
            g.fill(RoundRectangle2D.Float(cardPaddingX, cardPaddingY, cardW, cardH, 54f, 54f))

            // Card border (1.5px subtle white/accent stroke)
            g.color = Color(255, 255, 255, 38)
            g.draw(RoundRectangle2D.Float(cardPaddingX, cardPaddingY, cardW, cardH, 54f, 54f))

            // 4. Header: Artwork + Track details
            val contentX = (cardPaddingX + 64).toInt()
            var currentY = (cardPaddingY + 64).toInt()
            val artSize = 170

            if (artImage != null) {
                // Rounded clip for artwork
                val clipShape = RoundRectangle2D.Float(
                    contentX.toFloat(), currentY.toFloat(),
                    artSize.toFloat(), artSize.toFloat(), 32f, 32f
                )
                val originalClip = g.clip
                g.clip = clipShape
                g.drawImage(artImage, contentX, currentY, artSize, artSize, null)
                g.clip = originalClip

                // Artwork border
                g.color = Color(255, 255, 255, 45)
                g.draw(clipShape)
            } else {
                // Placeholder disc
                g.color = Color(40, 32, 54)
                val clipShape = RoundRectangle2D.Float(
                    contentX.toFloat(), currentY.toFloat(),
                    artSize.toFloat(), artSize.toFloat(), 32f, 32f
                )
                g.fill(clipShape)
            }

            // Track title & artist next to artwork
            val textLeft = contentX + artSize + 36
            val maxTextWidth = (cardW - artSize - 140).toInt()

            // Cypher pill tag
            g.color = Color(accentColor.red, accentColor.green, accentColor.blue, 40)
            g.fill(RoundRectangle2D.Float(textLeft.toFloat(), currentY.toFloat(), 140f, 34f, 16f, 16f))
            g.color = accentColor
            g.font = Font("SansSerif", Font.BOLD, 15)
            g.drawString("CYPHER MUSIC", textLeft + 14, currentY + 23)

            // Title
            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 36)
            val titleLines = wrapText(track.title, g, maxTextWidth, 2)
            var titleY = currentY + 76
            for (line in titleLines) {
                g.drawString(line, textLeft, titleY)
                titleY += 42
            }

            // Artist
            g.color = Color(200, 190, 215)
            g.font = Font("SansSerif", Font.PLAIN, 24)
            val artistText = if (track.artist.isNotBlank()) track.artist else "Artista Desconocido"
            val displayArtist = if (g.fontMetrics.stringWidth(artistText) > maxTextWidth) {
                artistText.take(28) + "..."
            } else {
                artistText
            }
            g.drawString(displayArtist, textLeft, titleY + 6)

            // 5. Divider
            currentY += artSize + 56
            g.color = Color(255, 255, 255, 25)
            g.drawLine(contentX, currentY, (cardPaddingX + cardW - 64).toInt(), currentY)
            currentY += 60

            // 6. Lyrics block (Big, highlighted text)
            val lyricMaxWidth = (cardW - 128).toInt()
            val cleanLyrics = lyricLines.filter { it.isNotBlank() }.take(6)

            if (cleanLyrics.isNotEmpty()) {
                g.font = Font("SansSerif", Font.BOLD, 42)
                val lineHeight = 58

                for ((idx, line) in cleanLyrics.withIndex()) {
                    if (currentY > cardPaddingY + cardH - 140) break

                    // Highlight the middle or first line
                    if (idx == 0 || cleanLyrics.size == 1) {
                        g.color = Color.WHITE
                    } else {
                        g.color = Color(230, 220, 245, 220)
                    }

                    val wrapped = wrapText(line, g, lyricMaxWidth, 2)
                    for (wl in wrapped) {
                        if (currentY > cardPaddingY + cardH - 140) break
                        g.drawString(wl, contentX, currentY)
                        currentY += lineHeight
                    }
                    currentY += 12
                }
            } else {
                g.font = Font("SansSerif", Font.ITALIC, 32)
                g.color = Color(160, 150, 175)
                g.drawString("♪ Disfrutando de la música en Cypher ♪", contentX, currentY + 40)
            }

            // 7. Footer: Cypher Branding & Badge
            val footerY = (cardPaddingY + cardH - 54).toInt()
            g.font = Font("SansSerif", Font.BOLD, 18)
            g.color = Color(160, 150, 180)
            g.drawString("CYPHER", contentX, footerY)

            g.font = Font("SansSerif", Font.PLAIN, 16)
            g.color = Color(120, 110, 140)
            g.drawString("•   Música sin anuncios ni restricciones", contentX + 85, footerY)

        } finally {
            g.dispose()
        }

        return image
    }

    private fun loadArtwork(urlStr: String?): BufferedImage? {
        if (urlStr.isNullOrBlank()) return null
        return runCatching {
            if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                ImageIO.read(URL(urlStr))
            } else {
                val f = File(urlStr)
                if (f.isFile) ImageIO.read(f) else null
            }
        }.getOrNull()
    }

    private fun wrapText(text: String, g: Graphics2D, maxWidth: Int, maxLines: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (g.fontMetrics.stringWidth(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    if (lines.size >= maxLines) break
                }
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) {
            lines.add(current.toString())
        }
        if (lines.size == maxLines && words.size > lines.flatMap { it.split(" ") }.size) {
            val last = lines.last()
            lines[lines.size - 1] = last.take(last.length - 3) + "..."
        }
        return lines.ifEmpty { listOf(text.take(35)) }
    }

    fun copyToClipboard(image: BufferedImage): Boolean {
        return runCatching {
            val transferable = ImageTransferable(image)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
            true
        }.getOrDefault(false)
    }

    fun saveToFile(image: BufferedImage, targetFile: File): Boolean {
        return runCatching {
            targetFile.parentFile?.mkdirs()
            ImageIO.write(image, "PNG", targetFile)
            true
        }.getOrDefault(false)
    }

    private class ImageTransferable(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}
