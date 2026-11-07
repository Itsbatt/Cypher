package com.cypher.music.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledBuildTest {

    @Test
    fun `same digest means the installed build is the published one`() {
        assertFalse(InstalledBuild.isDifferentBuild("abc123", "abc123"))
        assertFalse(InstalledBuild.isDifferentBuild("ABC123", "abc123"))
    }

    @Test
    fun `a rebuilt apk with the same version has a different digest`() {
        assertTrue(InstalledBuild.isDifferentBuild("abc123", "def456"))
    }

    @Test
    fun `missing data never reports a rebuild`() {
        assertFalse(InstalledBuild.isDifferentBuild(null, "def456"))
        assertFalse(InstalledBuild.isDifferentBuild("abc123", null))
        assertFalse(InstalledBuild.isDifferentBuild("", ""))
    }
}
