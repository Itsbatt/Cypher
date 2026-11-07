package com.cypher.music.data

import com.cypher.music.network.SpotifyPlaylistSummary

data class SpotifyFailedTrack(
    val playlist: String,
    val title: String,
    val artist: String
)

data class SpotifyTransferSummary(
    val playlists: Int = 0,
    val importedTracks: Int = 0,
    val failed: List<SpotifyFailedTrack> = emptyList()
) {
    val failedCount: Int get() = failed.size

    /** Plain text for the clipboard and for sharing, one song per line. */
    fun exportText(): String = buildString {
        appendLine("Cypher · canciones de Spotify no encontradas en YouTube Music")
        appendLine("Transferidas: $importedTracks canciones en $playlists playlists")
        appendLine("No encontradas: ${failed.size}")
        if (failed.isNotEmpty()) {
            appendLine()
            failed.forEach { appendLine("• ${it.title} — ${it.artist}  [${it.playlist}]") }
        }
    }
}

data class SpotifyTransferUiState(
    val isOpen: Boolean = false,
    val isLoadingPlaylists: Boolean = false,
    val playlists: List<SpotifyPlaylistSummary> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val status: String = "",
    val currentPlaylist: String? = null,
    val tracksDone: Int = 0,
    val tracksTotal: Int = 0,
    val playlistsDone: Int = 0,
    val playlistsTotal: Int = 0,
    val summary: SpotifyTransferSummary? = null,
    /** Files skipped while reading a ZIP, each one with its reason. */
    val importIssues: List<String> = emptyList(),
    val error: String? = null
) {
    val selectedCount: Int get() = selectedIds.size

    val progress: Float
        get() {
            if (playlistsTotal == 0) return 0f
            val current = if (tracksTotal > 0) {
                tracksDone.toFloat() / tracksTotal
            } else {
                0f
            }
            return ((playlistsDone + current) / playlistsTotal).coerceIn(0f, 1f)
        }
}
