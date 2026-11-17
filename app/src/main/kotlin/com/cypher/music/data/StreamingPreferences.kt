package com.cypher.music.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Preferences for alternative streaming providers (Piped / Invidious instances).
 *
 * When yt-dlp or direct InnerTube access encounters network blocks, HTTP 403, or SABR
 * limitations, Cypher can fall back to a self-hosted or public Piped / Invidious instance.
 */
class StreamingPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _instanceUrl = sharedProxyInstanceUrl
    val instanceUrl: StateFlow<String> = _instanceUrl.asStateFlow()

    private val _proxyEnabled = sharedProxyEnabled
    val proxyEnabled: StateFlow<Boolean> = _proxyEnabled.asStateFlow()

    init {
        if (!initialized) {
            sharedProxyInstanceUrl.value = prefs.getString(KEY_INSTANCE_URL, "") ?: ""
            sharedProxyEnabled.value = prefs.getBoolean(KEY_PROXY_ENABLED, false)
            initialized = true
        }
    }

    fun isProxyEnabled(): Boolean = _proxyEnabled.value

    fun getEffectiveInstanceUrl(): String? {
        if (!_proxyEnabled.value) return null
        val url = _instanceUrl.value.trim()
        return if (url.isNotBlank()) url else null
    }

    fun setProxyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROXY_ENABLED, enabled).apply()
        _proxyEnabled.value = enabled
    }

    fun setInstanceUrl(url: String) {
        val trimmed = url.trim()
        prefs.edit().putString(KEY_INSTANCE_URL, trimmed).apply()
        _instanceUrl.value = trimmed
    }

    fun exportJson(): String = JSONObject().apply {
        put("proxyEnabled", _proxyEnabled.value)
        put("instanceUrl", _instanceUrl.value)
    }.toString()

    fun importJson(json: String) {
        runCatching {
            val root = JSONObject(json)
            setProxyEnabled(root.optBoolean("proxyEnabled", false))
            setInstanceUrl(root.optString("instanceUrl", ""))
        }
    }

    companion object {
        const val PREFS = "cypher_streaming_prefs"
        private const val KEY_PROXY_ENABLED = "proxy_enabled"
        private const val KEY_INSTANCE_URL = "instance_url"

        private val sharedProxyInstanceUrl = MutableStateFlow("")
        private val sharedProxyEnabled = MutableStateFlow(false)

        @Volatile
        private var initialized = false

        /**
         * Global accessor for StreamResolvers without needing to carry a Context into
         * resolver singletons.
         */
        fun getInstanceUrl(): String? {
            if (!sharedProxyEnabled.value) return null
            val url = sharedProxyInstanceUrl.value.trim()
            return if (url.isNotBlank()) url else null
        }
    }
}
