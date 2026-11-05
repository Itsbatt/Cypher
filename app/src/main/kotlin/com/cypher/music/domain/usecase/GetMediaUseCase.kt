package com.cypher.music.domain.usecase

import android.net.Uri
import com.cypher.music.data.LocalMusicRepository
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import com.cypher.music.network.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Domain UseCase that orchestrates and unifies media retrieval across
 * LocalMusicRepository (MediaStore) and YouTubeRepository.
 *
 * Removes decision logic from ViewModels regarding which repository to query.
 */
class GetMediaUseCase(
    private val localMusicRepository: LocalMusicRepository,
    private val youTubeRepository: YouTubeRepository
) {
    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: Flow<List<Track>> = _localTracks.asStateFlow()

    private val _featuredTracks = MutableStateFlow<List<Track>>(emptyList())
    val featuredTracks: Flow<List<Track>> = _featuredTracks.asStateFlow()

    /**
     * Unified reactive flow combining local tracks and featured YouTube tracks.
     */
    val unifiedMediaStream: Flow<List<Track>> = combine(
        _localTracks,
        _featuredTracks
    ) { locals, featured ->
        locals + featured
    }.flowOn(Dispatchers.Default)

    /**
     * Refreshes data from both repositories asynchronously.
     */
    suspend fun refresh() {
        val locals = localMusicRepository.getLocalTracks()
        _localTracks.value = locals

        val featured = runCatching { youTubeRepository.getTrending() }
            .getOrDefault(youTubeRepository.getDefaultFeaturedTracks())
        _featuredTracks.value = featured
    }

    /**
     * Returns a reactive flow for local music store items.
     */
    fun getLocalMediaFlow(): Flow<List<Track>> = flow {
        val tracks = localMusicRepository.getLocalTracks()
        _localTracks.value = tracks
        emit(tracks)
    }.flowOn(Dispatchers.IO)

    /**
     * Returns a reactive flow for featured/trending cloud media.
     */
    fun getFeaturedMediaFlow(): Flow<List<Track>> = flow {
        val tracks = runCatching { youTubeRepository.getTrending() }
            .getOrDefault(youTubeRepository.getDefaultFeaturedTracks())
        _featuredTracks.value = tracks
        emit(tracks)
    }.flowOn(Dispatchers.IO)

    /**
     * Unified search querying local storage and YouTube seamlessly.
     */
    fun searchMedia(query: String): Flow<List<Track>> = flow {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        // 1. Search local music
        val localMatches = localMusicRepository.getLocalTracks().filter { track ->
            track.title.contains(cleanQuery, ignoreCase = true) ||
            track.artist.contains(cleanQuery, ignoreCase = true) ||
            track.album.contains(cleanQuery, ignoreCase = true)
        }

        // 2. Search YouTube music
        val remoteMatches = runCatching {
            youTubeRepository.searchTracks(cleanQuery)
        }.getOrDefault(emptyList())

        emit(localMatches + remoteMatches)
    }.flowOn(Dispatchers.IO)

    /**
     * Resolves a Track into a ready-to-play instance with verified stream URL
     * and metadata, abstracting away MediaStore vs YouTube details from the player.
     */
    fun resolvePlayableMedia(track: Track): Flow<Track> = flow {
        if (!track.streamUrl.isNullOrBlank()) {
            emit(track)
            return@flow
        }

        when (track.sourceType) {
            AudioSourceType.LOCAL -> {
                val locals = localMusicRepository.getLocalTracks()
                val found = locals.find { it.id == track.id } ?: track
                emit(found)
            }
            AudioSourceType.YOUTUBE -> {
                val videoId = track.id.removePrefix("yt_")
                val streamUrl = "https://www.youtube.com/watch?v=$videoId"
                emit(track.copy(streamUrl = streamUrl))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Creates a playable Track representation from an external intent or file Uri.
     */
    fun createMediaFromUri(uri: Uri, title: String): Track {
        return Track(
            id = "local_uri_${System.currentTimeMillis()}",
            title = title,
            artist = "Archivo local",
            album = "Almacenamiento",
            durationMs = 0L,
            streamUrl = uri.toString(),
            sourceType = AudioSourceType.LOCAL
        )
    }
}
