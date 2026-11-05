package com.cypher.music.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import com.cypher.music.network.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val tracks: List<Track>
)

class MusicRepository(
    private val context: Context,
    val youTubeRepo: YouTubeRepository = YouTubeRepository(),
    val localRepo: LocalMusicRepository = LocalMusicRepository(context)
) {
    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    private val _featuredTracks = MutableStateFlow<List<Track>>(emptyList())
    val featuredTracks: StateFlow<List<Track>> = _featuredTracks.asStateFlow()

    suspend fun refreshData() {
        val locals = localRepo.getLocalTracks()
        _localTracks.value = locals

        val trendingYt = youTubeRepo.getTrending()
        _featuredTracks.value = trendingYt
    }

    fun getEditorialPlaylists(): List<MediaCollection> {
        return listOf(
            MediaCollection(
                id = "playlist_spatial",
                title = "Audio Espacial",
                subtitle = "Sumérgete en la música con Dolby Atmos",
                artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
                tracks = youTubeRepo.getDefaultFeaturedTracks()
            ),
            MediaCollection(
                id = "playlist_hits",
                title = "Éxitos de Hoy",
                subtitle = "Las canciones más escuchadas del momento",
                artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
                tracks = youTubeRepo.getDefaultFeaturedTracks().reversed()
            ),
            MediaCollection(
                id = "playlist_chill",
                title = "Relax & Desconexión",
                subtitle = "Vibra acústica y ambient para desconectar",
                artworkUrl = "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=600&auto=format&fit=crop&q=80",
                tracks = youTubeRepo.getDefaultFeaturedTracks().take(4)
            ),
            MediaCollection(
                id = "playlist_lossless",
                title = "Colección Lossless ALAC",
                subtitle = "Archivos locales sin pérdida de fidelidad",
                artworkUrl = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=600&auto=format&fit=crop&q=80",
                tracks = _localTracks.value
            )
        )
    }

    fun getBrowseCategories(): List<SearchCategory> {
        return listOf(
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
                id = "chill",
                title = "Chill & Lo-Fi",
                gradientColors = listOf(Color(0xFFF3904F), Color(0xFF3B4371)),
                coverUrl = "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=300&auto=format&fit=crop&q=80"
            ),
            SearchCategory(
                id = "classical",
                title = "Música Clásica",
                gradientColors = listOf(Color(0xFF654EA3), Color(0xFFEAAFC8)),
                coverUrl = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=300&auto=format&fit=crop&q=80"
            )
        )
    }
}
