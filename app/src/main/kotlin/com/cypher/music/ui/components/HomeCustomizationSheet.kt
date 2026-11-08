package com.cypher.music.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.HomePreferences
import com.cypher.music.data.HomeSection
import com.cypher.music.data.Playlist
import com.cypher.music.ui.theme.ResolvedThemeColors

/**
 * Lets the user choose which home blocks exist, in which order, and which playlists are
 * featured. Reordering uses explicit arrows instead of a drag: it is unambiguous, works
 * with accessibility services and stays responsive on the slow tablet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCustomizationSheet(
    preferences: HomePreferences,
    playlists: List<Playlist>,
    favoritePlaylistIds: Set<String>,
    themeColors: ResolvedThemeColors,
    onMove: (Int, Int) -> Unit,
    onToggle: (HomeSection, Boolean) -> Unit,
    onReset: () -> Unit,
    onToggleFavoritePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF21C1C1E),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pantalla de inicio",
                            color = themeColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Elige qué ves y en qué orden",
                            color = themeColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onReset) {
                        Text("Restablecer", color = themeColors.accent, fontSize = 13.sp)
                    }
                }
            }

            items(preferences.sections.size) { index ->
                val entry = preferences.sections[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.section.title,
                            color = themeColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = entry.section.description,
                            color = themeColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { if (index > 0) onMove(index, index - 1) },
                        enabled = index > 0
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Subir ${entry.section.title}",
                            tint = themeColors.textSecondary.copy(alpha = if (index > 0) 0.9f else 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { if (index < preferences.sections.lastIndex) onMove(index, index + 1) },
                        enabled = index < preferences.sections.lastIndex
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Bajar ${entry.section.title}",
                            tint = themeColors.textSecondary.copy(
                                alpha = if (index < preferences.sections.lastIndex) 0.9f else 0.3f
                            )
                        )
                    }
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { onToggle(entry.section, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = themeColors.accent,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Playlists favoritas",
                    color = themeColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Marca las que quieras ver en la sección de favoritas.",
                    color = themeColors.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (playlists.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no tienes playlists.",
                        color = themeColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    val isFavorite = favoritePlaylistIds.contains(playlist.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleFavoritePlaylist(playlist.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null,
                                tint = if (isFavorite) themeColors.accent else themeColors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = playlist.name,
                            color = themeColors.textPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = playlist.formattedTrackCount,
                            color = themeColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
