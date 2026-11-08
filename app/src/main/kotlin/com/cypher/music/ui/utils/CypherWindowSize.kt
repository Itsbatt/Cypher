package com.cypher.music.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material-style width buckets resolved from the current configuration. The app
 * was designed phone-first, so on a 1200x1920 tablet the same single column used
 * to stretch across the whole panel. Screens use these buckets to centre and cap
 * their content, and to ask for more columns when there is room.
 */
enum class CypherWidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberCypherWidthClass(): CypherWidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> CypherWidthClass.EXPANDED
        widthDp >= 600 -> CypherWidthClass.MEDIUM
        else -> CypherWidthClass.COMPACT
    }
}

/** Widest a single column of content should get before it starts to stretch. */
@Composable
fun cypherContentMaxWidth(): Dp = when (rememberCypherWidthClass()) {
    CypherWidthClass.COMPACT -> Dp.Infinity
    CypherWidthClass.MEDIUM -> 760.dp
    CypherWidthClass.EXPANDED -> 900.dp
}
