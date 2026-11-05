package com.cypher.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkTextPrimary
import com.cypher.music.ui.theme.IosDarkTextSecondary

@Composable
fun CypherMediaCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    cardSize: Dp = 150.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(cardSize)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(cardSize)
                .shadow(12.dp, shape = RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(12.dp))
                .background(IosDarkCard)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            color = IosDarkTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            color = IosDarkTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
