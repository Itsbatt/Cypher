package com.cypher.music.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cypher.music.BuildConfig

/**
 * Keeps the linked account and its short-lived ID token.
 *
 * Tokens live in [EncryptedSharedPreferences] (AES256-SIV keys / AES256-GCM values,
 * master key in the Android Keystore). If the encrypted store cannot be opened on a
 * broken Keystore the session stays in memory for the process only instead of being
 * written in clear text.
 */
class SessionStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.onFailure { Log.w(TAG, "Encrypted session store unavailable: ${it.message}") }
        .getOrNull()

    private val persistenceAvailable: Boolean get() = BuildConfig.DEBUG || prefs != null

    // Process-only fallback, never written to disk.
    private var memoryAccount: CloudAccount? = null
    private var memoryToken: String? = null
    private var memoryOfferAt: Long = 0L
    private var memoryDismissAt: Long = 0L

    fun saveSession(account: CloudAccount, idToken: String) {
        memoryAccount = account
        memoryToken = idToken
        prefs?.edit()
            ?.putString(KEY_USER_ID, account.userId)
            ?.putString(KEY_EMAIL, account.email)
            ?.putString(KEY_NAME, account.displayName)
            ?.putString(KEY_PHOTO, account.photoUrl ?: "")
            ?.putString(KEY_TOKEN, idToken)
            ?.putLong(KEY_TOKEN_AT, System.currentTimeMillis())
            ?.apply()
    }

    fun account(): CloudAccount? {
        memoryAccount?.let { return it }
        val stored = prefs?.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return CloudAccount(
            userId = stored,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            displayName = prefs.getString(KEY_NAME, "").orEmpty().ifBlank { stored },
            photoUrl = prefs.getString(KEY_PHOTO, null)?.takeIf { it.isNotBlank() }
        ).also { memoryAccount = it }
    }

    fun idToken(): String? = memoryToken ?: prefs?.getString(KEY_TOKEN, null)?.also { memoryToken = it }

    /** ID tokens are short-lived; the engine asks Google again when this is stale. */
    fun tokenAgeMs(now: Long = System.currentTimeMillis()): Long {
        val at = prefs?.getLong(KEY_TOKEN_AT, 0L) ?: 0L
        return if (at == 0L) Long.MAX_VALUE else now - at
    }

    fun clearSession() {
        memoryAccount = null
        memoryToken = null
        prefs?.edit()
            ?.remove(KEY_USER_ID)
            ?.remove(KEY_EMAIL)
            ?.remove(KEY_NAME)
            ?.remove(KEY_PHOTO)
            ?.remove(KEY_TOKEN)
            ?.remove(KEY_TOKEN_AT)
            ?.apply()
    }

    fun markOfferShown(now: Long = System.currentTimeMillis()) {
        memoryOfferAt = now
        prefs?.edit()?.putLong(KEY_OFFER_AT, now)?.apply()
    }

    fun lastOfferAt(): Long? =
        (if (memoryOfferAt > 0L) memoryOfferAt else prefs?.getLong(KEY_OFFER_AT, 0L) ?: 0L)
            .takeIf { it > 0L }

    fun markDismissed(now: Long = System.currentTimeMillis()) {
        memoryDismissAt = now
        prefs?.edit()?.putLong(KEY_DISMISS_AT, now)?.apply()
    }

    fun lastDismissedAt(): Long? =
        (if (memoryDismissAt > 0L) memoryDismissAt else prefs?.getLong(KEY_DISMISS_AT, 0L) ?: 0L)
            .takeIf { it > 0L }

    @Suppress("unused")
    fun isPersistent(): Boolean = persistenceAvailable

    private companion object {
        const val TAG = "SessionStore"
        const val FILE_NAME = "cypher_cloud_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "display_name"
        const val KEY_PHOTO = "photo_url"
        const val KEY_TOKEN = "id_token"
        const val KEY_TOKEN_AT = "token_saved_at"
        const val KEY_OFFER_AT = "link_offer_at"
        const val KEY_DISMISS_AT = "link_dismissed_at"
    }
}
