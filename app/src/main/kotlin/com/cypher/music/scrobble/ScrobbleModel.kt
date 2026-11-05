package com.cypher.music.scrobble

/**
 * Supported scrobbling backends.
 */
enum class ScrobbleServiceType {
    LAST_FM,
    LISTENBRAINZ
}

/**
 * Configuration and credentials for scrobbling services.
 */
data class ScrobbleConfig(
    val isEnabled: Boolean = true,
    val lastFmApiKey: String = "4a9f2c79bb8e400d4c57e80a0a9efb92", // Demo API key
    val lastFmSessionKey: String = "",
    val lastFmSecret: String = "",
    val listenBrainzUserToken: String = "",
    val scrobblePercentage: Float = 0.50f,
    val minDurationSec: Long = 30L
)

/**
 * Payload describing a track playback event for scrobblers.
 */
data class ScrobbleTrackPayload(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val timestampSeconds: Long = System.currentTimeMillis() / 1000L
)
