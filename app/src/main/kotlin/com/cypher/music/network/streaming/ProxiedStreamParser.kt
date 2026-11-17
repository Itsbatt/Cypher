package com.cypher.music.network.streaming

import org.json.JSONObject

/**
 * Pure parsing of responses from Piped and Invidious video/stream endpoints.
 *
 * Piped streams endpoint (`/streams/{videoId}`):
 *   audioStreams: [ { url, bitrate, mimeType, format } ]
 *
 * Invidious video endpoint (`/api/v1/videos/{videoId}`):
 *   adaptiveFormats: [ { url, bitrate, type / mimeType } ]
 *   formatStreams: [ { url, type } ]
 */
object ProxiedStreamParser {

    /**
     * Parses a Piped streams response, extracting the audio stream with the highest bitrate.
     */
    fun parsePiped(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val audioStreams = json.optJSONArray("audioStreams") ?: return null

        var bestUrl: String? = null
        var maxBitrate = -1L

        for (i in 0 until audioStreams.length()) {
            val stream = audioStreams.optJSONObject(i) ?: continue
            val url = stream.optString("url", "").trim()
            if (!url.startsWith("http")) continue

            val bitrate = stream.optLong("bitrate", 0L)
            if (bitrate > maxBitrate || bestUrl == null) {
                maxBitrate = bitrate
                bestUrl = url
            }
        }
        return bestUrl
    }

    /**
     * Parses an Invidious video response, preferring adaptive audio formats with highest bitrate.
     */
    fun parseInvidious(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val adaptiveFormats = json.optJSONArray("adaptiveFormats")
        if (adaptiveFormats != null) {
            var bestUrl: String? = null
            var maxBitrate = -1L

            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val type = format.optString("type", format.optString("mimeType", ""))
                if (!type.contains("audio/", ignoreCase = true)) continue

                val url = format.optString("url", "").trim()
                if (!url.startsWith("http")) continue

                val bitrate = runCatching { format.optString("bitrate", "0").toLong() }.getOrDefault(0L)
                if (bitrate > maxBitrate || bestUrl == null) {
                    maxBitrate = bitrate
                    bestUrl = url
                }
            }
            if (bestUrl != null) return bestUrl
        }

        // Fallback to formatStreams if adaptive audio was absent
        val formatStreams = json.optJSONArray("formatStreams") ?: return null
        for (i in 0 until formatStreams.length()) {
            val stream = formatStreams.optJSONObject(i) ?: continue
            val url = stream.optString("url", "").trim()
            if (url.startsWith("http")) return url
        }
        return null
    }

    /**
     * Automatically attempts to parse as Piped first, then as Invidious.
     */
    fun parse(body: String): Pair<String, StreamSource>? {
        parsePiped(body)?.let { return Pair(it, StreamSource.PIPED) }
        parseInvidious(body)?.let { return Pair(it, StreamSource.INVIDIOUS) }
        return null
    }
}
