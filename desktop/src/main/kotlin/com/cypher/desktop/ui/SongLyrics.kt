package com.cypher.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cypher.desktop.core.LibraryData
import com.cypher.desktop.core.LyricLine
import com.cypher.desktop.core.Lyrics
import com.cypher.desktop.core.LyricsProvider

data class SongLyrics(val lines: List<LyricLine>, val status: LyricsProvider.Status)

/**
 * One source of truth for a song's lyrics, used by the right panel and the expanded screen.
 *
 * Order: a document stored with the song, then a `.lrc` next to a local file, then the disk
 * cache, then the provider. While the provider is being asked the state is LOADING, so the
 * panel can say "buscando" instead of "no hay letra".
 */
@Composable
fun rememberSongLyrics(data: LibraryData, view: TrackView?, path: String?): SongLyrics {
    val stored = view?.let { data.lyric(it.id) }
    val key = listOf(view?.id, path, stored?.updatedAt, view?.title, view?.artist)

    var result by remember(key) { mutableStateOf(SongLyrics(emptyList(), LyricsProvider.Status.LOADING)) }

    LaunchedEffect(key) {
        if (view == null) {
            result = SongLyrics(emptyList(), LyricsProvider.Status.NOT_FOUND)
            return@LaunchedEffect
        }
        val local = stored?.let { Lyrics.fromText(it.lrc) }.orEmpty()
            .ifEmpty { Lyrics.forTrack(path) }
        if (local.isNotEmpty()) {
            result = SongLyrics(local, LyricsProvider.Status.FOUND)
            return@LaunchedEffect
        }
        LyricsProvider.cached(view.title, view.artist)?.let { cached ->
            result = SongLyrics(cached, LyricsProvider.Status.FOUND)
            return@LaunchedEffect
        }
        result = SongLyrics(emptyList(), LyricsProvider.Status.LOADING)
        val remote = LyricsProvider.load(
            view.title, view.artist, view.album, view.durationMs.toLong()
        )
        result = SongLyrics(remote.lines, remote.status)
    }

    return result
}
