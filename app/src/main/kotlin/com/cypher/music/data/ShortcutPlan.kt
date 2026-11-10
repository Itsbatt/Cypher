package com.cypher.music.data

/**
 * The two launcher shortcuts declared in res/xml/shortcuts.xml. The action names
 * live here so the manifest, the launcher handler and the tests share one source
 * of truth instead of three string literals.
 */
enum class ShortcutAction(val intentAction: String) {
    PLAY_FAVORITES("com.cypher.music.action.PLAY_FAVORITES"),
    CONTINUE_SESSION("com.cypher.music.action.CONTINUE_SESSION");

    companion object {
        fun fromIntentAction(action: String?): ShortcutAction? =
            entries.firstOrNull { it.intentAction == action }
    }
}

/** What happened when a shortcut was used, so the UI can explain the quiet cases. */
enum class ShortcutOutcome {
    /** Favourites queue started. */
    STARTED_FAVORITES,

    /** The saved session resumed (or kept playing). */
    STARTED_SESSION,

    /** No favourites yet: the app opens without playing anything. */
    NO_FAVORITES,

    /** No saved session: the app opens without playing anything. */
    NO_SESSION
}

/**
 * Decides what a shortcut should do from the state it finds. Kept pure so the
 * "nothing to play" paths are covered by unit tests instead of only by hand.
 */
fun resolveShortcut(
    action: ShortcutAction,
    hasFavorites: Boolean,
    hasSession: Boolean
): ShortcutOutcome = when (action) {
    ShortcutAction.PLAY_FAVORITES ->
        if (hasFavorites) ShortcutOutcome.STARTED_FAVORITES else ShortcutOutcome.NO_FAVORITES

    ShortcutAction.CONTINUE_SESSION ->
        if (hasSession) ShortcutOutcome.STARTED_SESSION else ShortcutOutcome.NO_SESSION
}

/** Message shown when a shortcut could not start playback. */
fun shortcutOutcomeMessage(outcome: ShortcutOutcome): String? = when (outcome) {
    ShortcutOutcome.NO_FAVORITES -> "Aún no tienes favoritas: marca algunas con el corazón"
    ShortcutOutcome.NO_SESSION -> "No hay ninguna sesión guardada todavía"
    else -> null
}
