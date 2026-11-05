@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cypher.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.LiquidGlassConfig
import com.cypher.music.data.ShaderColorMode
import com.cypher.music.ui.theme.ResolvedThemeColors
import java.util.Locale

@Composable
fun LiquidGlassSettingsSheet(
    config: LiquidGlassConfig,
    themeColors: ResolvedThemeColors,
    onConfigChange: (LiquidGlassConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF21C1C1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title & Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Shader Liquid Glass",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Efecto AGSL de fluido y refracción acústica",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }

                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { onConfigChange(config.copy(isEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = themeColors.accent
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Selector: Dynamic vs Custom
            Text(
                text = "MODO DE PALETA CROMÁTICA",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isDynamic = config.colorMode == ShaderColorMode.DYNAMIC

                // Dynamic Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDynamic) themeColors.accent else Color.Transparent)
                        .clickable {
                            onConfigChange(config.copy(colorMode = ShaderColorMode.DYNAMIC))
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dinámico",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isDynamic) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Custom Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isDynamic) themeColors.accent else Color.Transparent)
                        .clickable {
                            onConfigChange(config.copy(colorMode = ShaderColorMode.CUSTOM))
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ColorLens,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Personalizado",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (!isDynamic) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Visual Color Picker Section
            AnimatedVisibility(visible = config.colorMode == ShaderColorMode.CUSTOM) {
                LiquidGlassColorPicker(
                    config = config,
                    accentColor = themeColors.accent,
                    onConfigChange = onConfigChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Advanced Controls Section (Sliders)
            Text(
                text = "CONTROLES AVANZADOS",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Animation Speed Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Velocidad de Animación",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.2fx", config.animationSpeed),
                    color = themeColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = config.animationSpeed,
                onValueChange = { onConfigChange(config.copy(animationSpeed = it)) },
                valueRange = 0.1f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = themeColors.accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Distortion Scale Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Waves,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nivel de Distorsión (Escala)",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.2f", config.distortionScale),
                    color = themeColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = config.distortionScale,
                onValueChange = { onConfigChange(config.copy(distortionScale = it)) },
                valueRange = 0.0f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = themeColors.accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Overall Opacity Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Opacidad General",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = "${(config.overallOpacity * 100).toInt()}%",
                    color = themeColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = config.overallOpacity,
                onValueChange = { onConfigChange(config.copy(overallOpacity = it)) },
                valueRange = 0.05f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = themeColors.accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
private fun LiquidGlassColorPicker(
    config: LiquidGlassConfig,
    accentColor: Color,
    onConfigChange: (LiquidGlassConfig) -> Unit
) {
    var selectedSlot by remember { mutableStateOf(0) } // 0: Dominante, 1: Secundario, 2: Acento

    val currentColorLong = when (selectedSlot) {
        0 -> config.customColor1
        1 -> config.customColor2
        else -> config.customColor3
    }

    var hsv by remember(selectedSlot, currentColorLong) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV((currentColorLong and 0xFFFFFFFFL).toInt(), array)
        mutableStateOf(Triple(array[0], array[1], array[2]))
    }

    fun applyColor(colorLong: Long) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV((colorLong and 0xFFFFFFFFL).toInt(), array)
        hsv = Triple(array[0], array[1], array[2])
        val updated = when (selectedSlot) {
            0 -> config.copy(customColor1 = colorLong)
            1 -> config.copy(customColor2 = colorLong)
            else -> config.copy(customColor3 = colorLong)
        }
        onConfigChange(updated)
    }

    fun applyHsv(h: Float, s: Float, v: Float) {
        hsv = Triple(h, s, v)
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        val colorLong = colorInt.toLong() and 0xFFFFFFFFL
        val updated = when (selectedSlot) {
            0 -> config.copy(customColor1 = colorLong)
            1 -> config.copy(customColor2 = colorLong)
            else -> config.copy(customColor3 = colorLong)
        }
        onConfigChange(updated)
    }

    val presetColors = remember {
        listOf(
            0xFFFA243CL, // Crimson / Apple Red
            0xFFFF3B30L, // Coral
            0xFFFF9500L, // Sunset Orange
            0xFFFFCC00L, // Electric Yellow
            0xFF34C759L, // Vivid Green
            0xFF00E5FFL, // Electric Cyan
            0xFF007AFFL, // Sky Azure
            0xFF5856D6L, // Royal Indigo
            0xFFAF52DEL, // Magenta Purple
            0xFFFF2D55L, // Neon Pink
            0xFFE040FBL, // Orchid Violet
            0xFF64D2FFL, // Ice Blue
            0xFFFFFFFFL, // Liquid White
            0xFF1C1C1EL  // Dark Graphite
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text(
            text = "SELECTOR DE COLOR (COLOR PICKER)",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Fusion Gradient Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(config.customColor1),
                            Color(config.customColor2),
                            Color(config.customColor3)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Mezcla de Fluidos en Tiempo Real",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Slot Tabs: Dominante, Secundario, Acento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorSlotTab(
                modifier = Modifier.weight(1f),
                label = "Dominante",
                color = Color(config.customColor1),
                isSelected = selectedSlot == 0,
                accentColor = accentColor,
                onClick = { selectedSlot = 0 }
            )
            ColorSlotTab(
                modifier = Modifier.weight(1f),
                label = "Secundario",
                color = Color(config.customColor2),
                isSelected = selectedSlot == 1,
                accentColor = accentColor,
                onClick = { selectedSlot = 1 }
            )
            ColorSlotTab(
                modifier = Modifier.weight(1f),
                label = "Acento",
                color = Color(config.customColor3),
                isSelected = selectedSlot == 2,
                accentColor = accentColor,
                onClick = { selectedSlot = 2 }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Preset Palette
        Text(
            text = "PALETA RÁPIDA",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presetColors.forEach { presetColorLong ->
                val isSelectedColor = (currentColorLong and 0xFFFFFFL) == (presetColorLong and 0xFFFFFFL)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(presetColorLong))
                        .border(
                            width = if (isSelectedColor) 2.5.dp else 1.dp,
                            color = if (isSelectedColor) Color.White else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable { applyColor(presetColorLong) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // HSV Sliders Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(12.dp)
        ) {
            // 1. Hue Slider (0 - 360)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tono (Hue)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${hsv.first.toInt()}°",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val hueGradient = remember {
                Brush.horizontalGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(hueGradient)
                )
                Slider(
                    value = hsv.first,
                    onValueChange = { applyHsv(it, hsv.second, hsv.third) },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Saturation Slider (0 - 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saturación",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(hsv.second * 100).toInt()}%",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val satGradient = remember(hsv.first, hsv.third) {
                val startColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv.first, 0f, hsv.third)))
                val endColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv.first, 1f, hsv.third)))
                Brush.horizontalGradient(listOf(startColor, endColor))
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(satGradient)
                )
                Slider(
                    value = hsv.second,
                    onValueChange = { applyHsv(hsv.first, it, hsv.third) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Brightness (Value) Slider (0 - 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brillo (Luminosidad)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(hsv.third * 100).toInt()}%",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val valGradient = remember(hsv.first, hsv.second) {
                val endColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv.first, hsv.second, 1f)))
                Brush.horizontalGradient(listOf(Color.Black, endColor))
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(valGradient)
                )
                Slider(
                    value = hsv.third,
                    onValueChange = { applyHsv(hsv.first, hsv.second, it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
private fun ColorSlotTab(
    modifier: Modifier = Modifier,
    label: String,
    color: Color,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
