package com.cypher.desktop.player

import com.cypher.desktop.core.Storage
import com.sun.net.httpserver.HttpServer
import me.friwi.jcefmaven.CefAppBuilder
import org.cef.browser.CefBrowser
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * YouTube playback through an embedded Chromium (JCEF), the desktop counterpart of the
 * phone's iframe: the song is streamed by YouTube itself instead of being downloaded to a
 * cache file first.
 *
 * Chromium cannot be told what to play from Java, so the browser loads a tiny page served
 * from a loopback HTTP server. The page mounts the official YouTube player and talks back
 * to us over that same server: it reports position/duration/state a few times a second and
 * asks for the next command, which is how the app's own play button and seek bar reach the
 * video.
 *
 * Everything here fails soft: if CEF is missing (no natives, no GPU, offline) [error] holds
 * the reason and the caller falls back to the normal cover.
 */
object YouTubeEmbed {

    @Volatile var positionMs: Long = 0
        private set
    @Volatile var durationMs: Long = 0
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var error: String? = null
        private set

    /** Set by the player when YouTube refuses the song (private, age-locked, no embed). */
    @Volatile var refused: Boolean = false
        private set

    var browser: CefBrowser? = null
        private set

    private var app: org.cef.CefApp? = null
    private var server: HttpServer? = null
    private var port = 0
    private val commands = ArrayDeque<String>()

    private val page = """
        <!doctype html>
        <html><head><meta charset="utf-8"><style>
        html,body{margin:0;height:100%;background:#000;overflow:hidden}
        #p{width:100%;height:100%}
        </style></head><body><div id="p"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        var player = null;
        var videoId = new URLSearchParams(location.search).get('v') || '';
        function onYouTubeIframeAPIReady() {
          player = new YT.Player('p', {
            videoId: videoId,
            playerVars: {autoplay: 1, controls: 1, rel: 0, playsinline: 1, modestbranding: 1},
            events: {
              onReady: function (e) { e.target.playVideo(); },
              onError: function (e) { fetch('/err?c=' + e.data); }
            }
          });
          setInterval(report, 400);
          setInterval(poll, 250);
        }
        function report() {
          if (!player || !player.getCurrentTime) return;
          var t = Math.round(player.getCurrentTime() * 1000);
          var d = Math.round((player.getDuration() || 0) * 1000);
          fetch('/pos?t=' + t + '&d=' + d + '&p=' + (player.getPlayerState() === 1 ? 1 : 0));
        }
        function poll() {
          fetch('/cmd').then(function (r) { return r.text(); }).then(function (c) {
            if (!c || !player) return;
            if (c === 'play') player.playVideo();
            else if (c === 'pause') player.pauseVideo();
            else if (c === 'toggle') {
              player.getPlayerState() === 1 ? player.pauseVideo() : player.playVideo();
            } else if (c.indexOf('seek:') === 0) {
              player.seekTo(parseInt(c.slice(5), 10) / 1000, true);
            }
          });
        }
        </script></body></html>
    """.trimIndent()

    /** Warms Chromium up (natives, app, loopback server). Blocking: call off the UI. */
    fun prepare(): Boolean = ensure()

    /**
     * Creates the browser that plays [videoId]. It has to be called on the AWT thread and
     * its component added to a real window: Chromium paints into its own surface, which is
     * why the player lives in a small window instead of inside the compose canvas.
     */
    fun openBrowser(videoId: String): CefBrowser? {
        val current = browser
        if (current != null) {
            current.loadURL(url(videoId))
            return current
        }
        if (!ensure()) return null
        val created = app?.createClient()?.createBrowser(url(videoId), false, false) ?: return null
        positionMs = 0
        durationMs = 0
        playing = true
        refused = false
        browser = created
        return created
    }

    private fun url(videoId: String) = "http://127.0.0.1:$port/?v=$videoId"

    fun command(text: String) {
        synchronized(commands) {
            if (commands.size < 8) commands.addLast(text)
        }
    }

    private fun ensure(): Boolean {
        if (browser != null) return true
        error = null
        return runCatching {
            startServer()
            val builder = CefAppBuilder().apply {
                setInstallDir(File(Storage.dataDir.toFile(), "cef").also { it.mkdirs() })
                // Software compositing: with the GPU path Chromium draws a blank surface
                // inside a Compose window.
                addJcefArgs("--disable-gpu", "--disable-gpu-compositing", "--in-process-gpu")
            }
            val created = builder.build()
            app = created
            true
        }.getOrElse { throwable ->
            error = throwable.message ?: throwable::class.java.simpleName
            false
        }
    }

    private fun startServer() {
        if (server != null) return
        val created = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        created.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val query = parseQuery(exchange.requestURI.rawQuery ?: "")
            when (path) {
                "/pos" -> {
                    positionMs = query["t"]?.toLongOrNull() ?: positionMs
                    val reported = query["d"]?.toLongOrNull() ?: 0L
                    if (reported > 0) durationMs = reported
                    playing = query["p"] == "1"
                    exchange.sendResponseHeaders(204, -1)
                }
                "/err" -> {
                    refused = true
                    exchange.sendResponseHeaders(204, -1)
                }
                "/cmd" -> {
                    val next = synchronized(commands) { if (commands.isEmpty()) "" else commands.removeFirst() }
                    respond(exchange, next)
                }
                else -> respond(exchange, page)
            }
            exchange.close()
        }
        created.executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "cypher-embed-http").apply { isDaemon = true }
        }
        created.start()
        server = created
        port = created.address.port
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.take(index) to part.substring(index + 1)
        }.toMap()
}
