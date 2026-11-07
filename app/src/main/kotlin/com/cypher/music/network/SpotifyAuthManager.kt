package com.cypher.music.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cypher.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SpotifySession(
    val isLoggedIn: Boolean = false,
    val displayName: String? = null,
    val email: String? = null,
    val product: String? = null,
    val lastSyncTimestamp: Long = 0L
)

data class SpotifyAuthRequest(val url: String, val state: String)

/**
 * Spotify's Authorization Code flow with PKCE (no client secret), plus the pure helpers
 * that build the request. Redirect URI is the app's own scheme, which Spotify still
 * accepts for mobile clients.
 */
object SpotifyAuth {
    const val REDIRECT_URI = "cypher://spotify-callback"
    const val SCOPES =
        "user-read-private user-read-email playlist-read-private playlist-read-collaborative user-library-read"
    const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
    const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    const val API_BASE = "https://api.spotify.com/v1"

    private const val VERIFIER_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun createVerifier(random: SecureRandom = SecureRandom()): String = buildString {
        repeat(64) { append(VERIFIER_CHARS[random.nextInt(VERIFIER_CHARS.length)]) }
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun authorizeUrl(clientId: String, state: String, verifier: String): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return AUTHORIZE_ENDPOINT + "?" + listOf(
            "client_id=${enc(clientId)}",
            "response_type=code",
            "redirect_uri=${enc(REDIRECT_URI)}",
            "state=${enc(state)}",
            "scope=${enc(SCOPES)}",
            "code_challenge_method=S256",
            "code_challenge=${enc(challenge(verifier))}"
        ).joinToString("&")
    }
}

/**
 * Stores the Spotify session and keeps access tokens fresh. Tokens live in
 * EncryptedSharedPreferences, never in logs or in the repository.
 */
class SpotifyAuthManager(private val context: Context) {
    companion object {
        private const val TAG = "SpotifyAuthManager"
        private const val PREFS_NAME = "spotify_secure_account"
    }

    private val prefs: SharedPreferences = createPrefs()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<SpotifySession> = _session.asStateFlow()

    private val _clientId = MutableStateFlow(resolveClientId())
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private fun createPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "Encrypted prefs unavailable (${e.message}), using private prefs")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun resolveClientId(): String {
        val stored = prefs.getString("client_id", null)?.trim().orEmpty()
        if (stored.isNotEmpty()) return stored
        return BuildConfig.SPOTIFY_CLIENT_ID.trim()
    }

    fun setClientId(value: String) {
        prefs.edit().putString("client_id", value.trim()).apply()
        _clientId.value = resolveClientId()
    }

    private fun loadSession(): SpotifySession = SpotifySession(
        isLoggedIn = !prefs.getString("refresh_token", null).isNullOrBlank(),
        displayName = prefs.getString("display_name", null),
        email = prefs.getString("email", null),
        product = prefs.getString("product", null),
        lastSyncTimestamp = prefs.getLong("last_sync", 0L)
    )

    fun beginAuthorization(): Result<SpotifyAuthRequest> {
        val clientId = _clientId.value
        if (clientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Falta el Client ID de Spotify. Crea una app en developer.spotify.com, " +
                        "añade el redirect ${SpotifyAuth.REDIRECT_URI} y pégalo aquí."
                )
            )
        }
        val verifier = SpotifyAuth.createVerifier()
        val state = UUID.randomUUID().toString()
        prefs.edit()
            .putString("pkce_verifier", verifier)
            .putString("oauth_state", state)
            .apply()
        return Result.success(
            SpotifyAuthRequest(SpotifyAuth.authorizeUrl(clientId, state, verifier), state)
        )
    }

    suspend fun completeAuthorization(
        code: String,
        state: String?
    ): Result<SpotifySession> = withContext(Dispatchers.IO) {
        val expectedState = prefs.getString("oauth_state", null)
        val verifier = prefs.getString("pkce_verifier", null)
        if (code.isBlank() || verifier.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("La autorización de Spotify llegó incompleta. Inténtalo de nuevo.")
            )
        }
        if (!state.isNullOrBlank() && !expectedState.isNullOrBlank() && state != expectedState) {
            return@withContext Result.failure(
                IllegalStateException("La respuesta de Spotify no coincide con esta sesión.")
            )
        }

        try {
            val token = requestToken(
                FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", SpotifyAuth.REDIRECT_URI)
                    .add("client_id", _clientId.value)
                    .add("code_verifier", verifier)
                    .build()
            )
            storeTokens(token)
            prefs.edit().remove("pkce_verifier").remove("oauth_state").apply()
            val accessToken = token.optString("access_token")
            saveProfile(fetchProfile(accessToken))
            Result.success(_session.value)
        } catch (e: Exception) {
            Log.w(TAG, "completeAuthorization failed: ${e.message}")
            Result.failure(
                IllegalStateException(
                    e.message ?: "Spotify rechazó la autorización. Vuelve a intentarlo."
                )
            )
        }
    }

    /** Returns a valid access token, refreshing it when it is about to expire. */
    fun accessToken(forceRefresh: Boolean = false): String? {
        val token = prefs.getString("access_token", null)
        val expiresAt = prefs.getLong("expires_at", 0L)
        if (!forceRefresh && !token.isNullOrBlank() &&
            expiresAt - System.currentTimeMillis() > 60_000
        ) {
            return token
        }
        synchronized(this) {
            val current = prefs.getString("access_token", null)
            val currentExpiry = prefs.getLong("expires_at", 0L)
            if (!forceRefresh && !current.isNullOrBlank() &&
                currentExpiry - System.currentTimeMillis() > 60_000
            ) {
                return current
            }
            val refreshToken = prefs.getString("refresh_token", null) ?: return null
            return try {
                val refreshed = requestToken(
                    FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", refreshToken)
                        .add("client_id", _clientId.value)
                        .build()
                )
                storeTokens(refreshed)
                refreshed.optString("access_token")
            } catch (e: Exception) {
                Log.w(TAG, "Token refresh failed: ${e.message}")
                null
            }
        }
    }

    private fun requestToken(body: FormBody): JSONObject {
        val request = Request.Builder()
            .url(SpotifyAuth.TOKEN_ENDPOINT)
            .post(body)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val description = runCatching {
                    JSONObject(payload).optString("error_description")
                        .ifBlank { JSONObject(payload).optString("error") }
                }.getOrNull()
                throw IllegalStateException(
                    description?.takeIf { it.isNotBlank() }
                        ?: "Spotify respondió ${response.code} al autorizar."
                )
            }
            return JSONObject(payload)
        }
    }

    private fun storeTokens(token: JSONObject) {
        val expiresIn = token.optLong("expires_in", 3600L)
        val refresh = token.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: prefs.getString("refresh_token", null)
        prefs.edit()
            .putString("access_token", token.optString("access_token"))
            .putString("refresh_token", refresh)
            .putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000L)
            .apply()
    }

    private fun fetchProfile(accessToken: String): JSONObject {
        val request = Request.Builder()
            .url("${SpotifyAuth.API_BASE}/me")
            .header("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return JSONObject()
            }
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun saveProfile(profile: JSONObject) {
        prefs.edit()
            .putString("display_name", profile.optString("display_name").takeIf { it.isNotBlank() })
            .putString("email", profile.optString("email").takeIf { it.isNotBlank() })
            .putString("product", profile.optString("product").takeIf { it.isNotBlank() })
            .apply()
        _session.value = loadSession().copy(isLoggedIn = true)
    }

    fun markSynced() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_sync", now).apply()
        _session.value = _session.value.copy(lastSyncTimestamp = now)
    }

    fun logout() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .remove("display_name")
            .remove("email")
            .remove("product")
            .apply()
        _session.value = SpotifySession()
    }
}
