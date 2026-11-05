package com.cypher.music.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.util.Locale

/**
 * Persistence for the single local Cypher profile.
 *
 * Uses SharedPreferences (same lightweight approach as [ThemePreferencesManager])
 * so no new dependency or database migration is needed. The exposed [StateFlow]
 * makes the saved name propagate to every screen immediately.
 */
class UserProfileStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "cypher_user_prefs"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR_COLOR = "avatar_color"
        private const val DEFAULT_AVATAR_COLOR = 0xFF9D7BB0L // Cypher Lavender
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _user = MutableStateFlow(loadUser())
    val user: StateFlow<User> = _user.asStateFlow()

    /** True until the user has saved a name for the first time. */
    val needsOnboarding: Boolean
        get() = _user.value.displayName.isBlank()

    fun updateDisplayName(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return

        val updated = _user.value.copy(
            username = slugify(name),
            displayName = name
        )
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, updated.displayName)
            .putString(KEY_USERNAME, updated.username)
            .putLong(KEY_AVATAR_COLOR, updated.avatarColor)
            .apply()
        _user.value = updated
    }

    private fun loadUser(): User {
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty().trim()
        val storedUsername = prefs.getString(KEY_USERNAME, null)
        return User(
            username = storedUsername?.takeIf { it.isNotBlank() }
                ?: if (displayName.isBlank()) "personal" else slugify(displayName),
            displayName = displayName,
            avatarColor = prefs.getLong(KEY_AVATAR_COLOR, DEFAULT_AVATAR_COLOR)
        )
    }

    private fun slugify(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "personal" }
}
