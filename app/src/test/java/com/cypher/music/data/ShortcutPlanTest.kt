package com.cypher.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutPlanTest {

    @Test
    fun `only the declared actions are shortcuts`() {
        assertEquals(
            ShortcutAction.PLAY_FAVORITES,
            ShortcutAction.fromIntentAction("com.cypher.music.action.PLAY_FAVORITES")
        )
        assertEquals(
            ShortcutAction.CONTINUE_SESSION,
            ShortcutAction.fromIntentAction("com.cypher.music.action.CONTINUE_SESSION")
        )
        assertNull(ShortcutAction.fromIntentAction("android.intent.action.VIEW"))
        assertNull(ShortcutAction.fromIntentAction("com.cypher.music.action.PLAY_FAVORITES "))
        assertNull(ShortcutAction.fromIntentAction(null))
    }

    @Test
    fun `favorites shortcut plays when there are favorites`() {
        assertEquals(
            ShortcutOutcome.STARTED_FAVORITES,
            resolveShortcut(ShortcutAction.PLAY_FAVORITES, hasFavorites = true, hasSession = false)
        )
    }

    @Test
    fun `favorites shortcut reports an empty list instead of playing nothing`() {
        assertEquals(
            ShortcutOutcome.NO_FAVORITES,
            resolveShortcut(ShortcutAction.PLAY_FAVORITES, hasFavorites = false, hasSession = true)
        )
        assertEquals(
            "Aún no tienes favoritas: marca algunas con el corazón",
            shortcutOutcomeMessage(ShortcutOutcome.NO_FAVORITES)
        )
    }

    @Test
    fun `continue shortcut needs a saved session`() {
        assertEquals(
            ShortcutOutcome.STARTED_SESSION,
            resolveShortcut(ShortcutAction.CONTINUE_SESSION, hasFavorites = false, hasSession = true)
        )
        assertEquals(
            ShortcutOutcome.NO_SESSION,
            resolveShortcut(ShortcutAction.CONTINUE_SESSION, hasFavorites = true, hasSession = false)
        )
        assertEquals(
            "No hay ninguna sesión guardada todavía",
            shortcutOutcomeMessage(ShortcutOutcome.NO_SESSION)
        )
    }

    @Test
    fun `starting outcomes show no message`() {
        assertNull(shortcutOutcomeMessage(ShortcutOutcome.STARTED_FAVORITES))
        assertNull(shortcutOutcomeMessage(ShortcutOutcome.STARTED_SESSION))
    }
}
