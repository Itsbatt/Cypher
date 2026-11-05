package com.cypher.music.data

import com.cypher.music.model.Track

data class RecommendationSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track> = emptyList()
)
