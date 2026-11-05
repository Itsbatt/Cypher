package com.cypher.music.data

import androidx.compose.ui.graphics.Color

/**
 * Extensible data model representing a browseable search category in Cypher.
 */
data class SearchCategory(
    val id: String,
    val title: String,
    val gradientColors: List<Color>,
    val coverUrl: String? = null,
    val iconRes: Int? = null,
    val customBorderColor: Color? = null
) {
    companion object {
        fun defaultCategories(): List<SearchCategory> = listOf(
            SearchCategory(
                id = "pop",
                title = "Pop",
                gradientColors = listOf(Color(0xFFFD297B), Color(0xFFFF5864)),
                coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "hiphop",
                title = "Hip-Hop / Urbano",
                gradientColors = listOf(Color(0xFFFF655B), Color(0xFFFF5864)),
                coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "rock",
                title = "Rock Clásico",
                gradientColors = listOf(Color(0xFF4776E6), Color(0xFF8E54E9)),
                coverUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "electronic",
                title = "Electrónica / Dance",
                gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "rnb",
                title = "R&B / Soul",
                gradientColors = listOf(Color(0xFF8A2387), Color(0xFFE94057)),
                coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "latin",
                title = "Latino / Éxitos",
                gradientColors = listOf(Color(0xFFFF8008), Color(0xFFFFC837)),
                coverUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "chill",
                title = "Chill & Relax",
                gradientColors = listOf(Color(0xFF2193B0), Color(0xFF6DD5ED)),
                coverUrl = "https://images.unsplash.com/photo-1445985543469-221e57446377?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "indie",
                title = "Indie & Alternativo",
                gradientColors = listOf(Color(0xFF0F2027), Color(0xFF2C5364)),
                coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=300&auto=format&fit=crop&q=80"
            )
        )
    }
}
