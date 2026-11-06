package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.HexColor
import com.cypher.music.data.HsvColor

/**
 * Free-form colour picker: tap the swatch to choose a colour visually (HSV
 * square, hue and alpha) or type any hex value. Everything is stored in the
 * existing theme preferences; no new dependency or browser storage.
 */
@Composable
fun HexColorPickerDialog(
    title: String,
    initialColor: Long,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var text by remember(initialColor) { mutableStateOf(HexColor.toHex(initialColor)) }
    var hsv by remember(initialColor) { mutableStateOf(HsvColor.fromArgb(initialColor)) }
    var alpha by remember(initialColor) { mutableIntStateOf(((initialColor ushr 24) and 0xFF).toInt()) }
    var showVisualPicker by remember { mutableStateOf(false) }
    val parsed = HexColor.parse(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(parsed ?: initialColor))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .semantics { contentDescription = "Elegir color visualmente" }
                            .clickable { showVisualPicker = !showVisualPicker }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { newText ->
                            text = newText
                            HexColor.parse(newText)?.let { argb ->
                                hsv = HsvColor.fromArgb(argb)
                                alpha = ((argb ushr 24) and 0xFF).toInt()
                            }
                        },
                        singleLine = true,
                        label = { Text("Hex") },
                        isError = parsed == null,
                        supportingText = {
                            Text(
                                text = if (parsed == null) {
                                    "#RRGGBB o #AARRGGBB"
                                } else if (showVisualPicker) {
                                    "Ajusta el color tocando el cuadro"
                                } else {
                                    "Toca el cuadro para elegir el color"
                                },
                                fontSize = 11.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            cursorColor = accentColor
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (showVisualPicker) {
                    HsvColorPicker(
                        hsv = hsv,
                        alpha = alpha,
                        onHsvChange = { updated ->
                            hsv = updated
                            text = HexColor.toHex(updated.toArgb(alpha))
                        },
                        onAlphaChange = { updatedAlpha ->
                            alpha = updatedAlpha
                            text = HexColor.toHex(hsv.toArgb(updatedAlpha))
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(
                    text = "Aplicar",
                    color = if (parsed != null) accentColor else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
