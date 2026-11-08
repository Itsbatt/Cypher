package com.cypher.music.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.cypher.music.data.WrappedStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * The annual recap as an exportable image. Same canvas approach as the lyric
 * cards: rendered at the final 1080 px width, previewed at half size, and drawn
 * with the shared CYPHER palette so both exports look like one product.
 */
object WrappedCardRenderer {

    suspend fun render(
        stats: WrappedStats,
        format: ShareFormat,
        accent: Color,
        targetWidth: Int? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val scale = targetWidth?.let { it.toFloat() / format.width } ?: 1f
        val width = (format.width * scale).toInt().coerceAtLeast(1)
        val height = (format.height * scale).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val w = width.toFloat()
        val h = height.toFloat()
        val accentArgb = accent.toArgb()
        val compact = format == ShareFormat.SQUARE
        val margin = w * 0.095f

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                AndroidColor.parseColor("#17131C"),
                AndroidColor.parseColor("#07060A"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, background)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.06f, h * 0.05f, w * (if (compact) 1.3f else 1.0f),
                intArrayOf(withAlpha(accentArgb, 0.30f), AndroidColor.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(w * 0.06f, h * 0.05f, w * (if (compact) 1.3f else 1.0f), glow)

        val labelPaint = text(w * 0.026f, AndroidColor.parseColor("#A9A3B8"), bold = true)
        val bigPaint = text(w * (if (compact) 0.155f else 0.135f), AndroidColor.WHITE, bold = true)
        val unitPaint = text(w * 0.036f, AndroidColor.parseColor("#F8F6FB"))
        val bodyPaint = text(w * 0.032f, AndroidColor.parseColor("#F8F6FB"), bold = true)
        val detailPaint = text(w * 0.026f, AndroidColor.parseColor("#A9A3B8"))
        val footPaint = text(w * 0.024f, AndroidColor.parseColor("#6F697E"))

        var y = h * 0.115f
        canvas.drawText("CYPHER WRAPPED · ${stats.year}", margin, y, labelPaint)

        y += h * (if (compact) 0.16f else 0.14f)
        val minutes = "${stats.totalMinutes}"
        canvas.drawText(minutes, margin, y, bigPaint)
        val minutesWidth = bigPaint.measureText(minutes)
        canvas.drawText("minutos", margin + minutesWidth + w * 0.02f, y, unitPaint)

        y += h * 0.045f
        canvas.drawText("${stats.totalPlays} reproducciones en ${stats.year}", margin, y, detailPaint)

        y += h * 0.035f
        canvas.drawRect(RectF(margin, y, w - margin, y + math(w)), solid(AndroidColor.parseColor("#26FFFFFF")))

        y += h * (if (compact) 0.055f else 0.05f)
        canvas.drawText("ARTISTA DEL AÑO", margin, y, labelPaint)
        y += h * 0.045f
        val artist = stats.topArtist
        canvas.drawText(
            ellipsize(artist?.name ?: "Sin datos todavía", bodyPaint, w - margin * 2f),
            margin, y, bodyPaint
        )
        if (artist != null) {
            y += h * 0.033f
            canvas.drawText(
                "${artist.playCount} reproducciones · ${(artist.share * 100).roundToInt()}% de tu tiempo",
                margin, y, detailPaint
            )
        }

        y += h * (if (compact) 0.05f else 0.045f)
        canvas.drawText("TOP CANCIONES", margin, y, labelPaint)
        val rows = if (compact) 2 else 3
        stats.topTracks.take(rows).forEachIndexed { index, track ->
            y += h * (if (compact) 0.042f else 0.038f)
            canvas.drawText("${index + 1}", margin, y, detailPaint)
            val titleX = margin + w * 0.035f
            canvas.drawText(
                ellipsize(track.title, bodyPaint, w - titleX - margin),
                titleX, y, bodyPaint
            )
        }

        y += h * (if (compact) 0.05f else 0.045f)
        val genre = stats.topGenres.firstOrNull()?.name
        canvas.drawText("GÉNERO", margin, y, labelPaint)
        y += h * 0.033f
        canvas.drawText(genre ?: "Sin datos de género", margin, y, bodyPaint)

        y += h * 0.036f
        canvas.drawText("ÁLBUM", margin, y, labelPaint)
        y += h * 0.033f
        canvas.drawText(
            ellipsize(stats.topAlbum?.name ?: "Sin álbum identificado", bodyPaint, w - margin * 2f),
            margin, y, bodyPaint
        )

        val footerY = h * 0.94f
        canvas.drawText("cypher", margin, footerY, footPaint)
        val credit = "hecho con cypher"
        canvas.drawText(credit, w - margin - footPaint.measureText(credit), footerY, footPaint)

        bitmap
    }

    private fun text(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(
                Typeface.create("sans-serif", Typeface.NORMAL),
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }

    private fun solid(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun math(width: Float): Float = width * 0.0015f

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or (((alpha.coerceIn(0f, 1f) * 255).toInt()) shl 24)

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        var end = value.length
        while (end > 1 && paint.measureText(value.substring(0, end) + "…") > maxWidth) end--
        return value.substring(0, end).trimEnd() + "…"
    }
}
