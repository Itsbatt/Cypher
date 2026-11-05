package com.cypher.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.ThemePreferences
import com.cypher.music.ui.theme.IosDarkBorder
import com.cypher.music.ui.theme.IosDarkSurface
import com.cypher.music.ui.theme.IosDarkTextSecondary
import com.cypher.music.ui.utils.AppHaptics

enum class CypherTab(val title: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector) {
    LISTEN_NOW("Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    LIBRARY("Biblioteca", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    SEARCH("Buscar", Icons.Filled.Search, Icons.Outlined.Search)
}

typealias AppleTab = CypherTab

@Composable
fun CypherNavBar(
    currentTab: CypherTab,
    onTabSelected: (CypherTab) -> Unit,
    themePrefs: ThemePreferences = ThemePreferences(),
    accentColor: Color = Color(0xFFFA243C),
    modifier: Modifier = Modifier
) {
    val navBarShape = RoundedCornerShape(32.dp)
    val mainTabs = remember {
        listOf(
            CypherTab.LISTEN_NOW,
            CypherTab.LIBRARY,
            CypherTab.SEARCH
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp)
            .shadow(
                elevation = 8.dp,
                shape = navBarShape,
                clip = false
            )
            .clip(navBarShape)
            .background(IosDarkSurface)
            .border(
                width = 1.dp,
                color = IosDarkBorder,
                shape = navBarShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            mainTabs.forEach { tab ->
                val isSelected = currentTab == tab
                val color = if (isSelected) accentColor else IosDarkTextSecondary
                val icon = if (isSelected) tab.activeIcon else tab.inactiveIcon

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "TabIconScale"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) {
                                AppHaptics.tabClick(themePrefs.hapticsEnabled)
                                onTabSelected(tab)
                            }
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.title,
                        tint = color,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(iconScale)
                    )
                    Text(
                        text = tab.title,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
