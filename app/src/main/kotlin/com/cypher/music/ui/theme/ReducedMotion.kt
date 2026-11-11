package com.cypher.music.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the user (or the system accessibility setting) asked for reduced motion.
 * The ambient AGSL shaders read this and draw a static gradient instead.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }
