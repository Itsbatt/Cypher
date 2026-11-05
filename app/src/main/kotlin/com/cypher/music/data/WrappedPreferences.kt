package com.cypher.music.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers which annual recaps have already been offered automatically, so the
 * full-screen Wrapped appears once per season instead of on every cold start.
 */
class WrappedPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "cypher_wrapped_prefs"
        private const val KEY_PREFIX = "wrapped_shown_"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasShown(year: Int): Boolean = prefs.getBoolean(key(year), false)

    fun markShown(year: Int) {
        prefs.edit().putBoolean(key(year), true).apply()
    }

    private fun key(year: Int): String = "$KEY_PREFIX$year"
}
