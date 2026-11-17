package com.cypher.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.cypher.desktop.core.LyricLine
import com.cypher.desktop.core.Preferences
import org.jetbrains.skia.Canvas as SkiaCanvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lyrics painter.
 *
 * The words are drawn with Skia directly instead of `Text`, because the letters have to
 * carry a real shader: the same family of runtime effects (SkSL here, AGSL on Android)
 * that the app already compiles for the glass. Compose can only paint text with a
 * gradient brush, which is why the full screen used to look flat.
 *
 * Every frame: the active line is filled with the runtime-effect shader and washed with
 * the karaoke progress, while the surrounding lines lose alpha, size and focus with the
 * distance to the centre. The scroll itself is one eased float, so a seek glides instead
 * of jumping.
 *
 * Nothing in here should allocate once the playback is steady: paints, blur filters,
 * shaders and wrapped text are all cached across frames and instances.
 */
@Composable
fun LyricsStage(
    lines: List<LyricLine>,
    positionMs: Long,
    prefs: Preferences,
    accent: Color,
    /** Dominant cover tones; ARTWORK and the LIQUID shader paint with them. */
    artworkColors: List<Color> = emptyList(),
    fullscreen: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val reduceMotion = prefs.reduceMotion || !prefs.animations
    val synced = lines.any { it.atMs >= 0 }
    val active = if (synced) lines.indexOfLast { it.atMs <= positionMs }.coerceAtLeast(0) else -1
    val focus by animateFloatAsState(
        if (active >= 0) active.toFloat() else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 520, easing = FastOutSlowInEasing)
    )

    // One clock drives every animated shader: the letters all breathe on the same time.
    var clock by remember { mutableLongStateOf(0L) }
    // Nothing to animate with the queue stopped: the letters keep the last frame instead
    // of asking the compositor for a new one sixty times a second.
    val animatedShader = prefs.lyricsShader != "NONE" && !reduceMotion && playing && lines.isNotEmpty()
    LaunchedEffect(animatedShader) {
        while (animatedShader) {
            // A timer, not the frame clock: the window only delivers frames when it has a
            // reason to redraw, and a shader alone was not enough of one.
            delay(41)
            clock = System.currentTimeMillis()
        }
    }
    val seconds = clock / 1000f

    Canvas(modifier.clipToBounds()) {
        if (lines.isEmpty()) return@Canvas
        drawIntoCanvas { compose ->
            paintLyrics(
                canvas = compose.nativeCanvas,
                lines = lines,
                active = active,
                synced = synced,
                focus = focus,
                positionMs = positionMs,
                prefs = prefs,
                accent = accent,
                artworkColors = artworkColors,
                fullscreen = fullscreen,
                width = size.width,
                height = size.height,
                density = density,
                seconds = seconds
            )
        }
    }
}

private fun paintLyrics(
    canvas: SkiaCanvas,
    lines: List<LyricLine>,
    active: Int,
    synced: Boolean,
    focus: Float,
    positionMs: Long,
    prefs: Preferences,
    accent: Color,
    artworkColors: List<Color>,
    fullscreen: Boolean,
    width: Float,
    height: Float,
    density: Float,
    seconds: Float
) {
    val spacing = when (prefs.lyricsLineSpacing) {
        "COMPACT" -> 1.55f
        "WIDE" -> 2.25f
        else -> 1.9f
    } * if (fullscreen) 1f else 1.15f
    // The panel is narrow, so a line that does not fit is wrapped there instead of being
    // shrunk to a size nobody can read.
    val baseSize = when (prefs.lyricsSize) {
        "S" -> if (fullscreen) 26f else 14f
        "L" -> if (fullscreen) 40f else 20f
        "XL" -> if (fullscreen) 46f else 22f
        else -> if (fullscreen) 32f else 17f
    }
    val pitch = baseSize * density * spacing
    // Untimed lyrics have no current line: they start at the top and never scroll.
    val centerY = if (!synced) pitch * 1.2f else height * if (fullscreen) 0.46f else 0.44f
    val inset = 5f * density
    // The line starts at `inset` and must end before the canvas does: measuring against
    // that same box is what keeps it inside the panel without a magic factor.
    val maxWidth = (width - inset * 2f).coerceAtLeast(1f)
    // Acento, el color de la portada o uno elegido a mano en Ajustes.
    val activeBase = when (prefs.lyricsColor) {
        "ARTWORK" -> artworkColors.firstOrNull() ?: Color.White
        "CUSTOM" -> Color(prefs.lyricsColorCustom)
        else -> accent
    }
    val secondColor = artworkColors.getOrNull(1) ?: secondTone(activeBase)
    val inactiveAlpha = prefs.lyricsInactiveOpacity.coerceIn(0.15f, 0.9f)

    val visible = ((height / pitch) / 2f).roundToInt() + 2
    val first = (focus.roundToInt() - visible).coerceAtLeast(0)
    val last = (focus.roundToInt() + visible).coerceAtMost(lines.lastIndex)

    for (index in first..last) {
        val text = lines[index].text.ifBlank { "♪" }
        val distance = abs(index - focus)
        val isActive = index == active && synced
        val sung = synced && active >= 0 && index < active

        val sizeScale = if (isActive) 1.12f else (1f - distance * 0.02f).coerceAtLeast(0.86f)
        val fontSize = baseSize * density * sizeScale
        val chunks = if (fullscreen) {
            listOf(Chunk(text, 0, text.length))
        } else {
            chunksFor(text, fontSize, isActive, maxWidth)
        }

        val y = centerY + (index - focus) * pitch
        if (y < -pitch || y > height + pitch) continue

        val alpha = when {
            isActive -> 1f
            sung -> (inactiveAlpha * 0.9f).coerceAtLeast(0.12f)
            else -> (inactiveAlpha * (1f - distance * 0.16f)).coerceIn(0.10f, 1f)
        }
        val blur = if (!isActive && distance > 0.6f) (distance * 3.2f * density).coerceAtMost(14f) else 0f
        val progress = when {
            isActive -> karaokeProgress(lines, index, positionMs, prefs.lyricsAnimation)
            sung -> 1f
            else -> 0f
        }
        // A two-row line is centred on its own slot, so the block keeps its place.
        val rowStep = fontSize * 1.08f
        val firstRowY = y - (chunks.size - 1) * rowStep / 2f

        chunks.forEachIndexed { row, chunk ->
            val rowY = firstRowY + row * rowStep
            var lineFont = fontSize
            var textLine = measureLine(chunk.text, lineFont, isActive)
            if (textLine.width > maxWidth && textLine.width > 0f) {
                // Only a single unbreakable word gets here; the floor keeps it legible.
                lineFont *= (maxWidth / textLine.width).coerceAtLeast(if (fullscreen) 0.62f else 0.55f)
                textLine = measureLine(chunk.text, lineFont, isActive)
            }
            val x = if (prefs.lyricsAlign == "CENTER") (width - textLine.width) / 2f else inset

            canvas.save()
            if (!isActive && distance > 1.4f) {
                val shrink = 1f - (distance * 0.012f).coerceAtMost(0.1f)
                val pivotX = x + textLine.width / 2f
                canvas.translate(pivotX, rowY)
                canvas.scale(shrink, shrink)
                canvas.translate(-pivotX, -rowY)
            }

            // Tone before it is sung.
            basePaint.color = withAlpha(activeBase, alpha)
            basePaint.imageFilter = blurFilter(blur)
            canvas.drawTextLine(textLine, x, rowY, basePaint)

            // Only the line being sung gets a second pass: the rest already carry their
            // final tone, and painting them twice is what left sharp fragments behind.
            val reveal = if (isActive) revealFor(chunk, text.length, progress) else 0f
            if (reveal > 0f) {
                if (prefs.lyricsShader != "NONE") {
                    glowPaint.color = withAlpha(activeBase, 0.5f)
                    glowPaint.maskFilter = glowMaskFilter(
                        (if (prefs.lyricsShader == "NEON") 24f else 13f) * density
                    )
                    canvas.drawTextLine(textLine, x, rowY, glowPaint)
                }
                val shader = lyricsShader(
                    prefs.lyricsShader,
                    textLine.width,
                    textLine.height,
                    seconds,
                    activeBase,
                    secondColor
                )
                if (shader != null) {
                    lightPaint.shader = shader
                } else {
                    lightPaint.shader = null
                    lightPaint.color = withAlpha(activeBase, alpha)
                }
                canvas.save()
                // ascent is negative and descent positive in Skia: the box goes from the
                // top of the glyphs (baseline + ascent) to their bottom (baseline + descent).
                canvas.clipRect(
                    Rect.makeLTRB(
                        x - 2f,
                        rowY + textLine.ascent - 6f * density,
                        x + textLine.width * reveal + 2f,
                        rowY + textLine.descent + 6f * density
                    )
                )
                canvas.drawTextLine(textLine, x, rowY, lightPaint)
                canvas.restore()
            }
            canvas.restore()
        }
    }
}

private fun withAlpha(color: Color, alpha: Float): Int =
    ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (color.toArgb() and 0x00FFFFFF)

/** 0..1 of the line already sung, interpolated between its timestamp and the next one. */
private fun karaokeProgress(lines: List<LyricLine>, index: Int, positionMs: Long, animation: String): Float {
    if (animation == "NONE") return 1f
    val start = lines[index].atMs
    if (start < 0) return 1f
    val end = lines.getOrNull(index + 1)?.atMs?.takeIf { it > start } ?: (start + 4_000)
    return ((positionMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}

/** How much of one wrapped row is lit: the wash runs across the whole line, not per row. */
private fun revealFor(chunk: Chunk, lineLength: Int, progress: Float): Float {
    if (progress <= 0f) return 0f
    if (progress >= 1f) return 1f
    if (lineLength <= 0) return progress
    val from = chunk.from.toFloat() / lineLength
    val to = chunk.to.toFloat() / lineLength
    if (to <= from) return progress
    return ((progress - from) / (to - from)).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------- resources

/**
 * Shared paints. Drawing happens on the UI thread only, so three of them cover the panel
 * and the full screen at once — no new Paint per line, per frame.
 */
private val basePaint = Paint().apply { isAntiAlias = true }
private val glowPaint = Paint().apply { isAntiAlias = true }
private val lightPaint = Paint().apply { isAntiAlias = true }

/** Blur filters by sigma (half-pixel steps): the distance keeps changing, the filters do not. */
private val blurCache = HashMap<Int, ImageFilter>()

private fun blurFilter(sigma: Float): ImageFilter? {
    if (sigma <= 0.15f) return null
    val key = (sigma * 2f).roundToInt().coerceIn(1, 60)
    blurCache[key]?.let { return it }
    if (blurCache.size > 64) blurCache.clear()
    val filter = ImageFilter.makeBlur(key / 2f, key / 2f, FilterTileMode.DECAL)
    blurCache[key] = filter
    return filter
}

private var glowSigma = -1f
private var glowMask: MaskFilter? = null

private fun glowMaskFilter(sigma: Float): MaskFilter? {
    if (abs(sigma - glowSigma) > 0.25f || glowMask == null) {
        glowSigma = sigma
        glowMask = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
    }
    return glowMask
}

// --------------------------------------------------------------------------------- text

private fun family(name: String, bold: Boolean): Typeface? = runCatching {
    FontMgr.default.matchFamilyStyle(name, if (bold) FontStyle.BOLD else FontStyle.NORMAL)
}.getOrNull()

private val typefaceRegular: Typeface? by lazy {
    family("Segoe UI", false) ?: family("Inter", false) ?: family("Arial", false)
}

private val typefaceBold: Typeface? by lazy {
    family("Segoe UI", true) ?: family("Inter", true) ?: family("Arial", true) ?: typefaceRegular
}

private val lineCache = HashMap<String, TextLine>()

private fun measureLine(text: String, sizePx: Float, bold: Boolean): TextLine {
    val key = "${if (bold) 1 else 0}|${sizePx.roundToInt()}|$text"
    lineCache[key]?.let { return it }
    if (lineCache.size > 800) lineCache.clear()
    val typeface = if (bold) typefaceBold else typefaceRegular
    val font = if (typeface != null) Font(typeface, sizePx) else Font(null, sizePx)
    val line = TextLine.make(text, font)
    lineCache[key] = line
    return line
}

private data class Chunk(val text: String, val from: Int, val to: Int)

private val wrapCache = HashMap<String, List<Chunk>>()

/** Splits a lyric line into at most two rows that fit the column, on word boundaries. */
private fun chunksFor(text: String, sizePx: Float, bold: Boolean, maxWidth: Float): List<Chunk> {
    val key = "${if (bold) 1 else 0}|${sizePx.roundToInt()}|${maxWidth.roundToInt()}|$text"
    wrapCache[key]?.let { return it }
    val chunks = wrap(text, sizePx, bold, maxWidth)
    if (wrapCache.size > 600) wrapCache.clear()
    wrapCache[key] = chunks
    return chunks
}

private fun wrap(text: String, sizePx: Float, bold: Boolean, maxWidth: Float): List<Chunk> {
    if (measureLine(text, sizePx, bold).width <= maxWidth) return listOf(Chunk(text, 0, text.length))
    val words = Regex("\\S+").findAll(text).toList()
    if (words.isEmpty()) return listOf(Chunk(text, 0, text.length))

    val chunks = ArrayList<Chunk>(2)
    var startIndex = 0
    var startChar = words.first().range.first
    for (i in words.indices) {
        val candidate = text.substring(startChar, words[i].range.last + 1)
        if (i > startIndex && measureLine(candidate, sizePx, bold).width > maxWidth && chunks.size < 1) {
            val previous = words[i - 1]
            chunks += Chunk(text.substring(startChar, previous.range.last + 1), startChar, previous.range.last + 1)
            startChar = words[i].range.first
            startIndex = i
        }
    }
    chunks += Chunk(text.substring(startChar), startChar, text.length)
    return chunks
}

// ------------------------------------------------------------------------------- shaders

/**
 * Aurora: two travelling waves across the glyphs plus a narrow sheen. The same idea as
 * the ambient shader on the phone, written in SkSL so Skia can compile it here.
 * Uniforms: (width, height, time, accent rgba, second rgba).
 */
private const val AURORA_SKSL = """
uniform float2 uSize;
uniform float uTime;
uniform float4 uAccent;
uniform float4 uAccent2;

half4 main(float2 p) {
    float2 uv = p / max(uSize, float2(1.0, 1.0));
    float w1 = sin((uv.x * 2.0 + uTime * 0.20) * 6.28318) * 0.5 + 0.5;
    float w2 = sin((uv.x * 1.15 - uTime * 0.13 + 0.35) * 6.28318) * 0.5 + 0.5;
    float3 c = mix(uAccent.rgb, uAccent2.rgb, w1);
    c = mix(c, float3(1.0), w2 * 0.42);
    float sheen = pow(max(0.0, sin((uv.x * 1.6 - uTime * 0.35) * 6.28318)), 10.0);
    c += sheen * 0.42;
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/** Glow: the line breathes between the accent and white instead of travelling. */
private const val GLOW_SKSL = """
uniform float2 uSize;
uniform float uTime;
uniform float4 uAccent;
uniform float4 uAccent2;

half4 main(float2 p) {
    float2 uv = p / max(uSize, float2(1.0, 1.0));
    float pulse = 0.5 + 0.5 * sin(uTime * 1.7);
    float core = 1.0 - abs(uv.y * 2.0 - 1.0) * 0.55;
    float3 c = mix(uAccent.rgb, float3(1.0), 0.45 + pulse * 0.3);
    c = mix(c, uAccent2.rgb, core * 0.3);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/**
 * Liquid: bands of the accent and the cover colour run across the glyphs with a moving
 * specular line, the letters' version of the backdrop behind them.
 */
private const val LIQUID_SKSL = """
uniform float2 uSize;
uniform float uTime;
uniform float4 uAccent;
uniform float4 uAccent2;

half4 main(float2 p) {
    float2 uv = p / max(uSize, float2(1.0, 1.0));
    float t = uTime;
    float band = 0.5 + 0.5 * sin((uv.x * 1.30 - t * 0.45) * 6.28318);
    float wash = 0.5 + 0.5 * sin((uv.x * 0.80 + uv.y * 0.70 + t * 0.30) * 6.28318);
    float3 c = mix(uAccent.rgb, uAccent2.rgb, band);
    c = mix(c, float3(1.0), wash * 0.30);
    float spec = pow(max(0.0, sin((uv.x * 1.90 - t * 0.60) * 6.28318)), 14.0);
    c += spec * 0.45;
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/** Neon: the colour on the outside, a white-hot core, breathing slowly. */
private const val NEON_SKSL = """
uniform float2 uSize;
uniform float uTime;
uniform float4 uAccent;
uniform float4 uAccent2;

half4 main(float2 p) {
    float2 uv = p / max(uSize, float2(1.0, 1.0));
    float core = 1.0 - abs(uv.y * 2.0 - 1.0);
    float pulse = 0.5 + 0.5 * sin(uTime * 2.1);
    float3 c = mix(uAccent.rgb, float3(1.0), smoothstep(0.25, 1.0, core) * (0.55 + 0.25 * pulse));
    c = mix(c, uAccent2.rgb, 0.18);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

private val liquidLyricsEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(LIQUID_SKSL) }.getOrNull()
}
private val neonEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(NEON_SKSL) }.getOrNull()
}

private val auroraEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(AURORA_SKSL) }.getOrNull()
}
private val glowEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(GLOW_SKSL) }.getOrNull()
}

/** Whether this machine could build the letters' runtime shader at all. */
val lyricsShaderAvailable: Boolean get() = auroraEffect != null

/** One buffer for the whole process: the uniforms are rewritten in place. */
private val uniformBuffer = ByteBuffer.allocate(11 * 4).order(ByteOrder.LITTLE_ENDIAN)

private fun uniformData(values: FloatArray): Data {
    uniformBuffer.clear()
    values.forEach { uniformBuffer.putFloat(it) }
    return Data.makeFromBytes(uniformBuffer.array())
}

private fun secondTone(accent: Color): Color = Color(
    red = accent.red * 0.30f + 0.62f,
    green = accent.green * 0.25f + 0.16f,
    blue = accent.blue * 0.55f + 0.52f,
    alpha = 1f
)

private fun shaderUniforms(width: Float, height: Float, seconds: Float, a: Color, b: Color): FloatArray =
    floatArrayOf(width.coerceAtLeast(1f), height.coerceAtLeast(1f), seconds) +
        floatArrayOf(a.red, a.green, a.blue, a.alpha) +
        floatArrayOf(b.red, b.green, b.blue, b.alpha)

/** Shaders keyed by their inputs; the animation only needs 24 of them per second. */
private val shaderCache = object : LinkedHashMap<String, Shader?>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Shader?>?) = size > 16
}

private fun lyricsShader(
    effect: String,
    width: Float,
    height: Float,
    seconds: Float,
    accent: Color,
    second: Color
): Shader? {
    if (effect == "NONE") return null
    // Rebuilding the Skia shader (and its uniform block) every frame was the most
    // expensive thing in this painter; 24 steps per second look identical.
    val tick = (seconds * 24f).toInt()
    val key = "$effect|${width.roundToInt()}|${height.roundToInt()}|$tick|${accent.value}|${second.value}"
    if (shaderCache.containsKey(key)) return shaderCache[key]
    val built = buildShader(effect, width, height, tick / 24f, accent, second)
    shaderCache[key] = built
    return built
}

private fun buildShader(
    effect: String,
    width: Float,
    height: Float,
    seconds: Float,
    accent: Color,
    second: Color
): Shader? {
    val runtime = when (effect) {
        "AURORA" -> auroraEffect
        "GLOW" -> glowEffect
        "LIQUID" -> liquidLyricsEffect
        "NEON" -> neonEffect
        else -> null
    }
    if (runtime != null) {
        val shader = runCatching {
            runtime.makeShader(
                uniformData(shaderUniforms(width, height, seconds, accent, second)),
                null,
                null
            )
        }.getOrNull()
        if (shader != null) return shader
    }
    return runCatching {
        Shader.makeLinearGradient(
            0f, 0f, width.coerceAtLeast(1f), 0f,
            intArrayOf(accent.toArgb(), Color.White.toArgb(), second.toArgb()),
            floatArrayOf(0f, 0.55f, 1f)
        )
    }.getOrNull()
}

/** Sample lines for the live preview in Ajustes. */
fun lyricPreviewLines(): List<LyricLine> = listOf(
    LyricLine(0, "Voy a buscarte, cuando no"),
    LyricLine(2_200, "confiado en algo,"),
    LyricLine(4_400, "entre las luces de la ciudad"),
    LyricLine(6_800, "olvidados en el parque")
)
