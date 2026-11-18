package com.cypher.music.playback

import android.content.Context
import android.net.wifi.WifiManager
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CypherConnectManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("cypher_remote", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isRemoteActive = MutableStateFlow(false)
    val isRemoteActive: StateFlow<Boolean> = _isRemoteActive.asStateFlow()

    private val _pcIp = MutableStateFlow<String?>(null)
    val pcIp: StateFlow<String?> = _pcIp.asStateFlow()

    private val _remotePlayerState = MutableStateFlow(PlayerState())
    val remotePlayerState: StateFlow<PlayerState> = _remotePlayerState.asStateFlow()

    private val _remoteProgress = MutableStateFlow(0f)
    val remoteProgress: StateFlow<Float> = _remoteProgress.asStateFlow()

    private val _remotePositionMs = MutableStateFlow(0L)
    val remotePositionMs: StateFlow<Long> = _remotePositionMs.asStateFlow()

    private val _remoteVolume = MutableStateFlow(0.8f)
    val remoteVolume: StateFlow<Float> = _remoteVolume.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        val lastIp = prefs.getString("last_pc_ip", null)
        if (!lastIp.isNullOrBlank() && lastIp.count { it == '.' } == 3) {
            connect(lastIp, silent = true)
        }
    }

    fun cleanIp(raw: String): String {
        return raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore(":")
            .substringBefore("?")
            .substringBefore("/")
    }

    fun connect(targetIp: String, silent: Boolean = false, onResult: (Boolean) -> Unit = {}) {
        val ip = cleanIp(targetIp)
        if (ip.isBlank()) {
            onResult(false)
            return
        }

        scope.launch {
            val ok = runCatching {
                val conn = URL("http://$ip:8484/api/status").openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.responseCode == 200
            }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                if (ok) {
                    _pcIp.value = ip
                    _isConnected.value = true
                    _isRemoteActive.value = true
                    prefs.edit().putString("last_pc_ip", ip).apply()
                    startPolling(ip)
                    onResult(true)
                } else {
                    if (!silent) {
                        _isConnected.value = false
                        _isRemoteActive.value = false
                    }
                    onResult(false)
                }
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        _isRemoteActive.value = false
        _isConnected.value = false
        _remotePlayerState.value = PlayerState()
    }

    fun setRemoteActive(active: Boolean) {
        _isRemoteActive.value = active
        if (active && _isConnected.value) {
            _pcIp.value?.let { startPolling(it) }
        } else if (!active) {
            pollJob?.cancel()
        }
    }

    fun sendAction(action: String, value: String? = null) {
        val ip = _pcIp.value ?: return
        scope.launch {
            runCatching {
                val conn = URL("http://$ip:8484/api/action").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val json = JSONObject().apply {
                    put("action", action)
                    if (value != null) put("value", value)
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
            }
            // Trigger an immediate quick poll after sending action
            delay(150)
            pollStatusOnce(ip)
        }
    }

    fun togglePlayPause() = sendAction("toggle")
    fun playNext() = sendAction("next")
    fun playPrevious() = sendAction("previous")
    fun seek(fraction: Float) = sendAction("seek", fraction.toString())
    fun setVolume(volume: Float) {
        _remoteVolume.value = volume
        sendAction("volume", volume.toString())
    }

    private fun startPolling(ip: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var consecutiveFails = 0
            while (isActive && _isConnected.value) {
                val success = pollStatusOnce(ip)
                if (success) {
                    consecutiveFails = 0
                } else {
                    consecutiveFails++
                    if (consecutiveFails >= 5) {
                        _isConnected.value = false
                        _isRemoteActive.value = false
                        break
                    }
                }
                delay(1000L)
            }
        }
    }

    private suspend fun pollStatusOnce(ip: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("http://$ip:8484/api/status").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val title = json.optString("title", "Sin reproducción")
                    val artist = json.optString("artist", "Cypher PC")
                    val album = json.optString("album", "Cypher")
                    val artwork = json.optString("artwork", null)?.takeIf { it.isNotBlank() }
                    val playing = json.optBoolean("playing", false)
                    val pos = json.optLong("positionMs", 0L)
                    val dur = json.optLong("durationMs", 0L)
                    val vol = json.optDouble("volume", 0.8).toFloat()

                    val track = if (title.isNotBlank() && title != "Sin reproducción") {
                        Track(
                            id = "remote:$title:$artist",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = dur,
                            artworkUrl = artwork,
                            sourceType = AudioSourceType.YOUTUBE
                        )
                    } else null

                    val newState = PlayerState(
                        currentTrack = track,
                        isPlaying = playing,
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                    val prog = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

                    withContext(Dispatchers.Main) {
                        _remotePlayerState.value = newState
                        _remoteProgress.value = prog
                        _remotePositionMs.value = pos
                        _remoteVolume.value = vol
                    }
                    true
                } else false
            }.getOrDefault(false)
        }
    }

    fun autoScan(onFound: (String) -> Unit, onNotFound: () -> Unit) {
        if (_isScanning.value) return
        _isScanning.value = true

        scope.launch {
            val baseSubnet = runCatching {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wm?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}."
                } else "192.168.1."
            }.getOrDefault("192.168.1.")

            var found: String? = null
            coroutineScope {
                val deferreds = (1..254).map { i ->
                    async {
                        val candidate = "$baseSubnet$i"
                        val ok = runCatching {
                            val conn = URL("http://$candidate:8484/api/status").openConnection() as HttpURLConnection
                            conn.connectTimeout = 700
                            conn.readTimeout = 700
                            conn.responseCode == 200
                        }.getOrDefault(false)
                        if (ok) candidate else null
                    }
                }
                for (d in deferreds) {
                    val candidate = d.await()
                    if (candidate != null) {
                        found = candidate
                        break
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _isScanning.value = false
                if (found != null) {
                    connect(found!!)
                    onFound(found!!)
                } else {
                    onNotFound()
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: CypherConnectManager? = null

        fun getInstance(context: Context): CypherConnectManager {
            return instance ?: synchronized(this) {
                instance ?: CypherConnectManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
