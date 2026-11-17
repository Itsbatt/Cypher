package com.cypher.desktop.importers

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Google consent for reading the user's YouTube playlists.
 *
 * This is **not** a Cypher account: there is no sign-in at startup, no session stored
 * on disk and no library copy anywhere. The token lives in memory for the duration of
 * the import and is dropped when the app closes or the user disconnects.
 *
 * Flow: installed-app OAuth (PKCE + loopback redirect), which is what Google documents
 * for desktop clients. No client secret is embedded; Google's Desktop client type may
 * still require one at the token endpoint, and when it does the user supplies it and it
 * is written outside the project.
 */
class YouTubeOAuth(
    private val clientId: String = DEFAULT_CLIENT_ID,
    private val secret: String? = desktopClientSecret(),
    // Overridable so the exchange can be tested without touching Google.
    private val tokenEndpoint: String =
        System.getenv("CYPHER_GOOGLE_TOKEN_ENDPOINT")?.takeIf { it.isNotBlank() } ?: TOKEN_ENDPOINT,
    private val authEndpoint: String = AUTH_ENDPOINT,
    /** Opening the system browser is injected so the loopback flow can be tested. */
    private val openBrowser: (String) -> Unit = ::openInSystemBrowser
) {
    data class Tokens(
        /** Bearer token for the YouTube Data API. Kept in memory only. */
        val accessToken: String,
        val expiresAt: Long,
        val email: String? = null,
        val subject: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun signIn(timeout: Duration = Duration.ofMinutes(3)): Tokens {
        val verifier = randomToken(64)
        val challenge = base64Url(sha256(verifier))
        val state = randomToken(24)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val redirect = "http://127.0.0.1:${server.address.port}/oauth2callback"
        val result = CompletableFuture<String>()

        server.createContext("/oauth2callback") { exchange ->
            val params = parseQuery(exchange.requestURI.rawQuery)
            val body = when {
                params["state"] != state -> "Estado inválido. Puedes cerrar esta pestaña."
                params["error"] != null -> "Google devolvió: ${params["error"]}"
                params["code"] == null -> "Falta el código de autorización."
                else -> {
                    result.complete(params["code"])
                    "Listo. Cypher Desktop ya puede leer tus playlists; puedes cerrar esta pestaña."
                }
            }
            val html = "<html><body style='font-family:sans-serif;background:#0D0B11;color:#F7F5FA'>" +
                "<p style='padding:40px'>$body</p></body></html>"
            val bytes: ByteArray = html.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { stream -> stream.write(bytes) }
        }
        server.start()
        try {
            val url = "$authEndpoint?" + listOf(
                "client_id" to clientId,
                "redirect_uri" to redirect,
                "response_type" to "code",
                "scope" to SCOPE,
                "include_granted_scopes" to "true",
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                "state" to state,
                "prompt" to "select_account"
            ).joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }

            openBrowser(url)

            val code = result.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            return exchangeCode(code, verifier, redirect)
        } finally {
            server.stop(0)
        }
    }

    /** Exchanges the authorization code. Visible to tests via the stub endpoint. */
    internal fun exchangeCode(code: String, verifier: String, redirect: String): Tokens {
        val form = buildList {
            add("client_id" to clientId)
            add("code" to code)
            add("code_verifier" to verifier)
            add("grant_type" to "authorization_code")
            add("redirect_uri" to redirect)
            secret?.let { add("client_secret" to it) }
        }.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(oauthFailureMessage(response.statusCode(), response.body().orEmpty()))
        }
        val body = json.parseToJsonElement(response.body()).jsonObject
        val accessToken = body["access_token"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Google no devolvió un token de acceso.")
        val expiresIn = body["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        val claims = body["id_token"]?.jsonPrimitive?.content?.let(::decodeClaims).orEmpty()
        return Tokens(
            accessToken = accessToken,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
            email = claims["email"],
            subject = claims["sub"],
        )
    }

    private fun decodeClaims(idToken: String): Map<String, String> = runCatching {
        val payload = idToken.split('.').getOrNull(1) ?: return emptyMap()
        val decoded = String(Base64.getUrlDecoder().decode(payload))
        json.parseToJsonElement(decoded).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else
                URLDecoder.decode(part.take(index), "UTF-8") to
                    URLDecoder.decode(part.drop(index + 1), "UTF-8")
        }.toMap()

    private fun randomToken(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return base64Url(buffer)
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        /** Read-only access to the user's playlists; the narrowest scope that allows it. */
        const val SCOPE = "openid email https://www.googleapis.com/auth/youtube.readonly"

        /**
         * Where the Desktop client secret comes from: the environment first, then the
         * file the app writes on request. It is never part of the repository, never
         * logged and never shown again once saved.
         */
        fun secretFile(): java.io.File =
            com.cypher.desktop.core.Storage.dataDir.resolve("desktop-client-secret.txt").toFile()

        fun desktopClientSecret(): String? {
            val fromEnv = System.getenv("CYPHER_DESKTOP_CLIENT_SECRET")?.trim().orEmpty()
            if (fromEnv.isNotEmpty()) return fromEnv
            val file = secretFile()
            return runCatching { file.readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
        }

        fun saveDesktopClientSecret(value: String): Boolean {
            val clean = value.trim()
            if (clean.isEmpty()) return false
            val file = secretFile()
            file.parentFile?.mkdirs()
            file.writeText(clean)
            runCatching {
                val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                java.nio.file.Files.setPosixFilePermissions(file.toPath(), perms)
            }
            return true
        }

        /** The exact Google answer plus the action that fixes it. */
        internal fun oauthFailureMessage(status: Int, raw: String): String {
            val code = runCatching {
                val body = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
                listOfNotNull(
                    body["error"]?.jsonPrimitive?.content,
                    body["error_description"]?.jsonPrimitive?.content
                ).joinToString(": ").ifBlank { null }
            }.getOrNull()
            val hint = when {
                raw.contains("client_secret", ignoreCase = true) ->
                    "Google exige el client secret del cliente OAuth Desktop en el intercambio. " +
                        "Pega el secreto del cliente Desktop en Ajustes y vuelve a conectar; " +
                        "también puedes definirlo en CYPHER_DESKTOP_CLIENT_SECRET."
                raw.contains("invalid_grant", ignoreCase = true) ->
                    "El código de autorización ya se usó o caducó. Vuelve a conectar la cuenta."
                raw.contains("redirect_uri_mismatch", ignoreCase = true) ->
                    "El cliente OAuth no admite la redirección de loopback de este equipo."
                raw.contains("invalid_client", ignoreCase = true) ->
                    "Google no reconoce el cliente Desktop configurado en Cypher."
                raw.contains("access_denied", ignoreCase = true) ->
                    "No se concedió el permiso de lectura de YouTube."
                else -> "Revisa el cliente OAuth de Desktop y su configuración."
            }
            return "Google rechazó el intercambio ($status) [${code ?: raw.take(160)}]. $hint"
        }

        private fun openInSystemBrowser(url: String) {
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(URI.create(url))
                    return
                } catch (_: Exception) {
                    // Falls through to the printable URL below.
                }
            }
            throw IllegalStateException("No se pudo abrir el navegador. Abre esta URL manualmente: $url")
        }

        const val DEFAULT_CLIENT_ID =
            "764777457932-goan61rtqc8g3u6t7o7if2pdm6pkkc97.apps.googleusercontent.com"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }
}
