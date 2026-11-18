package com.cypher.desktop.connect

import com.cypher.desktop.app.AppState
import com.cypher.desktop.core.Track
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.UUID

@Serializable
data class ConnectStatus(
    val connected: Boolean = true,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1.0f
)

/**
 * Cypher Connect LAN Server.
 * Enables real-time remote control from Cypher Android app and any local web browser.
 */
class ConnectServer(
    private val appState: AppState,
    val port: Int = 8484
) : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var server: HttpServer? = null
    val token: String = UUID.randomUUID().toString().take(8)

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    var activeIp: String = findLanIp()

    val lanIp: String
        get() = activeIp

    val connectUrl: String
        get() = "http://$activeIp:$port?token=$token"

    fun start() {
        if (server != null) return
        runCatching {
            val s = HttpServer.create(InetSocketAddress(port), 0)
            s.createContext("/api/status") { handleStatus(it) }
            s.createContext("/api/action") { handleAction(it) }
            s.createContext("/") { handleWebRemote(it) }
            s.executor = null
            s.start()
            server = s
        }.onFailure {
            System.err.println("Cypher ConnectServer failed to bind to port $port: ${it.message}")
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    override fun close() = stop()

    private fun handleStatus(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.sendResponseHeaders(405, -1)
            return
        }
        val playback = appState.playback.value
        val track = playback.track
        val status = ConnectStatus(
            connected = true,
            title = track?.title ?: "Sin reproducción",
            artist = track?.artist ?: "",
            album = track?.album ?: "",
            artwork = track?.artwork,
            playing = playback.playing,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            volume = appState.snapshot().preferences.volume
        )
        val jsonBytes = json.encodeToString(status).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, jsonBytes.size.toLong())
        exchange.responseBody.use { it.write(jsonBytes) }
    }

    private fun handleAction(exchange: HttpExchange) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")

        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            return
        }
        if (exchange.requestMethod != "POST") {
            exchange.sendResponseHeaders(405, -1)
            return
        }

        val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
        runCatching {
            val element = json.parseToJsonElement(body) as? JsonObject
            val action = element?.get("action")?.jsonPrimitive?.content ?: ""
            val value = element?.get("value")?.jsonPrimitive?.content

            when (action) {
                "play" -> appState.togglePlayback()
                "pause" -> appState.togglePlayback()
                "toggle" -> appState.togglePlayback()
                "next" -> appState.playNext()
                "previous" -> appState.playPrevious()
                "volume" -> {
                    val vol = value?.toFloatOrNull()?.coerceIn(0f, 1f)
                    if (vol != null) appState.setVolume(vol)
                }
                "seek" -> {
                    val frac = value?.toFloatOrNull()?.coerceIn(0f, 1f)
                    if (frac != null) appState.seek(frac)
                }
            }
        }

        val resp = """{"success":true}""".toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, resp.size.toLong())
        exchange.responseBody.use { it.write(resp) }
    }

    private fun handleWebRemote(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        if (path != "/" && path != "/index.html") {
            exchange.sendResponseHeaders(404, -1)
            return
        }

        val html = """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <title>Cypher Remote</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0E0A14; color: #FFF; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; }
        .card { width: 100%; max-width: 380px; background: rgba(30, 22, 42, 0.85); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.1); border-radius: 28px; padding: 24px; box-shadow: 0 16px 40px rgba(0,0,0,0.6); text-align: center; }
        .artwork { width: 220px; height: 220px; border-radius: 20px; object-fit: cover; margin: 0 auto 20px; box-shadow: 0 12px 28px rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.12); }
        .title { font-size: 20px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 6px; }
        .artist { font-size: 14px; color: #B3A5C7; margin-bottom: 24px; }
        .controls { display: flex; align-items: center; justify-content: center; gap: 20px; margin-bottom: 24px; }
        .btn { background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15); color: #FFF; width: 56px; height: 56px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 22px; cursor: pointer; transition: 0.15s; }
        .btn:active { transform: scale(0.92); background: rgba(255,255,255,0.2); }
        .btn-main { width: 72px; height: 72px; background: #C084FC; color: #0E0A14; border: none; font-size: 28px; }
        .slider-box { display: flex; align-items: center; gap: 12px; width: 100%; }
        .slider { flex: 1; accent-color: #C084FC; height: 6px; border-radius: 3px; cursor: pointer; }
        .badge { display: inline-block; font-size: 11px; font-weight: 700; letter-spacing: 1px; color: #C084FC; background: rgba(192, 132, 252, 0.15); padding: 4px 12px; border-radius: 12px; margin-bottom: 18px; }
    </style>
</head>
<body>
    <div class="card">
        <span class="badge">CYPHER CONNECT</span>
        <img id="art" class="artwork" src="" alt="Album Art" style="display:none;">
        <div id="title" class="title">Conectando...</div>
        <div id="artist" class="artist">Cypher PC</div>
        <div class="controls">
            <button class="btn" onclick="sendAction('previous')">⏮</button>
            <button id="playBtn" class="btn btn-main" onclick="sendAction('toggle')">▶</button>
            <button class="btn" onclick="sendAction('next')">⏭</button>
        </div>
        <div class="slider-box">
            <span>🔈</span>
            <input type="range" id="vol" class="slider" min="0" max="100" value="80" oninput="setVol(this.value)">
            <span>🔊</span>
        </div>
    </div>
    <script>
        async function fetchStatus() {
            try {
                const res = await fetch('/api/status');
                const data = await res.json();
                document.getElementById('title').textContent = data.title || "Sin reproducción";
                document.getElementById('artist').textContent = data.artist || "Cypher PC";
                document.getElementById('playBtn').textContent = data.playing ? "⏸" : "▶";
                const art = document.getElementById('art');
                if (data.artwork) {
                    art.src = data.artwork;
                    art.style.display = 'block';
                } else {
                    art.style.display = 'none';
                }
                document.getElementById('vol').value = Math.round(data.volume * 100);
            } catch (e) {}
        }
        async function sendAction(act, val) {
            await fetch('/api/action', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: act, value: val })
            });
            fetchStatus();
        }
        function setVol(val) {
            sendAction('volume', (val / 100).toString());
        }
        setInterval(fetchStatus, 1500);
        fetchStatus();
    </script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    fun findLanIp(): String {
        // 1. Direct OS routing table query for the outbound interface
        runCatching {
            java.net.DatagramSocket().use { socket ->
                socket.connect(java.net.InetSocketAddress("1.1.1.1", 80))
                val addr = socket.localAddress.hostAddress
                if (!addr.isNullOrBlank() && addr != "0.0.0.0" && !addr.startsWith("127.")) {
                    return addr
                }
            }
        }
        // 2. Iterate network interfaces, filtering out virtual / vmware / vbox / wsl / docker
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var fallback = "127.0.0.1"
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = (iface.name + " " + iface.displayName).lowercase()
                if (iface.isLoopback || !iface.isUp) continue
                if (name.contains("vbox") || name.contains("virtual") || name.contains("vmware") ||
                    name.contains("wsl") || name.contains("docker") || name.contains("pseudo") ||
                    name.contains("bluetooth") || name.contains("loopback")) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is InetAddress && !addr.isLoopbackAddress && addr.hostAddress.contains(".")) {
                        val ip = addr.hostAddress
                        if (ip.startsWith("192.168.56.")) continue // Ignore default VirtualBox subnet
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                        fallback = ip
                    }
                }
            }
            fallback
        }.getOrDefault("127.0.0.1")
    }

    fun findAllLanIps(): List<String> {
        val list = mutableListOf<String>()
        val primary = findLanIp()
        if (primary != "127.0.0.1") list.add(primary)
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = (iface.name + " " + iface.displayName).lowercase()
                if (iface.isLoopback || !iface.isUp) continue
                if (name.contains("vbox") || name.contains("virtual") || name.contains("vmware") ||
                    name.contains("wsl") || name.contains("docker") || name.contains("pseudo") ||
                    name.contains("bluetooth")) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is InetAddress && !addr.isLoopbackAddress && addr.hostAddress.contains(".")) {
                        val ip = addr.hostAddress
                        if (ip.startsWith("192.168.56.")) continue
                        if (!list.contains(ip)) list.add(ip)
                    }
                }
            }
        }
        if (list.isEmpty()) list.add("127.0.0.1")
        return list
    }
}
