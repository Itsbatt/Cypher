package com.cypher.music.data

import com.cypher.music.model.Track
import com.cypher.music.network.SpotifyPlaylistRef

/** State of the "import from Spotify" sheet, from the pasted link to the resolved preview. */
data class SpotifyImportUiState(
    val isOpen: Boolean = false,
    val link: String = "",
    val isBusy: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val playlist: SpotifyPlaylistRef? = null,
    val tracks: List<Track> = emptyList(),
    val missed: Int = 0
) {
    val hasPreview: Boolean get() = playlist != null && tracks.isNotEmpty()
}
