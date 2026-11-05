package com.cypher.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.ui.theme.AppleMusicRed
import com.cypher.music.ui.theme.IosDarkSeparator
import com.cypher.music.ui.theme.IosDarkTextPrimary
import com.cypher.music.ui.theme.IosDarkTextSecondary

@Composable
fun CypherSectionHeader(
    title: String,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (showDivider) {
            Divider(color = IosDarkSeparator, thickness = 0.5.dp, modifier = Modifier.padding(bottom = 12.dp))
        }

        if (subtitle != null) {
            Text(
                text = subtitle.uppercase(),
                color = IosDarkTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = IosDarkTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            if (onSeeAllClick != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSeeAllClick() }
                ) {
                    Text(
                        text = "Ver todo",
                        color = AppleMusicRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Ver todo",
                        tint = AppleMusicRed,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}
