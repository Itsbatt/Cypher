package com.cypher.music.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParsingTest {

    private val sampleJson = """
        [
          {
            "tag_name": "v1.0.2",
            "name": "Cypher 1.0.2",
            "body": "# Cypher 1.0.2\n\n- Mejoras de audio",
            "prerelease": false,
            "draft": false,
            "html_url": "https://github.com/Itsbatt/Cypher/releases/tag/v1.0.2",
            "assets": [
              {
                "name": "Cypher-v1.0.2-arm64-v8a-release.apk",
                "browser_download_url": "https://github.com/Itsbatt/Cypher/releases/download/v1.0.2/arm64.apk",
                "size": 60000000
              },
              {
                "name": "Cypher-v1.0.2-debug.apk",
                "browser_download_url": "https://github.com/Itsbatt/Cypher/releases/download/v1.0.2/debug.apk",
                "size": 173000000
              }
            ]
          },
          {
            "tag_name": "v1.0.3-beta.9",
            "name": "Beta 9",
            "body": "Notas beta",
            "prerelease": true,
            "draft": false,
            "html_url": "https://github.com/Itsbatt/Cypher/releases/tag/v1.0.3-beta.9",
            "assets": []
          },
          {
            "tag_name": "v1.0.3-beta.10",
            "name": "Beta 10",
            "body": "Notas beta 10",
            "prerelease": true,
            "draft": false,
            "html_url": "https://github.com/Itsbatt/Cypher/releases/tag/v1.0.3-beta.10",
            "assets": []
          },
          {
            "tag_name": "v9.9.9",
            "name": "Draft",
            "body": "",
            "prerelease": false,
            "draft": true,
            "html_url": "",
            "assets": []
          }
        ]
    """.trimIndent()

    @Test
    fun `parses stable and prerelease entries and skips drafts`() {
        val releases = GitHubUpdateClient.parseReleases(sampleJson)
        assertEquals(3, releases.size)
        assertTrue(releases.none { it.versionName == "9.9.9" })
        assertEquals("1.0.3-beta.10", releases.maxByOrNull { it.version }?.versionName)
    }

    @Test
    fun `uses first meaningful release note line`() {
        val stable = GitHubUpdateClient.parseReleases(sampleJson).first { !it.prerelease }
        assertEquals("Cypher 1.0.2", stable.summary)
    }

    @Test
    fun `selects the matching arm64 apk and ignores debug builds`() {
        val assets = listOf(
            ReleaseAsset("Cypher-v1.0.2-release.apk", "https://example.com/universal.apk", 1),
            ReleaseAsset("Cypher-v1.0.2-arm64-v8a-release.apk", "https://example.com/arm64.apk", 1),
            ReleaseAsset("Cypher-v1.0.2-debug.apk", "https://example.com/debug.apk", 1)
        )
        val selected = UpdateAssetSelector.select(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("Cypher-v1.0.2-arm64-v8a-release.apk", selected?.name)
    }

    @Test
    fun `rejects apks for other architectures and falls back to universal`() {
        val foreign = listOf(ReleaseAsset("app-x86_64-release.apk", "https://example.com/x.apk", 1))
        assertNull(UpdateAssetSelector.select(foreign, listOf("arm64-v8a")))

        val universal = listOf(ReleaseAsset("Cypher-v1.0.2-release.apk", "https://example.com/u.apk", 1))
        assertEquals("Cypher-v1.0.2-release.apk", UpdateAssetSelector.select(universal, listOf("arm64-v8a"))?.name)
    }

    @Test
    fun `extracts checksum by filename and single hash files`() {
        val hash = "a".repeat(64)
        val named = "$hash  Cypher-v1.0.2-arm64-v8a-release.apk\n" + "b".repeat(64) + "  other.apk"
        assertEquals(hash, UpdateInstaller.parseChecksum(named, "Cypher-v1.0.2-arm64-v8a-release.apk"))
        assertEquals(hash, UpdateInstaller.parseChecksum(hash, "Cypher-v1.0.2-arm64-v8a-release.apk"))
        assertNull(UpdateInstaller.parseChecksum(named, "missing.apk"))
    }
}
