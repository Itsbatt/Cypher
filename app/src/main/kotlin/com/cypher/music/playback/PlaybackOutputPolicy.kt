package com.cypher.music.playback

/**
 * Decides which engine owns a track's audio.
 *
 * Offline downloads keep their YOUTUBE source type but are rendered by
 * ExoPlayer, so transport and volume must follow ExoPlayer instead of the
 * IFrame. Getting this wrong left downloaded tracks muted after any streaming
 * playback, because the silent carrier had set ExoPlayer volume to zero.
 */
object PlaybackOutputPolicy {
    fun usesExoPlayer(isLocalSource: Boolean, isOfflineDownload: Boolean): Boolean =
        isLocalSource || isOfflineDownload
}
