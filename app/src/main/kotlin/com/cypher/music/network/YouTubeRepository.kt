package com.cypher.music.network

import android.util.Log
import com.cypher.music.data.ArtistNormalizer
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class YouTubeDns : Dns {
    private val fallbackIps by lazy {
        listOf(
            InetAddress.getByName("142.251.156.4"),
            InetAddress.getByName("142.251.157.4"),
            InetAddress.getByName("142.251.153.4"),
            InetAddress.getByName("142.251.154.4")
        )
    }

    private val dohClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.contains("youtube.com") || hostname.contains("googlevideo.com")) {
            try {
                val req = Request.Builder()
                    .url("https://cloudflare-dns.com/dns-query?name=$hostname&type=A")
                    .header("Accept", "application/dns-json")
                    .build()
                val res = dohClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val root = JSONObject(body)
                    val answers = root.optJSONArray("Answer")
                    if (answers != null && answers.length() > 0) {
                        val list = mutableListOf<InetAddress>()
                        for (i in 0 until answers.length()) {
                            val ans = answers.getJSONObject(i)
                            if (ans.optInt("type") == 1) {
                                val ip = ans.optString("data")
                                if (ip.isNotBlank() && ip != "127.0.0.1" && ip != "0.0.0.0") {
                                    list.add(InetAddress.getByName(ip))
                                }
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (e: Exception) {
                Log.w("AppleMusicPlayer", "DoH lookup failed for $hostname, using fallback IPs", e)
            }
            return fallbackIps
        }

        return try {
            val systemResolved = Dns.SYSTEM.lookup(hostname)
            if (systemResolved.isNotEmpty() && systemResolved.none { it.hostAddress == "127.0.0.1" || it.hostAddress == "0.0.0.0" }) {
                systemResolved
            } else {
                fallbackIps
            }
        } catch (e: Exception) {
            fallbackIps
        }
    }
}

class YouTubeRepository {
    companion object {
        const val ANDROID_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        const val CONSENT_COOKIE = "SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; CONSENT=YES+"
    }

    private val client = OkHttpClient.Builder()
        .dns(YouTubeDns())
        .followRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1. Primary: Direct YouTube HTML Results with embedded ytInitialData (0 rate limits, instant)
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encodedQuery")
                .header("User-Agent", ANDROID_USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Cookie", CONSENT_COOKIE)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val marker = "ytInitialData = "
                val startIdx = html.indexOf(marker).let { if (it != -1) it + marker.length else -1 }
                if (startIdx != -1) {
                    val endIdx = html.indexOf(";</script>", startIdx).let { if (it != -1) it else html.indexOf(";\n", startIdx) }
                    val jsonStr = if (endIdx != -1) html.substring(startIdx, endIdx) else html.substring(startIdx)
                    val root = JSONObject(jsonStr)
                    val sections = root.optJSONObject("contents")
                        ?.optJSONObject("twoColumnSearchResultsRenderer")
                        ?.optJSONObject("primaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents") ?: JSONArray()

                    val results = mutableListOf<Track>()

                    for (s in 0 until sections.length()) {
                        val section = sections.getJSONObject(s)
                        val items = section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val video = item.optJSONObject("videoRenderer") ?: continue
                            val videoId = video.optString("videoId")
                            if (videoId.isNotBlank()) {
                                val title = video.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Pista YouTube"
                                val artist = video.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Artista de YouTube"

                                val thumbs = video.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                                    thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                                } else {
                                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                }

                                val durationText = video.optJSONObject("lengthText")?.optString("simpleText") ?: "3:30"
                                val durationMs = parseDuration(durationText)

                                results.add(
                                    Track(
                                        id = "yt_$videoId",
                                        title = cleanTitle(title),
                                        artist = ArtistNormalizer.normalize(artist),
                                        album = "YouTube Music",
                                        durationMs = durationMs,
                                        artworkUrl = thumbUrl,
                                        streamUrl = videoId,
                                        sourceType = AudioSourceType.YOUTUBE
                                    )
                                )
                            }
                        }
                    }

                    if (results.isNotEmpty()) {
                        Log.d("AppleMusicPlayer", "YouTube HTML search returned ${results.size} tracks for '$query'")
                        return@withContext results
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AppleMusicPlayer", "YouTube HTML search failed", e)
        }

        // 2. Secondary backup: Piped API
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://api.piped.private.coffee/search?q=$encodedQuery&filter=music_songs")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val root = JSONObject(body)
                val items = root.optJSONArray("items") ?: JSONArray()
                val results = mutableListOf<Track>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val url = item.optString("url", "")
                    val vid = url.removePrefix("/watch?v=").ifEmpty { item.optString("id") }
                    if (vid.isNotBlank()) {
                        results.add(
                            Track(
                                id = "yt_$vid",
                                title = cleanTitle(item.optString("title", "Canción")),
                                artist = ArtistNormalizer.normalize(item.optString("uploaderName", "Artista")),
                                album = "YouTube Music",
                                durationMs = item.optLong("duration", 210L) * 1000L,
                                artworkUrl = item.optString("thumbnail", "https://i.ytimg.com/vi/$vid/hqdefault.jpg"),
                                streamUrl = vid,
                                sourceType = AudioSourceType.YOUTUBE
                            )
                        )
                    }
                }
                if (results.isNotEmpty()) return@withContext results
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        getDefaultFeaturedTracks().filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    suspend fun getTrending(): List<Track> = withContext(Dispatchers.IO) {
        // Fetch real trending hits
        val tracks = searchTracks("Top Éxitos España 2026")
        if (tracks.isNotEmpty()) {
            return@withContext tracks
        }
        getDefaultFeaturedTracks()
    }

    /**
     * Resolves a direct audio stream URL for a YouTube videoId with multi-tier resilience:
     * - Tier 1: yt-dlp android,ios,tv player clients (bypasses default SABR limits)
     * - Tier 2: yt-dlp tv_embedded,mweb fallback
     * - Tier 3: InnerTube TVHTML5/Android direct API endpoint
     * Integrates passive telemetry to record and adapt to structural breakages.
     */
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanVid = videoId.removePrefix("yt_").trim()
        if (cleanVid.isEmpty()) return@withContext null
        val targetUrl = "https://www.youtube.com/watch?v=$cleanVid"

        // TIER 1: yt-dlp with android,ios,tv extractor args
        try {
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(targetUrl).apply {
                addOption("-f", "140/bestaudio[ext=m4a]/bestaudio/best")
                addOption("-g")
                addOption("--extractor-args", "youtube:player_client=android,ios,tv")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-update")
            }
            val res = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            val directUrl = res.out.trim().lines().firstOrNull { it.startsWith("http") }
            if (!directUrl.isNullOrBlank()) {
                StreamingTelemetry.recordSuccess(tier = 1)
                StreamingTelemetry.recordTrackResolution(cleanVid, tier = 1)
                return@withContext directUrl
            }
        } catch (e: Exception) {
            StreamingTelemetry.recordError(errorCode = 403, message = e.message, endpoint = "yt-dlp:tier1")
            Log.w("YouTubeRepository", "yt-dlp tier 1 failed: ${e.message}, falling back to tier 2...")
        }

        // TIER 2: yt-dlp with tv_embedded and relaxed format
        try {
            val requestTier2 = com.yausername.youtubedl_android.YoutubeDLRequest(targetUrl).apply {
                addOption("-f", "bestaudio/best")
                addOption("-g")
                addOption("--extractor-args", "youtube:player_client=tv_embedded,web_creator,mweb")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-update")
            }
            val res2 = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(requestTier2)
            val directUrl2 = res2.out.trim().lines().firstOrNull { it.startsWith("http") }
            if (!directUrl2.isNullOrBlank()) {
                StreamingTelemetry.recordSuccess(tier = 2)
                StreamingTelemetry.recordTrackResolution(cleanVid, tier = 2)
                return@withContext directUrl2
            }
        } catch (e2: Exception) {
            StreamingTelemetry.recordError(errorCode = 403, message = e2.message, endpoint = "yt-dlp:tier2")
            Log.w("YouTubeRepository", "yt-dlp tier 2 failed: ${e2.message}, attempting Tier 3 InnerTube...")
        }

        // TIER 3: Direct InnerTube API call (fallback for unauthenticated streaming)
        try {
            val directInnerTubeUrl = resolveInnerTubeStreamUrl(cleanVid)
            if (!directInnerTubeUrl.isNullOrBlank()) {
                StreamingTelemetry.recordSuccess(tier = 3)
                StreamingTelemetry.recordTrackResolution(cleanVid, tier = 3)
                return@withContext directInnerTubeUrl
            }
        } catch (e3: Exception) {
            StreamingTelemetry.recordError(errorCode = 500, message = e3.message, endpoint = "innertube:tier3")
            Log.e("YouTubeRepository", "Tier 3 InnerTube fallback failed: ${e3.message}")
        }

        null
    }

    private fun resolveInnerTubeStreamUrl(videoId: String): String? {
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "TVHTML5")
                    put("clientVersion", "7.20230405.08.01")
                    put("hl", "es")
                    put("gl", "ES")
                })
            })
            put("videoId", videoId)
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player")
            .header("User-Agent", ANDROID_USER_AGENT)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val responseBody = response.body?.string() ?: return null
        val json = JSONObject(responseBody)
        val streamingData = json.optJSONObject("streamingData") ?: return null

        // Try adaptiveFormats first for best audio
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
        if (adaptiveFormats != null) {
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.getJSONObject(i)
                val mimeType = format.optString("mimeType", "")
                if (mimeType.startsWith("audio/")) {
                    val url = format.optString("url")
                    if (url.startsWith("http")) {
                        return url
                    }
                }
            }
        }

        // Fallback to legacy formats
        val formats = streamingData.optJSONArray("formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val format = formats.getJSONObject(i)
                val url = format.optString("url")
                if (url.startsWith("http")) {
                    return url
                }
            }
        }

        return null
    }


    private fun parseDuration(text: String): Long {
        val parts = text.split(":")
        var seconds = 0L
        for (part in parts) {
            seconds = seconds * 60 + (part.toLongOrNull() ?: 0L)
        }
        return (if (seconds > 0) seconds else 210L) * 1000L
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("(?i)\\(official video\\)"), "")
            .replace(Regex("(?i)\\[official music video\\]"), "")
            .replace(Regex("(?i)\\(video oficial\\)"), "")
            .replace(Regex("(?i)\\(audio oficial\\)"), "")
            .replace(Regex("(?i)\\(visualizer\\)"), "")
            .replace(Regex("(?i)\\(letra/lyrics\\)"), "")
            .trim()
    }

    fun getDefaultFeaturedTracks(): List<Track> {
        return listOf(
            Track(
                id = "yt_juRFjpB5Ppg",
                title = "Tití Me Preguntó",
                artist = "Bad Bunny",
                album = "Un Verano Sin Ti",
                durationMs = 243000L,
                artworkUrl = "https://i.ytimg.com/vi/juRFjpB5Ppg/hqdefault.jpg",
                streamUrl = "juRFjpB5Ppg",
                sourceType = AudioSourceType.YOUTUBE
            ),
            Track(
                id = "yt_QlZNGcVfeF0",
                title = "Columbia",
                artist = "Quevedo",
                album = "Buenas Noches",
                durationMs = 186000L,
                artworkUrl = "https://i.ytimg.com/vi/QlZNGcVfeF0/hqdefault.jpg",
                streamUrl = "QlZNGcVfeF0",
                sourceType = AudioSourceType.YOUTUBE
            ),
            Track(
                id = "yt_d_HlPboLRL8",
                title = "Starboy",
                artist = "The Weeknd ft. Daft Punk",
                album = "Starboy",
                durationMs = 230000L,
                artworkUrl = "https://i.ytimg.com/vi/d_HlPboLRL8/hqdefault.jpg",
                streamUrl = "d_HlPboLRL8",
                sourceType = AudioSourceType.YOUTUBE
            ),
            Track(
                id = "yt_4NRXx6U8ABQ",
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                durationMs = 200000L,
                artworkUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                streamUrl = "4NRXx6U8ABQ",
                sourceType = AudioSourceType.YOUTUBE
            ),
            Track(
                id = "yt_50VNCymT-Cs",
                title = "Midnight City",
                artist = "M83",
                album = "Hurry Up, We're Dreaming",
                durationMs = 244000L,
                artworkUrl = "https://i.ytimg.com/vi/50VNCymT-Cs/hqdefault.jpg",
                streamUrl = "50VNCymT-Cs",
                sourceType = AudioSourceType.YOUTUBE
            ),
            Track(
                id = "yt_TUVcZfQe-Kw",
                title = "Levitating",
                artist = "Dua Lipa",
                album = "Future Nostalgia",
                durationMs = 203000L,
                artworkUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
                streamUrl = "TUVcZfQe-Kw",
                sourceType = AudioSourceType.YOUTUBE
            )
        )
    }

    // -----------------------------------------------------------------------------------
    // InnerTube Authenticated Sync (Google / YouTube Music)
    // -----------------------------------------------------------------------------------

    data class RemotePlaylist(
        val id: String,
        val title: String,
        val description: String = "",
        val coverUrl: String? = null,
        val tracks: List<Track> = emptyList()
    )

    suspend fun fetchLikedMusic(accountManager: YouTubeAccountManager): List<Track> = withContext(Dispatchers.IO) {
        val session = accountManager.session.value
        if (!session.isLoggedIn || session.cookie.isNullOrBlank()) return@withContext emptyList()
        val authHeader = accountManager.getInnerTubeAuthHeader() ?: return@withContext emptyList()

        // 1. Try VLLM (Liked Music canonical playlist ID)
        val likedVLLM = executeInnerTubeBrowse("VLLM", session.cookie, authHeader)
        if (likedVLLM.isNotEmpty()) {
            Log.d("AppleMusicPlayer", "fetchLikedMusic retrieved ${likedVLLM.size} tracks from VLLM")
            return@withContext likedVLLM
        }

        // 2. Try FEmusic_liked_videos
        val likedVideos = executeInnerTubeBrowse("FEmusic_liked_videos", session.cookie, authHeader)
        if (likedVideos.isNotEmpty()) {
            Log.d("AppleMusicPlayer", "fetchLikedMusic retrieved ${likedVideos.size} tracks from FEmusic_liked_videos")
            return@withContext likedVideos
        }

        // 3. Try VLSE (Secondary liked videos list)
        executeInnerTubeBrowse("VLSE", session.cookie, authHeader)
    }

    suspend fun fetchUserPlaylists(accountManager: YouTubeAccountManager): List<RemotePlaylist> = withContext(Dispatchers.IO) {
        val session = accountManager.session.value
        if (!session.isLoggedIn || session.cookie.isNullOrBlank()) return@withContext emptyList()
        val authHeader = accountManager.getInnerTubeAuthHeader() ?: return@withContext emptyList()

        val playlists = mutableListOf<RemotePlaylist>()

        // 1. Try FEmusic_liked_playlists (Primary for modern YouTube Music)
        val list1 = executeInnerTubeBrowsePlaylists("FEmusic_liked_playlists", session.cookie, authHeader)
        playlists.addAll(list1)

        // 2. Try FEmusic_library_playlists
        if (playlists.isEmpty()) {
            val list2 = executeInnerTubeBrowsePlaylists("FEmusic_library_playlists", session.cookie, authHeader)
            playlists.addAll(list2)
        }

        Log.d("AppleMusicPlayer", "fetchUserPlaylists found ${playlists.size} playlists")

        // 3. For each playlist, fetch tracks
        val fullPlaylists = mutableListOf<RemotePlaylist>()
        for (item in playlists.distinctBy { it.id }) {
            val tracks = fetchPlaylistTracks(item.id, accountManager)
            Log.d("AppleMusicPlayer", "fetchPlaylistTracks for '${item.title}' (${item.id}): ${tracks.size} tracks")
            fullPlaylists.add(item.copy(tracks = tracks))
        }

        fullPlaylists
    }

    private fun executeInnerTubeBrowse(browseId: String, cookie: String, authHeader: String): List<Track> {
        try {
            var cookieToSend = cookie
            if (!cookieToSend.contains("SOCS=")) {
                cookieToSend = "$cookieToSend; $CONSENT_COOKIE"
            }

            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240901.01.00")
                        put("hl", "es")
                        put("gl", "ES")
                    })
                })
                put("browseId", browseId)
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-Origin", "https://music.youtube.com")
                .header("Cookie", cookieToSend)
                .header("Authorization", authHeader)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240901.01.00")
                .header("X-Goog-AuthUser", "0")
                .header("User-Agent", ANDROID_USER_AGENT)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: ""
                val root = JSONObject(respBody)
                return parseInnerTubeTrackList(root)
            } else {
                Log.w("AppleMusicPlayer", "InnerTube browse $browseId returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("AppleMusicPlayer", "InnerTube browse $browseId failed", e)
        }
        return emptyList()
    }

    private fun executeInnerTubeBrowsePlaylists(browseId: String, cookie: String, authHeader: String): List<RemotePlaylist> {
        try {
            var cookieToSend = cookie
            if (!cookieToSend.contains("SOCS=")) {
                cookieToSend = "$cookieToSend; $CONSENT_COOKIE"
            }

            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240901.01.00")
                        put("hl", "es")
                        put("gl", "ES")
                    })
                })
                put("browseId", browseId)
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-Origin", "https://music.youtube.com")
                .header("Cookie", cookieToSend)
                .header("Authorization", authHeader)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240901.01.00")
                .header("X-Goog-AuthUser", "0")
                .header("User-Agent", ANDROID_USER_AGENT)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: ""
                val root = JSONObject(respBody)
                return parseInnerTubePlaylistList(root)
            } else {
                Log.w("AppleMusicPlayer", "InnerTube browse playlists $browseId returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("AppleMusicPlayer", "InnerTube browse playlists $browseId failed", e)
        }
        return emptyList()
    }

    suspend fun fetchPlaylistTracks(browseId: String, accountManager: YouTubeAccountManager?): List<Track> = withContext(Dispatchers.IO) {
        val session = accountManager?.session?.value
        val reqBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20240901.01.00")
            .header("X-Goog-AuthUser", "0")
            .header("User-Agent", ANDROID_USER_AGENT)

        if (session != null && session.isLoggedIn && !session.cookie.isNullOrBlank()) {
            val authHeader = accountManager.getInnerTubeAuthHeader()
            if (authHeader != null) {
                var cookieToSend = session.cookie
                if (!cookieToSend.contains("SOCS=")) {
                    cookieToSend = "$cookieToSend; $CONSENT_COOKIE"
                }
                reqBuilder.header("Cookie", cookieToSend)
                reqBuilder.header("Authorization", authHeader)
            }
        } else {
            reqBuilder.header("Cookie", CONSENT_COOKIE)
        }

        val formattedBrowseId = if (browseId.startsWith("VL")) browseId else "VL$browseId"
        val bodyJson = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240901.01.00")
                    put("hl", "es")
                    put("gl", "ES")
                })
            })
            put("browseId", formattedBrowseId)
        }

        try {
            val req = reqBuilder.post(bodyJson.toString().toRequestBody("application/json".toMediaType())).build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val respBody = res.body?.string() ?: ""
                val root = JSONObject(respBody)
                return@withContext parseInnerTubeTrackList(root)
            }
        } catch (e: Exception) {
            Log.w("AppleMusicPlayer", "Failed to fetch tracks for playlist $browseId", e)
        }
        emptyList()
    }

    private fun findJsonObjectsByKey(json: Any?, key: String, action: (JSONObject) -> Unit) {
        when (json) {
            is JSONObject -> {
                val target = json.optJSONObject(key)
                if (target != null) {
                    action(target)
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    findJsonObjectsByKey(json.opt(k), key, action)
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until json.length()) {
                    findJsonObjectsByKey(json.opt(i), key, action)
                }
            }
        }
    }

    private fun parseInnerTubeTrackList(root: JSONObject): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            findJsonObjectsByKey(root, "musicResponsiveListItemRenderer") { renderer ->
                val vid = renderer.optJSONObject("playlistItemData")?.optString("videoId")
                    ?: renderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                    ?: ""

                if (vid.isNotBlank()) {
                    val flexCols = renderer.optJSONArray("flexColumns")
                    var title = ""
                    var artist = ""

                    if (flexCols != null && flexCols.length() > 0) {
                        val col0 = flexCols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val runs0 = col0?.optJSONObject("text")?.optJSONArray("runs")
                        title = runs0?.optJSONObject(0)?.optString("text") ?: ""

                        if (flexCols.length() > 1) {
                            val col1 = flexCols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            val runs1 = col1?.optJSONObject("text")?.optJSONArray("runs")
                            artist = runs1?.optJSONObject(0)?.optString("text") ?: ""
                        }
                    }

                    if (title.isNotBlank()) {
                        tracks.add(
                            Track(
                                id = "yt_$vid",
                                title = cleanTitle(title),
                                artist = ArtistNormalizer.normalize(if (artist.isNotBlank()) artist else "YouTube Music"),
                                album = "YouTube Music",
                                durationMs = 210000L,
                                artworkUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                streamUrl = vid,
                                sourceType = AudioSourceType.YOUTUBE
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AppleMusicPlayer", "parseInnerTubeTrackList JSON traversal error", e)
        }

        // Fallback: If structured traversal didn't catch, use regex
        if (tracks.isEmpty()) {
            try {
                val textStr = root.toString()
                val regex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}\\].*?\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}\\]")
                val matches = regex.findAll(textStr)
                for (m in matches) {
                    val vid = m.groupValues[1]
                    val title = m.groupValues[2]
                    val artist = m.groupValues[3]
                    if (vid.isNotBlank() && title.isNotBlank()) {
                        tracks.add(
                            Track(
                                id = "yt_$vid",
                                title = cleanTitle(title),
                                artist = ArtistNormalizer.normalize(artist),
                                album = "YouTube Music",
                                durationMs = 210000L,
                                artworkUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                streamUrl = vid,
                                sourceType = AudioSourceType.YOUTUBE
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AppleMusicPlayer", "parseInnerTubeTrackList regex fallback error", e)
            }
        }

        return tracks.distinctBy { it.id }
    }

    private fun parseInnerTubePlaylistList(root: JSONObject): List<RemotePlaylist> {
        val playlists = mutableListOf<RemotePlaylist>()
        try {
            findJsonObjectsByKey(root, "musicTwoRowItemRenderer") { renderer ->
                val nav = renderer.optJSONObject("navigationEndpoint")
                val browseEndpoint = nav?.optJSONObject("browseEndpoint")
                val browseId = browseEndpoint?.optString("browseId") ?: ""

                val titleRuns = renderer.optJSONObject("title")?.optJSONArray("runs")
                val title = titleRuns?.optJSONObject(0)?.optString("text") ?: ""

                val thumbList = renderer.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val coverUrl = if (thumbList != null && thumbList.length() > 0) {
                    thumbList.optJSONObject(thumbList.length() - 1)?.optString("url")
                } else null

                if (browseId.isNotBlank() && title.isNotBlank() && !title.equals("Nueva lista", true) && !title.equals("New playlist", true)) {
                    playlists.add(
                        RemotePlaylist(
                            id = browseId,
                            title = title,
                            description = "Sincronizada desde YouTube Music",
                            coverUrl = coverUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AppleMusicPlayer", "parseInnerTubePlaylistList JSON traversal error", e)
        }

        if (playlists.isEmpty()) {
            try {
                findJsonObjectsByKey(root, "musicResponsiveListItemRenderer") { renderer ->
                    val nav = renderer.optJSONObject("navigationEndpoint")
                    val browseEndpoint = nav?.optJSONObject("browseEndpoint")
                    val browseId = browseEndpoint?.optString("browseId") ?: ""

                    val flexCols = renderer.optJSONArray("flexColumns")
                    val col0 = flexCols?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    val title = col0?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                    if (browseId.isNotBlank() && title.isNotBlank() && (browseId.startsWith("VLPL") || browseId.startsWith("FEmusic_"))) {
                        playlists.add(
                            RemotePlaylist(
                                id = browseId,
                                title = title,
                                description = "Sincronizada desde YouTube Music"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return playlists.distinctBy { it.id }
    }

    suspend fun getSmartRecommendations(
        favoriteArtists: List<String>,
        favoriteGenres: List<String>
    ): List<Track> = withContext(Dispatchers.IO) {
        val normalizedArtists = favoriteArtists.map { ArtistNormalizer.normalize(it) }.distinct()
        val results = mutableListOf<Track>()

        // 1. Query for each top artist
        for (artist in normalizedArtists.take(3)) {
            val tracks = searchTracks("$artist mix canciones")
            results.addAll(tracks.take(6))
        }

        // 2. Query for favorite genres if artists are few
        if (results.size < 10) {
            for (genre in favoriteGenres.take(2)) {
                val genreTracks = searchTracks("$genre mejores exitos")
                results.addAll(genreTracks.take(4))
            }
        }

        if (results.isEmpty()) {
            return@withContext getDefaultFeaturedTracks()
        }

        return@withContext results.distinctBy { it.id }
    }
}
