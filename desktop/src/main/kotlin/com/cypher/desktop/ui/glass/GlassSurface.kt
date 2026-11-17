@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.cypher.desktop.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Liquid Glass for the desktop, ported from the Android AGSL shader family.
 *
 * Android paints an ambient mesh behind the Now Playing sheet; its glass language lives
 * in three ideas: a refracted backdrop, a chromatic fringe at the rim, and a light
 * specular sweep. There is no `RuntimeShader` in Compose Desktop, but Skia is right
 * underneath, so the surface shader is real SkSL running on the GPU — the same family
 * of maths, adapted to a desktop window: the backdrop is sampled as a texture instead
 * of being recomputed in the shader, which keeps the refraction honest for photographs
 * too.
 *
 * Everything degrades gracefully: if the GPU cannot compile the shader, [GlassSupport]
 * reports it, the surface falls back to a layered Compose gradient that keeps the rim
 * and the specular, and Settings shows the state instead of hiding it.
 */
object GlassSpec {
    /** Alpha of the glass itself. Android uses 0.65 for its background; a panel needs less. */
    const val OPACITY = 0.62f
    /** How much the backdrop is pulled toward a dark tint so text stays readable. */
    const val TINT = 0.14f
    /** Sampling resolution of the backdrop texture (px). Lower = softer, cheaper. */
    const val BACKDROP_WIDTH = 320
    const val BACKDROP_HEIGHT = 180
    /** Fresnel band width, in px, and its strength. */
    const val RIM_WIDTH = 30f
    const val RIM = 0.55f
    /** Fixed top-left highlight. */
    const val SPECULAR = 0.13f
    /** Max displacement of the refracted sample at the rim, in px. */
    const val REFRACTION = 18f
    /** Corner radius used by the shader; the Compose shape must match. */
    const val RADIUS = 20f
    /** Grain amplitude, the same value Android uses to kill OLED banding. */
    const val GRAIN = 0.018f

    /** Force the effect off (or on) without rebuilding: -Dcypher.glass=off|on. */
    val forced: String? = System.getProperty("cypher.glass")

    val enabled: Boolean get() = forced != "off"
}

/** Whether the GPU could compile the shader. Settings reads this to say which path is live. */
object GlassSupport {
    @Volatile
    var shaderCompiled: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** Set when frames stop fitting the budget: the glass keeps its rim, loses the lens. */
    @Volatile
    var degradedReason: String? = null
        private set

    val shaderActive: Boolean get() = shaderCompiled && degradedReason == null

    internal fun record(ok: Boolean, error: Throwable? = null) {
        shaderCompiled = ok
        lastError = error?.message
    }

    /**
     * Adaptive quality. If the shader cannot hold the frame budget on this machine, the
     * panels fall back to the layered gradient instead of dragging the whole UI down.
     * The reason is kept and shown in Settings: nothing is disabled silently.
     */
    internal fun degrade(reason: String) {
        if (degradedReason == null) degradedReason = reason
    }

    /** Rolling cost of one glass panel draw, in milliseconds. */
    internal object Perf {
        // A long window with a generous budget: one slow first frame (shader JIT, texture
        // upload) must never cost the user the effect.
        private const val WINDOW = 90
        private const val BUDGET_MS = 12.0
        private val samples = DoubleArray(WINDOW)
        private var count = 0
        private var index = 0

        @Synchronized
        fun record(millis: Double) {
            samples[index] = millis
            index = (index + 1) % WINDOW
            if (count < WINDOW) count++
            if (count < WINDOW) return
            val average = samples.sum() / WINDOW
            if (average > BUDGET_MS && degradedReason == null) {
                degrade("un panel de cristal tarda " + String.format(java.util.Locale.US, "%.1f", average) + " ms")
            }
        }
    }
}

/** Compiled once per process: SkSL JIT is not free and must never run per frame. */
private val glassEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(GLASS_SKSL) }
        .onSuccess { GlassSupport.record(true) }
        .onFailure { GlassSupport.record(false, it) }
        .getOrNull()
}

private const val GLASS_SKSL = """
uniform float2 uSize;
uniform float2 uOrigin;
uniform float2 uWindow;
uniform float2 uImageSize;
uniform float  uRadius;
uniform float  uTime;
uniform float  uOpacity;
uniform float  uDensity;
uniform float  uTint;
uniform float  uRim;
uniform float  uRimWidth;
uniform float  uSpecular;
uniform float  uRefraction;
uniform float  uGrain;
uniform half4  uAccent;
uniform shader uBackdrop;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float2 centered = fragCoord - halfSize;
    float dist = sdRoundRect(centered, halfSize, uRadius);
    float inside = clamp(-dist, 0.0, 1.0);

    // Distance to the border grows inward: 0 on the rim, 1 further in.
    float band = 1.0 - smoothstep(0.0, uRimWidth, inside);

    // Outward normal, derived from the position inside the rounded rect.
    float2 safe = centered + float2(0.0001, 0.0001);
    float2 dir = normalize(safe);

    // Lens: the sample is pulled toward the centre near the rim, which is what makes
    // the edge look curved instead of merely translucent.
    float lens = smoothstep(0.0, uRimWidth * 1.4, inside);
    float2 offset = -dir * (1.0 - lens) * uRefraction;

    float2 baseUv = (uOrigin + fragCoord) / uWindow;
    float2 shift = offset / uWindow;

    // Chromatic dispersion: the three channels are refracted by slightly different
    // amounts at the rim, exactly like Android's dispersion term.
    half4 cr = uBackdrop.eval((baseUv + shift * float2(1.18, 1.0)) * uImageSize);
    half4 cg = uBackdrop.eval((baseUv + shift) * uImageSize);
    half4 cb = uBackdrop.eval((baseUv + shift * float2(0.82, 1.12)) * uImageSize);
    half3 glass = half3(cr.r, cg.g, cb.b);

    // Legibility: pull the colour toward the shell tint, then lift the centre a little.
    // [uDensity] is the per-surface frost: menus sit on top of everything and need more
    // body than the player, which is read at a glance.
    glass = mix(glass, half3(0.074, 0.069, 0.098), clamp(uTint + uDensity * 0.78, 0.0, 0.92));
    glass *= (0.94 + 0.14 * lens) * (1.0 - uDensity * 0.20);

    // Fresnel rim: light trapped at the edge, tinted with the accent.
    glass += uAccent.rgb * band * uRim * 0.32;
    glass += half3(band * uRim * 0.085);

    float2 uv = fragCoord / uSize;

    // Fixed specular in the upper-left corner.
    float spec = smoothstep(0.85, 0.0, length((uv - float2(0.24, 0.06)) * float2(1.0, 2.4)));
    glass += half3(spec * uSpecular);

    // Slow travelling sheen, so the surface feels alive without being a light show.
    float sheenX = fract(uv.x * 0.85 + uTime * 0.012);
    float sheen = smoothstep(0.30, 0.0, abs(sheenX - 0.5)) * smoothstep(0.35, 0.0, abs(uv.y - 0.18));
    glass += half3(sheen * 0.022);

    // Depth: darker toward the bottom, a thin light line at the very top.
    glass *= mix(1.0, 0.88, smoothstep(0.45, 1.0, uv.y));
    glass += half3(smoothstep(0.05, 0.0, uv.y) * 0.045);

    // Grain: same trick as Android, against banding on flat gradients.
    glass += half3((fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453) - 0.5) * uGrain);

    return half4(clamp(glass, 0.0, 1.0), mix(uOpacity, 0.99, uDensity));
}
"""

/**
 * The picture the glass refracts: a small texture of the app background.
 *
 * Built once per background/accent change, never per frame. 320×180 is enough because
 * the sampling is linear and the surface itself adds the rim; anything bigger only
 * costs upload time.
 */
class GlassBackdrop internal constructor(val image: Image) {

    /** Child shader handed to the panel shader; created once, reused by every panel. */
    val shader: org.jetbrains.skia.Shader by lazy {
        image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, Matrix33.IDENTITY)
    }

    companion object {
        fun build(mode: String, imagePath: String?, accent: Color): GlassBackdrop? {
            if (!GlassSpec.enabled) return null
            return runCatching {
                val width = GlassSpec.BACKDROP_WIDTH
                val height = GlassSpec.BACKDROP_HEIGHT
                val pixels = ByteArray(width * height * 4)
                val photo = if (mode == "IMAGE") loadThumbnail(imagePath, width, height) else null
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val i = (y * width + x) * 4
                        val rgb = if (photo != null) {
                            photo(x, y)
                        } else {
                            procedural(mode, accent, x / width.toFloat(), y / height.toFloat())
                        }
                        pixels[i] = rgb[0].toByte()
                        pixels[i + 1] = rgb[1].toByte()
                        pixels[i + 2] = rgb[2].toByte()
                        pixels[i + 3] = 0xFF.toByte()
                    }
                }
                val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
                GlassBackdrop(Image.makeRaster(info, pixels, width * 4))
            }.getOrNull()
        }

        /** Same base tone and accent pools the shell paints behind the panels. */
        private fun procedural(mode: String, accent: Color, u: Float, v: Float): IntArray {
            val baseR = 0.051f
            val baseG = 0.043f
            val baseB = 0.067f
            var r = baseR
            var g = baseG
            var b = baseB
            if (mode != "ORIGINAL") {
                // Linear sweep, then two accent pools: the GLASS recipe of CypherBackground.
                val sweep = (u * 0.35f + v * 0.65f).coerceIn(0f, 1f)
                r += (0.102f - r) * sweep
                g += (0.082f - g) * sweep
                b += (0.149f - b) * sweep
                r = pool(r, accent.red, u, v, 0.28f, 0.18f, 0.62f, 0.30f)
                g = pool(g, accent.green, u, v, 0.28f, 0.18f, 0.62f, 0.30f)
                b = pool(b, accent.blue, u, v, 0.28f, 0.18f, 0.62f, 0.30f)
                r = pool(r, Color(0xFF7A588E).red, u, v, 0.78f, 0.82f, 0.70f, 0.22f)
                g = pool(g, Color(0xFF7A588E).green, u, v, 0.78f, 0.82f, 0.70f, 0.22f)
                b = pool(b, Color(0xFF7A588E).blue, u, v, 0.78f, 0.82f, 0.70f, 0.22f)
            }
            return intArrayOf(
                (r.coerceIn(0f, 1f) * 255).toInt(),
                (g.coerceIn(0f, 1f) * 255).toInt(),
                (b.coerceIn(0f, 1f) * 255).toInt()
            )
        }

        private fun pool(value: Float, target: Float, u: Float, v: Float, cx: Float, cy: Float, radius: Float, alpha: Float): Float {
            val d = kotlin.math.hypot(u - cx, v - cy) / radius
            if (d >= 1f) return value
            val a = alpha * (1f - d * d)
            return value + (target - value) * a
        }

        /** Reads the user's picture once, scaled down, with the same dim the shell applies. */
        private fun loadThumbnail(path: String?, width: Int, height: Int): ((Int, Int) -> IntArray)? {
            if (path.isNullOrBlank()) return null
            val file = File(path)
            if (!file.isFile) return null
            return runCatching {
                val source = ImageIO.read(file) ?: return null
                val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                val graphics = scaled.createGraphics()
                graphics.drawImage(source, 0, 0, width, height, null)
                graphics.dispose()
                val out = IntArray(width * height)
                scaled.getRGB(0, 0, width, height, out, 0, width)
                val dim = 0.55f
                val sample: (Int, Int) -> IntArray = { x, y ->
                    val argb = out[y * width + x]
                    intArrayOf(
                        (((argb shr 16) and 0xFF) * dim).toInt(),
                        (((argb shr 8) and 0xFF) * dim).toInt(),
                        ((argb and 0xFF) * dim).toInt()
                    )
                }
                sample
            }.getOrNull()
        }
    }
}

/** The backdrop shared by every panel of one window, rebuilt only when it changes. */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Time for the sheen. Driven by frames (not by recomposition): the value is read inside
 * the draw block, so a new frame repaints the surface without recomposing the tree —
 * the mistake the Android shader still makes.
 */
@Composable
fun rememberGlassTime(): Float {
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time = (now - start) / 1_000_000_000f
        }
    }
    return time
}

/**
 * Paints the glass over whatever this element sits on.
 *
 * [radius] is in dp and must match the shape used for clipping, so the rim lands on the
 * visible edge. The element must keep its own padding: the glass only paints the
 * background, it never moves content.
 */
fun Modifier.cypherGlass(
    accent: Color,
    cornerRadiusDp: Float = GlassSpec.RADIUS,
    opacity: Float = GlassSpec.OPACITY,
    /** 0 = pure lens, 1 = almost opaque plate. Menus need more than the player. */
    density: Float = 0.35f,
    strength: Float = 1f
): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val window = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    val time = rememberGlassTime()
    val pixelsPerDp = androidx.compose.ui.platform.LocalDensity.current

    this
        .onGloballyPositioned { coordinates ->
            origin = coordinates.positionInWindow()
        }
        .drawWithCache {
            val radiusPx = cornerRadiusDp * pixelsPerDp.density
            val windowWidth = window.width.toFloat().takeIf { it > 1f } ?: size.width
            val windowHeight = window.height.toFloat().takeIf { it > 1f } ?: size.height
            onDrawBehind {
                val shader = if (backdrop != null && GlassSupport.degradedReason == null) {
                    buildShader(
                        backdropShader = backdrop.shader,
                        backdrop = backdrop.image,
                        size = size.width to size.height,
                        origin = origin.x to origin.y,
                        window = windowWidth to windowHeight,
                        radius = radiusPx,
                        time = time,
                        opacity = opacity,
                        density = density,
                        accent = accent,
                        strength = strength
                    )
                } else {
                    null
                }
                if (shader == null) {
                    // Degraded path: layered gradients that keep the rim and the specular.
                    drawDegradedGlass(accent, radiusPx, opacity)
                    return@onDrawBehind
                }
                val started = System.nanoTime()
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val count = native.save()
                    native.clipRRect(RRect.makeXYWH(0f, 0f, size.width, size.height, radiusPx))
                    native.drawRect(
                        org.jetbrains.skia.Rect.makeWH(size.width, size.height),
                        Paint().apply {
                            this.shader = shader
                            isAntiAlias = true
                        }
                    )
                    native.restoreToCount(count)
                }
                GlassSupport.Perf.record((System.nanoTime() - started) / 1_000_000.0)
            }
        }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDegradedGlass(
    accent: Color,
    radiusPx: Float,
    opacity: Float
) {
    val edge = androidx.compose.ui.geometry.CornerRadius(radiusPx)
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                Color(0xFF16141D).copy(alpha = 0.92f),
                Color.Black.copy(alpha = 0.30f)
            )
        ),
        cornerRadius = edge,
        alpha = opacity + 0.12f
    )
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.06f),
            radius = size.maxDimension * 0.9f
        ),
        cornerRadius = edge
    )
}

/** Builds the SkSL shader for one panel and one frame. */
private fun buildShader(
    backdropShader: org.jetbrains.skia.Shader,
    backdrop: Image,
    size: Pair<Float, Float>,
    origin: Pair<Float, Float>,
    window: Pair<Float, Float>,
    radius: Float,
    time: Float,
    opacity: Float,
    density: Float,
    accent: Color,
    strength: Float
): org.jetbrains.skia.Shader? = runCatching {
    val effect = glassEffect ?: error("shader no disponible: " + GlassSupport.lastError)
    val builder = RuntimeShaderBuilder(effect)
    builder.uniform("uSize", size.first, size.second)
    builder.uniform("uOrigin", origin.first, origin.second)
    builder.uniform("uWindow", window.first.coerceAtLeast(1f), window.second.coerceAtLeast(1f))
    builder.uniform("uImageSize", backdrop.width.toFloat(), backdrop.height.toFloat())
    builder.uniform("uRadius", radius)
    builder.uniform("uTime", time)
    builder.uniform("uOpacity", opacity)
    builder.uniform("uDensity", density)
    builder.uniform("uTint", GlassSpec.TINT)
    builder.uniform("uRim", GlassSpec.RIM * strength)
    builder.uniform("uRimWidth", GlassSpec.RIM_WIDTH)
    builder.uniform("uSpecular", GlassSpec.SPECULAR * strength)
    builder.uniform("uRefraction", GlassSpec.REFRACTION * strength)
    builder.uniform("uGrain", GlassSpec.GRAIN)
    builder.uniform("uAccent", accent.red, accent.green, accent.blue, 1f)
    builder.child("uBackdrop", backdropShader)
    builder.makeShader()
}.getOrElse { error ->
    GlassSupport.degrade("el shader falló al construirse: " + error.message)
    null
}

/**
 * Wraps a whole screen region so every panel below it can refract the same backdrop.
 * A single texture per window: panels never rebuild it.
 */
@Composable
fun GlassBackdropProvider(
    mode: String,
    imagePath: String?,
    accent: Color,
    content: @Composable () -> Unit
) {
    val backdrop = remember(mode, imagePath, accent) {
        GlassBackdrop.build(mode, imagePath, accent)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop
    ) { Box { content() } }
}
