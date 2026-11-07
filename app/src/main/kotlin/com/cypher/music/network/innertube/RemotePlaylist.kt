package com.cypher.music.network.innertube

/** Playlist metadata discovered in the YouTube Music library. */
data class RemotePlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val coverUrl: String? = null
)
