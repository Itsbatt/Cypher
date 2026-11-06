package com.cypher.music.update

import android.content.Context

/** Local state for update notices: which version was dismissed and when we last checked. */
class UpdatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("cypher_updates", Context.MODE_PRIVATE)

    var dismissedVersion: String?
        get() = prefs.getString(KEY_DISMISSED_VERSION, null)
        set(value) {
            prefs.edit().putString(KEY_DISMISSED_VERSION, value).apply()
        }

    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_CHECK_AT, value).apply()
        }

    private companion object {
        const val KEY_DISMISSED_VERSION = "dismissed_version"
        const val KEY_LAST_CHECK_AT = "last_check_at"
    }
}
