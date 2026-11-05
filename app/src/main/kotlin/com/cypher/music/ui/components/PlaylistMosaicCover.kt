package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PlaylistMosaicCover(
    artworks: List<String>,
    customCoverUri: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    cornerRadius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        // Base placeholder with fixed size and neutral gradient + music note icon
        ArtworkPlaceholder(modifier = Modifier.fillMaxSize(), iconSize = size * 0.45f)

        if (!customCoverUri.isNullOrBlank()) {
            AsyncImage(
                model = customCoverUri,
                contentDescription = "Carátula de playlist",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (artworks.isEmpty()) {
            // Already showing ArtworkPlaceholder underneath
        } else if (artworks.size < 4) {
            // Single dominant artwork
            AsyncImage(
                model = artworks.first(),
                contentDescription = "Carátula de playlist",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 2x2 Mosaic
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = artworks[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                    AsyncImage(
                        model = artworks[1],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
                Row(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = artworks[2],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                    AsyncImage(
                        model = artworks[3],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }
        }
    }
}
