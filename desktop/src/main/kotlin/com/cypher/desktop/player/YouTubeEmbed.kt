package com.cypher.desktop.player

import com.cypher.desktop.core.Storage
import com.sun.net.httpserver.HttpServer
import me.friwi.jcefmaven.CefAppBuilder
import org.cef.browser.CefBrowser
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Why Chromium could not start, written for the person in front of the screen instead of
 * for a log file. The first run has to download ~150 MB into the app's data folder, and a
 * corporate proxy, an antivirus blocking the extraction or a plain offline machine all end
 * up as the same opaque Java exception.
 */
data class YouTubeSetupIssue(
    val headline: String,
    val detail: String,
    val hint: String
)

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
 * the reason, [diagnostic] holds the explanation shown to the user and the caller falls
 * back to the normal cover.
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

    /** Actionable version of [error] for the UI; null while no attempt has failed. */
    @Volatile var diagnostic: YouTubeSetupIssue? = null
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

    /** Where Chromium unpacks its binaries when the app does not ship them. */
    private val installDir: File get() = File(Storage.dataDir.toFile(), "cef")

    /**
     * Retries the bootstrap after the UI showed the diagnostic. Cheap when Chromium is
     * already up, so it is safe to call from a button without extra guards.
     */
    fun retry(): Boolean = ensure()

    private fun ensure(): Boolean {
        if (browser != null) return true
        error = null
        diagnostic = null
        return runCatching {
            startServer()
            // A previous attempt may have built the app and failed later (no window, no
            // GPU); CefApp cannot be initialised twice in the same process.
            if (app == null) {
                val bundled = bundledRuntime()
                val builder = CefAppBuilder().apply {
                    // The MSI and the portable ZIP carry Chromium, so there is nothing to
                    // download or unpack on the first launch. Only a source run (or an
                    // install without the bundled folder) installs into the data dir.
                    if (bundled != null) {
                        setInstallDir(bundled)
                        setSkipInstallation(true)
                    } else {
                        setInstallDir(installDir.also { it.mkdirs() })
                    }
                    // Software compositing: with the GPU path Chromium draws a blank
                    // surface inside a Compose window.
                    addJcefArgs("--disable-gpu", "--disable-gpu-compositing", "--in-process-gpu")
                }
                app = builder.build()
            }
            true
        }.getOrElse { throwable ->
            error = throwable.message ?: throwable::class.java.simpleName
            diagnostic = diagnose(throwable)
            false
        }
    }

    /**
     * The Chromium folder packaged with the app, or null when it is not there (development
     * run, or a distribution built without the CEF resources).
     *
     * Compose exposes its resource folder through a system property; `jpackage.app-path`
     * is the fallback for launchers that do not set it.
     */
    private fun bundledRuntime(): File? {
        val roots = buildList {
            System.getProperty("compose.application.resources.dir")?.let { add(File(it)) }
            System.getProperty("jpackage.app-path")?.let { exe ->
                add(File(File(exe).parentFile, "app/resources"))
            }
        }
        return roots.asSequence()
            .map { File(it, "cef") }
            .firstOrNull { File(it, "libcef.dll").isFile && File(it, "jcef.dll").isFile }
    }

    /**
     * Maps the bootstrap exception to something the user can act on. The order matters:
     * TLS and timeouts are IOExceptions too, so they are matched before the generic disk
     * case.
     */
    private fun diagnose(throwable: Throwable): YouTubeSetupIssue {
        val raw = throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.java.simpleName
        val folder = (bundledRuntime() ?: installDir).absolutePath
        return when (throwable) {
            is javax.net.ssl.SSLException -> YouTubeSetupIssue(
                headline = "La red bloquea la descarga de Chromium",
                detail = "Cypher descarga una sola vez los binarios de Chromium (unos 150 MB) en\n«$folder». La conexión se cerró durante el intercambio cifrado, algo habitual en redes de empresa que inspeccionan el tráfico.",
                hint = "Prueba desde otra red o desactiva temporalmente la inspección TLS. Detalle: $raw"
            )
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException,
            is java.net.SocketTimeoutException -> YouTubeSetupIssue(
                headline = "No hay conexión para descargar Chromium",
                detail = "La reproducción de YouTube usa un Chromium embebido. Esta copia de Cypher no lo trae incluido, así que hay que descargarlo una vez (unos 150 MB) en\n«$folder», y el equipo no pudo conectar con el servidor.",
                hint = "Comprueba la conexión (o el proxy) y pulsa Reintentar. Instalar Cypher desde el MSI o el ZIP portable evita esta descarga. Las pistas locales siguen funcionando. Detalle: $raw"
            )
            is java.io.IOException, is SecurityException -> YouTubeSetupIssue(
                headline = "No se pudo escribir la carpeta de Chromium",
                detail = "Cypher necesita descomprimir Chromium en\n«$folder». La descarga llegó o no se pudo guardar; un antivirus o una carpeta protegida producen este fallo.",
                hint = "Revisa el permiso de escritura de esa carpeta y las exclusiones del antivirus, y pulsa Reintentar. Detalle: $raw"
            )
            else -> YouTubeSetupIssue(
                headline = "Chromium no pudo arrancar",
                detail = "La reproducción de YouTube necesita el Chromium embebido y no se pudo iniciar en este equipo (carpeta: «$folder»).",
                hint = "Pulsa Reintentar. Si se repite, cierra otras ventanas de Cypher y vuelve a abrir la app. Detalle: $raw"
            )
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
