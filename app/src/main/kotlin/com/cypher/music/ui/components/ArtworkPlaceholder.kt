package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium Apple Music Artwork Placeholder.
 * Replaces plain gray boxes with a subtle dark gradient and a centered
 * faint-gray music note vector icon (0x40FFFFFF).
 */
@Composable
fun ArtworkPlaceholder(
    modifier: Modifier = Modifier,
    iconSize: Dp? = null
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2C2C30),
                    Color(0xFF18181B)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        val iconModifier = if (iconSize != null) {
            Modifier.size(iconSize)
        } else {
            Modifier.fillMaxSize(0.48f)
        }

        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color(0x40FFFFFF),
            modifier = iconModifier
        )
    }
}
