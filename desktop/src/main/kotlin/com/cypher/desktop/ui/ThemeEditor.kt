package com.cypher.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.desktop.core.ThemeColors

/**
 * One editable colour of the interface. The read/write pair keeps the settings list and
 * the data class in sync without a `when` per row.
 */
class ThemeSlot(
    val label: String,
    val read: (ThemeColors) -> Long,
    val write: (ThemeColors, Long) -> ThemeColors
)

private val themeSlots = listOf(
    ThemeSlot("Fondo", { it.background }, { c, v -> c.copy(background = v) }),
    ThemeSlot("Panel", { it.surface }, { c, v -> c.copy(surface = v) }),
    ThemeSlot("Tarjeta", { it.card }, { c, v -> c.copy(card = v) }),
    ThemeSlot("Píldora", { it.pill }, { c, v -> c.copy(pill = v) }),
    ThemeSlot("Borde", { it.border }, { c, v -> c.copy(border = v) }),
    ThemeSlot("Separador", { it.separator }, { c, v -> c.copy(separator = v) }),
    ThemeSlot("Texto principal", { it.textPrimary }, { c, v -> c.copy(textPrimary = v) }),
    ThemeSlot("Texto secundario", { it.textSecondary }, { c, v -> c.copy(textSecondary = v) }),
    ThemeSlot("Texto terciario", { it.textTertiary }, { c, v -> c.copy(textTertiary = v) })
)

/** "COLORES": every surface and text level, plus the way back to the reference palette. */
@Composable
fun ThemeColorsCard(
    colors: ThemeColors,
    accent: Color,
    onChange: (ThemeColors) -> Unit,
    onReset: () -> Unit
) {
    var editing by remember { mutableStateOf<ThemeSlot?>(null) }

    SettingsCard {
        Text("Color de la interfaz", color = CypherColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cada superficie y cada nivel de texto se puede cambiar por separado, igual que en Android.",
            color = CypherColors.TextTertiary, fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        themeSlots.forEach { slot ->
            val value = Color(slot.read(colors))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { editing = slot }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(18.dp)
                        .background(value, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(slot.label, color = CypherColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(hexOf(value), color = CypherColors.TextTertiary, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f))
                .clickable(onClick = onReset)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Restablecer paleta", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "El acento se elige arriba, en «Acento»: tiñe botones, pestañas y la letra de la canción.",
            color = CypherColors.TextTertiary, fontSize = 10.sp
        )
    }

    editing?.let { slot ->
        ColorPickerDialog(
            title = slot.label,
            initial = Color(slot.read(colors)),
            accent = accent,
            onDismiss = { editing = null },
            onPick = { picked ->
                onChange(slot.write(colors, picked.toArgb().toLong() and 0xFFFFFFFFL))
                editing = null
            }
        )
    }
}

/** Conic swatch that opens the same picker, so the accent is not limited to presets. */
@Composable
fun AccentPickerSwatch(accentValue: Long, accent: Color, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(30.dp)
            .background(
                Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF4D4D), Color(0xFFFFD24D), Color(0xFF4DFF88),
                        Color(0xFF4DD2FF), Color(0xFF7A5CFF), Color(0xFFFF4DD2), Color(0xFFFF4D4D)
                    )
                ),
                CircleShape
            )
            .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .clickable { open = true }
    )
    if (open) {
        ColorPickerDialog(
            title = "Acento",
            initial = Color(accentValue),
            accent = accent,
            onDismiss = { open = false },
            onPick = { picked ->
                onPick(picked.toArgb().toLong() and 0xFFFFFFFFL)
                open = false
            }
        )
    }
}

/**
 * Hue bar plus a saturation/brightness square: the same gesture as the phone's picker,
 * with the hex code for anyone who already knows the colour they want.
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Color,
    accent: Color,
    onDismiss: () -> Unit,
    onPick: (Color) -> Unit
) {
    val start = remember(initial) { hsvOf(initial) }
    var hue by remember { mutableStateOf(start[0]) }
    var saturation by remember { mutableStateOf(start[1]) }
    var brightness by remember { mutableStateOf(start[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    val current = Color.hsv(hue, saturation, brightness)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(352.dp)
                .background(CypherColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, CypherColors.Border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(title, color = CypherColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            // Saturation (x) and brightness (y).
            Canvas(
                Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed) {
                                    saturation = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    brightness = (1f - change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    hex = hexOf(Color.hsv(hue, saturation, brightness))
                                    change.consume()
                                }
                            }
                        }
                    }
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val thumb = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawCircle(Color.White, radius = 7.dp.toPx(), center = thumb, style = Stroke(width = 2.dp.toPx()))
            }

            Spacer(Modifier.height(12.dp))

            // Hue.
            Canvas(
                Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed) {
                                    hue = ((change.position.x / size.width.toFloat()) * 360f).coerceIn(0f, 360f)
                                    hex = hexOf(Color.hsv(hue, saturation, brightness))
                                    change.consume()
                                }
                            }
                        }
                    }
            ) {
                drawRect(
                    Brush.horizontalGradient(
                        (0..6).map { Color.hsv(it * 60f, 1f, 1f) }
                    )
                )
                val x = (hue / 360f) * size.width
                drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp)
                        .background(current, RoundedCornerShape(9.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(9.dp))
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { typed ->
                        hex = typed
                        val parsed = parseHex(typed)
                        if (parsed != null) {
                            val hsv = hsvOf(parsed)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CypherColors.TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = CypherColors.Border,
                        focusedContainerColor = CypherColors.Pill.copy(alpha = 0.35f),
                        unfocusedContainerColor = CypherColors.Pill.copy(alpha = 0.35f),
                        cursorColor = accent
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = CypherColors.TextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { onPick(Color.hsv(hue, saturation, brightness)) }) {
                    Text("Aplicar", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun hexOf(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)

private fun parseHex(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    val value = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

/** Red/green/blue to hue (0..360), saturation and brightness (0..1). */
private fun hsvOf(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    var hue = 0f
    if (delta > 0.0001f) {
        hue = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
        if (hue < 0f) hue += 360f
    }
    return floatArrayOf(hue.coerceIn(0f, 360f), if (max <= 0f) 0f else delta / max, max)
}
