package com.cypher.music.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

/**
 * Draws the share card with the native canvas instead of capturing Compose.
 *
 * Two reasons: the exported PNG is rendered at its final 1080 px width (so type is
 * crisp, never an upscaled screenshot), and nothing is drawn on the UI thread.
 * The preview uses this same renderer at half width, so what the user sees is
 * exactly what gets shared.
 */
object LyricsCardRenderer {

    suspend fun render(
        context: Context,
        data: LyricsCardData,
        design: ShareDesign,
        format: ShareFormat,
        targetWidth: Int? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val scale = targetWidth?.let { it.toFloat() / format.width } ?: 1f
        val width = (format.width * scale).toInt().coerceAtLeast(1)
        val height = (format.height * scale).toInt().coerceAtLeast(1)

        val needsArtwork = design == ShareDesign.ARTWORK || design == ShareDesign.MINIMAL
        val artwork = if (needsArtwork) loadArtwork(context, data.artworkUrl) else null

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val accent = data.accent.toArgb()

        when (design) {
            ShareDesign.MINIMAL -> drawMinimal(canvas, width, height, data, artwork, accent)
            ShareDesign.ARTWORK -> drawArtwork(canvas, width, height, data, artwork, accent)
            ShareDesign.TYPOGRAPHY -> drawTypography(canvas, width, height, data, accent)
        }
        bitmap
    }

    // ------------------------------------------------------------------ designs

    private fun drawMinimal(
        canvas: Canvas,
        width: Int,
        height: Int,
        data: LyricsCardData,
        artwork: Bitmap?,
        accent: Int
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        paintFill(canvas, w, h, AndroidColor.parseColor("#17131C"), AndroidColor.parseColor("#07060A"))
        radialGlow(canvas, w * 0.10f, h * 0.12f, w * 0.85f, withAlpha(accent, 0.20f))

        val margin = w * 0.098f
        val top = h * 0.115f
        val coverSize = w * 0.125f

        drawCoverCircle(canvas, artwork, margin, top, coverSize, w * 0.14f)

        val metaX = margin + coverSize + w * 0.045f
        val metaWidth = w - metaX - margin
        val titlePaint = textPaint(w * 0.036f, AndroidColor.WHITE, bold = true)
        val artistPaint = textPaint(w * 0.030f, AndroidColor.parseColor("#A9A3B8"))
        val title = ellipsize(titlePaint, data.trackTitle, metaWidth)
        val artist = ellipsize(artistPaint, data.artist, metaWidth)
        val titleBaseline = top + coverSize * 0.44f
        canvas.drawText(title, metaX, titleBaseline, titlePaint)
        canvas.drawText(artist, metaX, titleBaseline + coverSize * 0.34f, artistPaint)

        // Lyrics block: left aligned, with a thin accent rule as the only ornament.
        val blockLeft = margin
        val blockWidth = w - margin * 2f
        val blockTop = h * 0.255f
        val blockBottom = h * 0.815f
        val body = data.lines.joinToString("\n")
        val available = blockBottom - blockTop
        val layout = fitLyrics(
            text = body,
            maxWidth = blockWidth - w * 0.035f,
            maxHeight = available,
            maxLines = 8,
            startSize = w * 0.082f,
            minSize = w * 0.038f,
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL),
            color = AndroidColor.parseColor("#F8F6FB")
        )
        // Center the fragment in its reserved area so short selections do not sit
        // at the top of a large empty block.
        val blockStart = blockTop + (available - layout.totalHeight) / 2f
        drawLines(canvas, layout, blockLeft + w * 0.035f, blockStart, Paint.Align.LEFT)

        val ruleTop = blockStart - h * 0.006f
        val ruleBottom = blockStart + layout.totalHeight
        canvas.drawRoundRect(
            RectF(blockLeft, ruleTop, blockLeft + w * 0.006f, ruleBottom),
            w * 0.003f,
            w * 0.003f,
            solid(accent)
        )

        footer(canvas, w, h, margin, accent, data, dark = true)
    }

    private fun drawArtwork(
        canvas: Canvas,
        width: Int,
        height: Int,
        data: LyricsCardData,
        artwork: Bitmap?,
        accent: Int
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (artwork != null) {
            // Deliberately soft background: a low-resolution source reads as an
            // intentional blur instead of visible pixels behind crisp type.
            val background = softBackground(artwork, width, height)
            drawCover(canvas, background, RectF(0f, 0f, w, h))
            background.recycle()
            // Two scrims: a light one for the header, a strong one for the lyrics.
            canvas.drawRect(
                0f, 0f, w, h * 0.34f,
                Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, h * 0.34f,
                        AndroidColor.argb(150, 0, 0, 0), AndroidColor.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }
            )
            canvas.drawRect(
                0f, h * 0.30f, w, h,
                Paint().apply {
                    shader = LinearGradient(
                        0f, h * 0.30f, 0f, h,
                        AndroidColor.TRANSPARENT, AndroidColor.argb(244, 5, 4, 8),
                        Shader.TileMode.CLAMP
                    )
                }
            )
        } else {
            paintFill(canvas, w, h, AndroidColor.parseColor("#1B1424"), AndroidColor.parseColor("#07060A"))
            radialGlow(canvas, w * 0.8f, h * 0.2f, w, withAlpha(accent, 0.26f))
        }

        val margin = w * 0.098f
        val labelPaint = textPaint(w * 0.023f, AndroidColor.parseColor("#D9D3E4"), bold = true).apply {
            letterSpacing = 0.18f
        }
        canvas.drawText("CYPHER", margin, h * 0.085f, labelPaint)

        val titlePaint = textPaint(w * 0.040f, AndroidColor.WHITE, bold = true)
        val artistPaint = textPaint(w * 0.030f, AndroidColor.parseColor("#CFC8DB"))
        canvas.drawText(
            ellipsize(titlePaint, data.trackTitle, w - margin * 2f),
            margin,
            h * 0.135f,
            titlePaint
        )
        canvas.drawText(
            ellipsize(artistPaint, data.artist, w - margin * 2f),
            margin,
            h * 0.135f + w * 0.052f,
            artistPaint
        )

        val lyricsTop = h * 0.455f
        val lyricsBottom = h * 0.815f
        val layout = fitLyrics(
            text = data.lines.joinToString("\n"),
            maxWidth = w - margin * 2f,
            maxHeight = lyricsBottom - lyricsTop,
            maxLines = 7,
            startSize = w * 0.078f,
            minSize = w * 0.036f,
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            color = AndroidColor.WHITE
        )

        canvas.drawRoundRect(
            RectF(margin, lyricsTop - h * 0.028f, margin + w * 0.055f, lyricsTop - h * 0.022f),
            w * 0.003f,
            w * 0.003f,
            solid(accent)
        )
        drawLines(canvas, layout, margin, lyricsTop, Paint.Align.LEFT)

        footer(canvas, w, h, margin, accent, data, dark = false)
    }

    private fun drawTypography(
        canvas: Canvas,
        width: Int,
        height: Int,
        data: LyricsCardData,
        accent: Int
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        paintFill(canvas, w, h, AndroidColor.parseColor("#0C0A11"), AndroidColor.parseColor("#050408"))
        radialGlow(canvas, w * 0.5f, h * 0.18f, w * 0.95f, withAlpha(accent, 0.18f))

        val margin = w * 0.11f
        val wordmark = textPaint(w * 0.026f, withAlpha(accent, 0.9f), bold = true).apply {
            letterSpacing = 0.42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("CYPHER", w / 2f, h * 0.095f, wordmark)

        // Oversized quote glyph as the single decorative element.
        val quote = textPaint(w * 0.34f, withAlpha(accent, 0.16f)).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("\u201C", margin * 0.55f, h * 0.30f, quote)

        val blockTop = h * 0.24f
        val blockBottom = h * 0.72f
        val layout = fitLyrics(
            text = data.lines.joinToString("\n"),
            maxWidth = w - margin * 2f,
            maxHeight = blockBottom - blockTop,
            maxLines = 8,
            startSize = w * 0.076f,
            minSize = w * 0.034f,
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL),
            color = AndroidColor.parseColor("#F8F6FB")
        )
        drawLines(canvas, layout, w / 2f, blockTop, Paint.Align.CENTER)

        val metaTop = h * 0.795f
        canvas.drawRoundRect(
            RectF(w / 2f - w * 0.03f, metaTop, w / 2f + w * 0.03f, metaTop + h * 0.0025f),
            w * 0.003f,
            w * 0.003f,
            solid(accent)
        )

        val titlePaint = textPaint(w * 0.033f, AndroidColor.WHITE, bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        val artistPaint = textPaint(w * 0.027f, AndroidColor.parseColor("#A9A3B8")).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            ellipsize(titlePaint, data.trackTitle, w - margin * 2f),
            w / 2f,
            metaTop + h * 0.032f,
            titlePaint
        )
        canvas.drawText(
            ellipsize(artistPaint, data.artist, w - margin * 2f),
            w / 2f,
            metaTop + h * 0.032f + w * 0.045f,
            artistPaint
        )

        val foot = textPaint(w * 0.018f, withAlpha(AndroidColor.WHITE, 0.38f), bold = true).apply {
            letterSpacing = 0.30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("FRAGMENTO DE LETRA", w / 2f, h * 0.945f, foot)
    }

    // ------------------------------------------------------------------ helpers

    private fun footer(
        canvas: Canvas,
        w: Float,
        h: Float,
        margin: Float,
        accent: Int,
        data: LyricsCardData,
        dark: Boolean
    ) {
        val lineY = h * 0.885f
        canvas.drawRect(
            margin, lineY, w - margin, lineY + h * 0.0012f,
            solid(withAlpha(AndroidColor.WHITE, if (dark) 0.14f else 0.20f))
        )

        val markPaint = textPaint(w * 0.027f, AndroidColor.WHITE, bold = true).apply {
            letterSpacing = 0.30f
        }
        canvas.drawText("CYPHER", margin, lineY + h * 0.045f, markPaint)

        val dotX = margin + markPaint.measureText("CYPHER") + w * 0.018f
        canvas.drawCircle(dotX, lineY + h * 0.036f, w * 0.0055f, solid(accent))

        val captionPaint = textPaint(w * 0.019f, AndroidColor.parseColor("#A9A3B8")).apply {
            textAlign = Paint.Align.RIGHT
        }
        val album = data.album?.trim()
        val caption = album?.takeIf {
            it.isNotEmpty() &&
                !it.equals("Sencillo", true) &&
                !it.equals("YouTube Music", true) &&
                !it.equals("Álbum local", true)
        } ?: "FRAGMENTO DE LETRA"
        canvas.drawText(
            ellipsize(captionPaint, caption.uppercase(), w * 0.5f),
            w - margin,
            lineY + h * 0.043f,
            captionPaint
        )
    }

    private fun paintFill(canvas: Canvas, w: Float, h: Float, top: Int, bottom: Int) {
        canvas.drawRect(
            0f, 0f, w, h,
            Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
            }
        )
    }

    private fun radialGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        canvas.drawCircle(
            cx, cy, radius,
            Paint().apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(color, AndroidColor.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
    }

    private class LyricsLayout(
        val paint: Paint,
        val lines: List<String>,
        val lineHeight: Float
    ) {
        val totalHeight: Float get() = lines.size * lineHeight
    }

    /**
     * Shrinks the type until the fragment fits the reserved area. It never crops a
     * line: if even the minimum size overflows, it trims whole lines and ellipsizes.
     */
    private fun fitLyrics(
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        maxLines: Int,
        startSize: Float,
        minSize: Float,
        typeface: Typeface,
        color: Int
    ): LyricsLayout {
        var size = startSize
        val lineFactor = 1.34f
        while (size > minSize) {
            val paint = textPaint(size, color).apply { this.typeface = typeface }
            val wrapped = wrap(paint, text, maxWidth)
            if (wrapped.size <= maxLines && wrapped.size * size * lineFactor <= maxHeight) {
                return LyricsLayout(paint, wrapped, size * lineFactor)
            }
            size *= 0.93f
        }

        val paint = textPaint(minSize, color).apply { this.typeface = typeface }
        val wrapped = wrap(paint, text, maxWidth)
        val fits = (maxHeight / (minSize * lineFactor)).toInt().coerceAtLeast(1)
        val allowed = minOf(maxLines, fits)
        val visible = wrapped.take(allowed).toMutableList()
        if (visible.isNotEmpty() && wrapped.size > allowed) {
            visible[visible.lastIndex] = ellipsize(paint, visible.last() + " …", maxWidth)
        }
        return LyricsLayout(paint, visible, minSize * lineFactor)
    }

    private fun drawLines(canvas: Canvas, layout: LyricsLayout, x: Float, top: Float, align: Paint.Align) {
        layout.paint.textAlign = align
        val metrics = layout.paint.fontMetrics
        var baseline = top - metrics.top
        for (line in layout.lines) {
            canvas.drawText(line, x, baseline, layout.paint)
            baseline += layout.lineHeight
        }
    }

    private fun wrap(paint: Paint, text: String, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            val words = paragraph.trim().split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            var current = ""
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) result.add(current)
                    current = word
                }
            }
            if (current.isNotEmpty()) result.add(current)
        }
        return result
    }

    private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            isSubpixelText = true
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun solid(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return AndroidColor.argb(a, AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
    }

    private fun drawCoverCircle(canvas: Canvas, bitmap: Bitmap?, x: Float, y: Float, size: Float, radius: Float) {
        val path = Path().apply {
            addRoundRect(RectF(x, y, x + size, y + size), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        if (bitmap != null) {
            drawCover(canvas, bitmap, RectF(x, y, x + size, y + size))
        } else {
            canvas.drawRect(x, y, x + size, y + size, solid(AndroidColor.parseColor("#2A2333")))
        }
        canvas.restore()
    }

    /** Downscale/upscale blur: cheap, allocation-bounded and good enough for type over it. */
    private fun softBackground(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val smallWidth = 96
        val smallHeight = (96f * height / width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, smallWidth, smallHeight, true)
        val full = Bitmap.createScaledBitmap(small, width, height, true)
        if (small !== full) small.recycle()
        return full
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, dst: RectF) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val dstRatio = dst.width() / dst.height()
        val src = if (srcRatio > dstRatio) {
            val visibleWidth = (bitmap.height * dstRatio).toInt().coerceAtLeast(1)
            Rect((bitmap.width - visibleWidth) / 2, 0, (bitmap.width + visibleWidth) / 2, bitmap.height)
        } else {
            val visibleHeight = (bitmap.width / dstRatio).toInt().coerceAtLeast(1)
            Rect(0, (bitmap.height - visibleHeight) / 2, bitmap.width, (bitmap.height + visibleHeight) / 2)
        }
        canvas.drawBitmap(bitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private suspend fun loadArtwork(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(1200, 1200)
                .build()
            val result = context.imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.let { drawable ->
                (drawable as? BitmapDrawable)?.bitmap
            }
        }.getOrNull()
    }
}
