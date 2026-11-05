package com.cypher.music.data

/**
 * Color source mode for the Liquid Glass AGSL shader.
 */
enum class ShaderColorMode(val label: String) {
    DYNAMIC("Dinámico (Carátula)"),
    CUSTOM("Personalizado (Color Picker)")
}

/**
 * Real-time customizable configuration for the AGSL Liquid Glass shader.
 */
data class LiquidGlassConfig(
    val colorMode: ShaderColorMode = ShaderColorMode.DYNAMIC,
    val customColor1: Long = 0xFFFA243C, // Vibrant Red / Apple Accent
    val customColor2: Long = 0xFF651FFF, // Deep Violet / Indigo
    val customColor3: Long = 0xFF00E5FF, // Cyan Electric
    val animationSpeed: Float = 0.60f,   // [0.10f .. 2.00f]
    val distortionScale: Float = 0.70f,  // [0.00f .. 2.00f]
    val overallOpacity: Float = 0.65f,   // [0.05f .. 1.00f]
    val isEnabled: Boolean = true
)
