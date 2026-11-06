package com.cypher.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cypher.music.data.HsvColor
import kotlin.math.roundToInt

/**
 * Visual colour picker built with Canvas: a saturation/value square, a hue bar
 * and an alpha bar. Tapping or dragging updates [onHsvChange] continuously.
 */
@Composable
fun HsvColorPicker(
    hsv: HsvColor,
    alpha: Int,
    onHsvChange: (HsvColor) -> Unit,
    onAlphaChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pureHue = Color.hsv(hsv.hue.coerceIn(0f, 360f), 1f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Saturation (x) / value (y)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Saturación y brillo" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onHsvChange(positionToHsv(down.position, size.width, size.height, hsv.hue))
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { change ->
                                onHsvChange(positionToHsv(change.position, size.width, size.height, hsv.hue))
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val markerX = hsv.saturation.coerceIn(0f, 1f) * size.width
            val markerY = (1f - hsv.value.coerceIn(0f, 1f)) * size.height
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(markerX, markerY),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Hue
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .semantics { contentDescription = "Matiz" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onHsvChange(hsv.copy(hue = (down.position.x / size.width).coerceIn(0f, 1f) * 360f).let {
                            HsvColor(it.hue, hsv.saturation, hsv.value)
                        })
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { change ->
                                val hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                                onHsvChange(HsvColor(hue, hsv.saturation, hsv.value))
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                        Color.Blue, Color.Magenta, Color.Red
                    )
                )
            )
            val x = (hsv.hue.coerceIn(0f, 360f) / 360f) * size.width
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(
                Color.Black.copy(alpha = 0.35f),
                radius = 10.dp.toPx(),
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Alpha
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .semantics { contentDescription = "Opacidad" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onAlphaChange(((down.position.x / size.width).coerceIn(0f, 1f) * 255f).roundToInt())
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { change ->
                                onAlphaChange(((change.position.x / size.width).coerceIn(0f, 1f) * 255f).roundToInt())
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val solid = Color(hsv.toArgb(alpha = 255))
            drawRect(Brush.horizontalGradient(listOf(solid.copy(alpha = 0f), solid)))
            val x = (alpha.coerceIn(0, 255) / 255f) * size.width
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(
                Color.Black.copy(alpha = 0.35f),
                radius = 10.dp.toPx(),
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

private fun positionToHsv(position: Offset, width: Int, height: Int, hue: Float): HsvColor {
    val saturation = (position.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
    val value = (1f - position.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
    return HsvColor(hue, saturation, value)
}
