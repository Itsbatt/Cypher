package com.cypher.desktop.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdaterTest {

    @Test
    fun `isNewerVersion correctly compares semantic versions`() {
        // Equal versions
        assertFalse(DesktopUpdater.isNewerVersion("1.1.1", "1.1.1"))
        assertFalse(DesktopUpdater.isNewerVersion("1.0.0", "1.0.0"))

        // Older versions
        assertFalse(DesktopUpdater.isNewerVersion("1.1.1", "1.1.0"))
        assertFalse(DesktopUpdater.isNewerVersion("1.1.1", "1.0.9"))
        assertFalse(DesktopUpdater.isNewerVersion("2.0.0", "1.9.9"))

        // Newer versions
        assertTrue(DesktopUpdater.isNewerVersion("1.1.1", "1.1.2"))
        assertTrue(DesktopUpdater.isNewerVersion("1.1.1", "1.2.0"))
        assertTrue(DesktopUpdater.isNewerVersion("1.1.1", "2.0.0"))
        assertTrue(DesktopUpdater.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(DesktopUpdater.isNewerVersion("1.1", "1.1.1"))
    }

    @Test
    fun `portable mode inhibits auto update`() {
        val originalProp = System.getProperty("cypher.portable")
        try {
            System.setProperty("cypher.portable", "true")
            assertTrue(Storage.isPortable, "Storage should report portable mode")
            assertFalse(DesktopUpdater.isMsiInstalled(), "MSI updates must be disabled in portable mode")
        } finally {
            if (originalProp != null) {
                System.setProperty("cypher.portable", originalProp)
            } else {
                System.clearProperty("cypher.portable")
            }
        }
    }
}
