package com.cypher.music.data

import android.content.Context

/**
 * Records that the user has read the legal notice shown on first launch, so it is
 * not repeated on every start. It can always be reopened from Settings.
 */
class LegalNoticeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasAccepted(): Boolean = prefs.getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_VERSION

    fun accept() {
        prefs.edit().putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION).apply()
    }

    companion object {
        private const val PREFS = "cypher_legal_notice"
        private const val KEY_ACCEPTED_VERSION = "accepted_version"

        /** Bump when the wording changes in a way the user must read again. */
        const val CURRENT_VERSION = 1
    }
}
