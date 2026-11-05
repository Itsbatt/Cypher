package com.cypher.music.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class YouTubeAccountSession(
    val isLoggedIn: Boolean = false,
    val accountEmail: String? = null,
    val cookie: String? = null,
    val sapisid: String? = null,
    val lastSyncTimestamp: Long = 0L
)

class YouTubeAccountManager(private val context: Context) {
    companion object {
        private const val TAG = "YouTubeAccountManager"

        fun extractCookieValue(cookies: String, key: String): String? {
            val parts = cookies.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.startsWith("$key=")) {
                    return trimmed.substring(key.length + 1).trim()
                }
            }
            return null
        }

        fun mergeCookies(vararg cookieStrings: String?): String {
            val map = linkedMapOf<String, String>()
            for (cs in cookieStrings) {
                if (cs.isNullOrBlank()) continue
                val parts = cs.split(";")
                for (part in parts) {
                    val trimmed = part.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx > 0) {
                        val k = trimmed.substring(0, eqIdx).trim()
                        val v = trimmed.substring(eqIdx + 1).trim()
                        map[k] = v
                    }
                }
            }
            return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        fun injectConsentCookies() {
            try {
                val cm = CookieManager.getInstance()
                cm.setCookie("https://youtube.com", "SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; domain=.youtube.com; path=/")
                cm.setCookie("https://music.youtube.com", "SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; domain=.youtube.com; path=/")
                cm.setCookie("https://www.youtube.com", "SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; domain=.youtube.com; path=/")
                cm.setCookie("https://youtube.com", "CONSENT=YES+; domain=.youtube.com; path=/")
                cm.setCookie("https://music.youtube.com", "CONSENT=YES+; domain=.youtube.com; path=/")
                cm.flush()
            } catch (e: Exception) {
                // Ignore
            }
        }

        fun collectAllYouTubeCookies(): String {
            val cm = CookieManager.getInstance()
            injectConsentCookies()
            val accounts = cm.getCookie("https://accounts.google.com")
            val mYt = cm.getCookie("https://m.youtube.com")
            val wwwYt = cm.getCookie("https://www.youtube.com")
            val yt = cm.getCookie("https://youtube.com")
            val music = cm.getCookie("https://music.youtube.com")
            // Accounts cookies must be passed first so YouTube specific cookies (HSID, SSID, SIDCC) overwrite them!
            var merged = mergeCookies(accounts, mYt, wwwYt, yt, music)
            if (!merged.contains("SOCS=")) {
                merged += "; SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; CONSENT=YES+"
            }
            return merged
        }
    }

    private var activeUserId: String = "default"
    private var prefs: SharedPreferences = createEncryptedPrefs(activeUserId)

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<YouTubeAccountSession> = _session.asStateFlow()

    private fun createEncryptedPrefs(userId: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val securePrefs = EncryptedSharedPreferences.create(
                context,
                "yt_secure_account_$userId",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Migrate legacy plain text prefs if exists
            val legacyPrefs = context.getSharedPreferences("yt_account_$userId", Context.MODE_PRIVATE)
            val legacyCookie = legacyPrefs.getString("cookie", null)
            if (!legacyCookie.isNullOrBlank() && !securePrefs.contains("cookie")) {
                securePrefs.edit()
                    .putString("cookie", legacyCookie)
                    .putString("sapisid", legacyPrefs.getString("sapisid", null))
                    .putString("email", legacyPrefs.getString("email", null))
                    .putLong("last_sync", legacyPrefs.getLong("last_sync", 0L))
                    .apply()
                legacyPrefs.edit().clear().apply()
                Log.d(TAG, "Migrated legacy YouTube account credentials to EncryptedSharedPreferences for user $userId")
            }
            securePrefs
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences initialization failed (${e.message}), using fallback private prefs", e)
            context.getSharedPreferences("yt_account_$userId", Context.MODE_PRIVATE)
        }
    }

    fun setUserId(userId: String) {
        activeUserId = userId.trim().lowercase().ifEmpty { "default" }
        prefs = createEncryptedPrefs(activeUserId)
        _session.value = loadSession()
    }

    private fun loadSession(): YouTubeAccountSession {
        val cookie = prefs.getString("cookie", null)
        val sapisid = prefs.getString("sapisid", null)
        val email = prefs.getString("email", null)
        val syncTime = prefs.getLong("last_sync", 0L)
        val isLoggedIn = !cookie.isNullOrBlank() && !sapisid.isNullOrBlank()
        return YouTubeAccountSession(
            isLoggedIn = isLoggedIn,
            accountEmail = email,
            cookie = cookie,
            sapisid = sapisid,
            lastSyncTimestamp = syncTime
        )
    }

    fun saveCookies(rawCookie: String, accountEmail: String? = null) {
        var cleanCookie = rawCookie
        if (!cleanCookie.contains("SOCS=")) {
            cleanCookie = "$cleanCookie; SOCS=CAESEwgDEgk2OTg1MjY2NzMaAmVzIAEaBgiA_LuuBg; CONSENT=YES+"
        }

        val sapisid = extractCookieValue(cleanCookie, "SAPISID")
            ?: extractCookieValue(cleanCookie, "__Secure-3PAPISID")
            ?: extractCookieValue(cleanCookie, "__Secure-1PAPISID")
            ?: extractCookieValue(cleanCookie, "APISID")

        prefs.edit()
            .putString("cookie", cleanCookie)
            .putString("sapisid", sapisid)
            .putString("email", accountEmail ?: "Cuenta de Google vinculada")
            .apply()

        _session.value = YouTubeAccountSession(
            isLoggedIn = !sapisid.isNullOrBlank(),
            accountEmail = accountEmail ?: "Cuenta de Google vinculada",
            cookie = cleanCookie,
            sapisid = sapisid,
            lastSyncTimestamp = prefs.getLong("last_sync", 0L)
        )
    }

    fun updateSyncTimestamp() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_sync", now).apply()
        _session.value = _session.value.copy(lastSyncTimestamp = now)
    }

    fun logout() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            // Ignore
        }
        prefs.edit().clear().apply()
        _session.value = YouTubeAccountSession()
    }

    fun getInnerTubeAuthHeader(): String? {
        val sapisid = _session.value.sapisid ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val payload = "$timestamp $sapisid https://music.youtube.com"
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }
}
