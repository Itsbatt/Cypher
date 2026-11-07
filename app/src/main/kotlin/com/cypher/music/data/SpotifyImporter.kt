package com.cypher.music.data

import android.util.Log
import com.cypher.music.model.Track
import com.cypher.music.network.SpotifyPlaylistRef
import com.cypher.music.network.SpotifyTrackRef
import com.cypher.music.network.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns Spotify track metadata into playable Cypher tracks by searching each song on
 * YouTube and keeping only convincing matches. Spotify's own audio is not used: Cypher
 * plays everything through YouTube, so a bad match would be worse than a missing song.
 */
class SpotifyImporter(private val youTubeRepository: YouTubeRepository) {

    data class Resolution(
        val tracks: List<Track>,
        val failed: List<SpotifyTrackRef>,
        val total: Int
    ) {
        val missed: Int get() = failed.size
    }

    suspend fun resolve(
        playlist: SpotifyPlaylistRef,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Resolution = withContext(Dispatchers.IO) {
        val total = playlist.tracks.size
        if (total == 0) return@withContext Resolution(emptyList(), emptyList(), 0)

        val done = AtomicInteger(0)
        val matched = mutableListOf<Track?>()
        // Small batches keep YouTube happy while still cutting a long playlist's wait.
        playlist.tracks.chunked(MAX_PARALLEL).forEach { chunk ->
            val batch = coroutineScope {
                chunk.map { ref ->
                    async {
                        val found = runCatching { matchTrack(ref) }
                            .onFailure { Log.w(TAG, "Match failed for '${ref.title}': ${it.message}") }
                            .getOrNull()
                        onProgress(done.incrementAndGet(), total)
                        found
                    }
                }.awaitAll()
            }
            matched += batch
        }

        val seen = HashSet<String>()
        val tracks = matched.filterNotNull().filter { seen.add(it.id) }
        val failed = playlist.tracks
            .filterIndexed { index, _ -> matched[index] == null }
            .distinctBy { it.title.lowercase() + "|" + it.artist.lowercase() }
        Resolution(tracks, failed, total)
    }

    private suspend fun matchTrack(ref: SpotifyTrackRef): Track? {
        val cleanTitle = ref.title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ").trim()
        val queries = listOf(
            "${ref.title} ${ref.artist}",
            "$cleanTitle ${ref.artist}"
        ).distinct()

        for (query in queries) {
            val candidates = youTubeRepository.searchTracks(query)
            val best = candidates.maxByOrNull { score(ref, it) } ?: continue
            if (score(ref, best) >= MIN_SCORE) return best
        }
        return null
    }

    companion object {
        private const val TAG = "SpotifyImporter"
        private const val MAX_PARALLEL = 2
        private const val MIN_SCORE = 0.45

        private val DECORATIONS = Regex(
            "\\b(feat|ft|featuring|prod|remaster|remastered|remix|version|official|video|audio|lyrics?|visualizer)\\b"
        )
        private val BRACKETS = Regex("\\(.*?\\)|\\[.*?\\]")
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9 ]")
        private val SPACES = Regex("\\s+")

        internal fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(Locale.ROOT)
                .replace(BRACKETS, " ")
                .replace(DECORATIONS, " ")
                .replace(NON_ALPHANUMERIC, " ")
                .replace(SPACES, " ")
                .trim()

        private fun tokens(text: String): Set<String> =
            normalize(text).split(' ').filter { it.length > 1 }.toSet()

        internal fun score(spotify: SpotifyTrackRef, candidate: Track): Double {
            val wantedTitle = tokens(spotify.title)
            if (wantedTitle.isEmpty()) return 0.0
            val candidateTitle = tokens(candidate.title)
            val titleScore = wantedTitle.count { it in candidateTitle }.toDouble() / wantedTitle.size

            val wantedArtist = tokens(spotify.artist)
            val candidateArtist = tokens(ArtistNormalizer.normalize(candidate.artist))
            val artistScore = if (wantedArtist.isEmpty()) 0.0 else {
                wantedArtist.count { it in candidateArtist }.toDouble() / wantedArtist.size
            }

            val durationScore = if (spotify.durationMs > 0 && candidate.durationMs > 0) {
                val diff = kotlin.math.abs(spotify.durationMs - candidate.durationMs)
                (1.0 - diff / 30_000.0).coerceIn(0.0, 1.0)
            } else {
                0.5
            }

            return titleScore * 0.60 + artistScore * 0.28 + durationScore * 0.12
        }
    }
}
