package com.cypher.music.ui.main.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cypher.music.data.LocalMusicRepository
import com.cypher.music.data.Playlist
import com.cypher.music.domain.usecase.GetMediaUseCase
import com.cypher.music.model.Track
import com.cypher.music.network.YouTubeRepository
import com.cypher.music.playback.AudioVolumeManager
import com.cypher.music.playback.EqualizerManager
import com.cypher.music.playback.MusicPlayerManager
import com.cypher.music.playback.PlayerState
import com.cypher.music.playback.SleepTimerMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    application: Application,
    val playerManager: MusicPlayerManager = MusicPlayerManager.getInstance(application),
    val volumeManager: AudioVolumeManager = AudioVolumeManager(application),
    val getMediaUseCase: GetMediaUseCase = GetMediaUseCase(
        LocalMusicRepository(application),
        YouTubeRepository()
    )
) : AndroidViewModel(application) {

    val playerState: StateFlow<PlayerState> = playerManager.state
    val playbackProgress: StateFlow<Float> = playerManager.playbackProgress
    val playbackPositionMs: StateFlow<Long> = playerManager.playbackPositionMs
    val volumeFraction: StateFlow<Float> = volumeManager.volumeFraction
    val sleepTimerMode: StateFlow<SleepTimerMode> = playerManager.sleepTimerMode
    val sleepTimerRemainingSeconds: StateFlow<Long?> = playerManager.sleepTimerRemainingSeconds
    val equalizerManager: EqualizerManager = playerManager.equalizerManager

    /**
     * Consumes GetMediaUseCase flow to resolve playable track without
     * PlayerViewModel needing to know if media is Local MediaStore or YouTube.
     */
    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        viewModelScope.launch {
            getMediaUseCase.resolvePlayableMedia(track).collect { resolvedTrack ->
                playerManager.playTrack(resolvedTrack, queue)
            }
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        if (playlist.tracks.isEmpty()) return
        val list = if (shuffle) playlist.tracks.shuffled() else playlist.tracks
        playTrack(list.first(), list)
    }

    fun playFromUri(uri: Uri, title: String) {
        val track = getMediaUseCase.createMediaFromUri(uri, title)
        playTrack(track, listOf(track))
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun pause() {
        playerManager.pause()
    }

    fun seekToProgress(fraction: Float) {
        playerManager.seekToProgress(fraction)
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun enqueueNext(track: Track) {
        playerManager.enqueueNext(track)
    }

    fun addToQueue(track: Track) {
        playerManager.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerManager.removeFromQueue(index)
    }

    fun jumpToQueueIndex(index: Int) {
        playerManager.jumpToQueueIndex(index)
    }

    fun setMasterPlaybackVolume(fraction: Float) {
        volumeManager.setVolumeFraction(fraction)
        playerManager.setMasterPlaybackVolume(fraction)
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        playerManager.setSleepTimer(mode)
    }
}
