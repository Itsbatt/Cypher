package com.cypher.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.cypher.desktop.core.Preferences
import org.jetbrains.skia.Data
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Shader
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Liquid glass behind the lyrics, the desktop reading of the phone's AGSL shader.
 *
 * Three colour fields taken from the cover drift and fold over each other (the domain is
 * warped by a slow sine pair, which is what makes the edges look poured rather than
 * radial), a wide sheen crosses the screen and thin caustics travel on top. The result is
 * darkened towards the corners so white letters keep their contrast.
 *
 * Written in SkSL because that is what Skia compiles at runtime here; the shader and its
 * uniform block are cached per tick, so a steady screen costs one draw call.
 */
private const val LIQUID_SKSL = """
uniform float2 uSize;
uniform float uTime;
uniform float4 uA;
uniform float4 uB;
uniform float4 uC;

float2 spin(float2 p, float a) {
    float s = sin(a);
    float c = cos(a);
    return float2(p.x * c - p.y * s, p.x * s + p.y * c);
}

// Peso de cada campo en un punto: tres gaussianas, una por color de la portada.
float3 weights(float2 g, float2 c1, float2 c2, float2 c3) {
    return float3(
        exp(-dot(g - c1, g - c1) * 3.0),
        exp(-dot(g - c2, g - c2) * 3.2),
        exp(-dot(g - c3, g - c3) * 2.8)
    );
}

half4 main(float2 p) {
    float2 uv = p / max(uSize, float2(1.0, 1.0));
    float2 g = (uv - 0.5) * float2(1.30, 1.0);
    // El reloj va acelerado de una vez: todo el conjunto respira y viaja mas deprisa.
    float t = uTime * 1.8;

    float2 c1 = float2(0.28 + 0.24 * sin(t * 0.42), 0.38 + 0.21 * cos(t * 0.35));
    float2 c2 = float2(0.74 + 0.22 * cos(t * 0.31 + 1.7), 0.64 + 0.23 * sin(t * 0.39 + 0.6));
    float2 c3 = float2(0.52 + 0.28 * sin(t * 0.25 + 3.1), 0.22 + 0.26 * cos(t * 0.47 + 2.2));

    // Plegado del espacio: el campo se ondula y los bordes se retuercen.
    g += float2(
        sin(g.y * 3.1 + t * 0.55) * 0.065 + sin(g.y * 7.3 - t * 0.31) * 0.024,
        cos(g.x * 2.7 + t * 0.48) * 0.065 + cos(g.x * 6.1 + t * 0.27) * 0.024
    );

    float3 w = weights(g, c1, c2, c3);
    float F = w.x + w.y + w.z;

    float3 colour = uC.rgb * 0.07;
    colour = mix(colour, uA.rgb, w.x);
    colour = mix(colour, uB.rgb, w.y);
    colour = mix(colour, uC.rgb, w.z * 0.92);

    // La portada manda el tono, y el vidrio lo sube de saturacion en vez de apagarlo.
    float luma = dot(colour, float3(0.299, 0.587, 0.114));
    colour = max(float3(0.0), mix(float3(luma), colour, 1.75));

    // Fílos de vidrio: dos anillos estrechos donde el campo cruza un umbral. Es el canto
    // de la lente, y es lo que convierte las manchas en cristal.
    float rim = smoothstep(0.42, 0.50, F) * (1.0 - smoothstep(0.56, 0.66, F));
    float inner = smoothstep(0.13, 0.18, F) * (1.0 - smoothstep(0.22, 0.30, F));
    colour += rim * 0.95 + inner * 0.50;

    // Reflejo direccional a partir de la normal del campo (gradiente): el borde exterior
    // se enciende de un lado y se apaga del otro.
    float e = 0.006;
    float Fa = dot(weights(g + float2(e, 0.0), c1, c2, c3), float3(1.0));
    float Fb = dot(weights(g + float2(0.0, e), c1, c2, c3), float3(1.0));
    float2 n = normalize(float2(Fa - F, Fb - F) + float2(1e-5, 1e-5));
    colour += pow(max(0.0, dot(n, normalize(float2(0.72, 0.70)))), 3.0) * 0.38;

    // Cáusticas rápidas: dos peines girados que solo queman donde se cruzan.
    float2 q = spin(g, 0.55 + 0.14 * sin(t * 0.21));
    float ca = pow(max(0.0, sin(q.x * 11.0 + t * 1.10)), 9.0);
    float cb = pow(max(0.0, sin(q.y * 9.0 - t * 0.85 + q.x * 2.5)), 9.0);
    colour += ca * cb * 0.75;

    // Reflejo que barre la pantalla.
    float sheen = pow(max(0.0, sin((uv.x * 1.15 + uv.y * 0.35 - t * 0.22) * 3.14159)), 5.0);
    colour += sheen * 0.14;

    colour *= 0.80;
    float vignette = smoothstep(1.05, 0.10, length(uv - 0.5) * 1.45);
    colour *= mix(0.42, 1.0, vignette);

    return half4(half3(clamp(colour, 0.0, 1.0)), 1.0);
}
"""

private val liquidEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(LIQUID_SKSL) }
        .onFailure { println("LIQUID_SKSL no compila: ${it.message}") }
        .getOrNull()
}

private val liquidPaint = org.jetbrains.skia.Paint().apply { isAntiAlias = true }
private val uniformBuffer = ByteBuffer.allocate(3 * 4 * 4).order(ByteOrder.LITTLE_ENDIAN)

private fun uniformData(size: FloatArray, colors: List<Color>): Data {
    uniformBuffer.clear()
    size.forEach { uniformBuffer.putFloat(it) }
    repeat(3) { index ->
        val color = colors.getOrElse(index) { colors.last() }
        uniformBuffer.putFloat(color.red)
        uniformBuffer.putFloat(color.green)
        uniformBuffer.putFloat(color.blue)
        uniformBuffer.putFloat(1f)
    }
    return Data.makeFromBytes(uniformBuffer.array())
}

private val shaderCache = object : LinkedHashMap<String, Shader?>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Shader?>?) = size > 8
}

private fun liquidShader(width: Float, height: Float, seconds: Float, colors: List<Color>): Shader? {
    val effect = liquidEffect ?: return null
    // 24 steps per second: the movement is slow enough that nobody can tell, and the
    // Skia shader is only rebuilt 24 times a second instead of 60.
    val tick = (seconds * 24f).toInt()
    val key = "${width.roundToInt()}|${height.roundToInt()}|$tick|" +
        colors.joinToString("") { it.value.toString() }
    if (shaderCache.containsKey(key)) return shaderCache[key]
    val shader = runCatching {
        effect.makeShader(uniformData(floatArrayOf(width, height, tick / 24f), colors), null, null)
    }.getOrNull()
    shaderCache[key] = shader
    return shader
}

/**
 * Paints the liquid backdrop. When the GPU cannot compile the shader the same colours are
 * laid out as a soft gradient, so the screen never falls back to a flat panel.
 */
@Composable
fun LiquidBackdrop(
    colors: List<Color>,
    prefs: Preferences,
    modifier: Modifier = Modifier
) {
    val animate = !prefs.reduceMotion
    var clock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        while (animate) {
            // A plain 24 Hz timer, not the frame clock: with nothing else animating on
            // screen the window's frame clock stopped delivering frames and the backdrop
            // froze, which is exactly what "no movement" looked like.
            delay(41)
            clock = System.currentTimeMillis()
        }
    }

    // Leer el reloj al componer (y no dentro del dibujo) es lo que garantiza que el lienzo
    // se vuelva a pintar: un estado leido solo en la fase de dibujo no siempre la reabre.
    val seconds = clock / 1000f

    Canvas(modifier.fillMaxSize()) {
        val shader = liquidShader(size.width, size.height, seconds, colors)
        if (shader != null) {
            drawIntoCanvas { canvas ->
                liquidPaint.shader = shader
                canvas.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), liquidPaint)
                liquidPaint.shader = null
            }
        } else {
            val a = colors.getOrElse(0) { Color(0xFF322332) }
            val b = colors.getOrElse(1) { Color(0xFF413041) }
            val c = colors.getOrElse(2) { Color(0xFF261B26) }
            drawRect(
                Brush.radialGradient(
                    colors = listOf(a, b, c, Color(0xFF0B0910)),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.30f),
                    radius = size.maxDimension * 0.95f
                )
            )
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.55f)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height)
                )
            )
        }
    }
}
