package io.github.whywood.deepdeep.data.update

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun parsesVersionsWithAndWithoutPrefix() {
        assertEquals(listOf(1, 0, 2), UpdatePolicy.parseVersion("1.0.2"))
        assertEquals(listOf(1, 2, 10), UpdatePolicy.parseVersion("v1.2.10"))
        assertNull(UpdatePolicy.parseVersion("1.0"))
        assertNull(UpdatePolicy.parseVersion("nightly"))
    }

    @Test
    fun comparesVersionsByNumber() {
        assertTrue(UpdatePolicy.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdatePolicy.isNewer("v1.1.0", "1.0.99"))
        assertFalse(UpdatePolicy.isNewer("1.0.2", "1.0.2"))
        assertFalse(UpdatePolicy.isNewer("1.0.1", "1.0.2"))
        assertEquals(0, UpdatePolicy.compareVersions("invalid", "1.0.2"))
    }

    @Test
    fun parsesSha256Digest() {
        val digest = "sha256:" + "a".repeat(64)
        assertEquals("a".repeat(64), UpdatePolicy.parseDigest(digest))
        assertEquals("a".repeat(64), UpdatePolicy.parseDigest(digest.uppercase()))
        assertNull(UpdatePolicy.parseDigest("md5:abc"))
        assertNull(UpdatePolicy.parseDigest(null))
    }

    @Test
    fun evaluatesNewerRelease() {
        val result = UpdatePolicy.evaluateLatest(
            latestJson(version = "1.0.3", assetName = "DeepDeep-1.0.3.apk"),
            currentVersion = "1.0.2",
        )
        val release = (result as UpdatePolicy.Latest.Newer).release
        assertEquals("v1.0.3", release.tag)
        assertEquals("1.0.3", release.versionName)
        assertEquals("更新内容", release.notes)
        assertEquals("https://example.com/DeepDeep-1.0.3.apk", release.apkUrl)
        assertEquals(3L, release.apkSize)
        assertEquals("b".repeat(64), release.sha256)
        assertEquals("https://github.com/example/releases/tag/v1.0.3", release.pageUrl)
    }

    @Test
    fun currentVersionIsNotAnUpdate() {
        val result = UpdatePolicy.evaluateLatest(
            latestJson(version = "1.0.2"),
            currentVersion = "1.0.2",
        )
        assertEquals(UpdatePolicy.Latest.Current, result)
    }

    @Test
    fun prereleaseAndDraftAreIgnored() {
        assertFalse(
            UpdatePolicy.evaluateLatest(latestJson(version = "1.0.3", prerelease = true), "1.0.2") is UpdatePolicy.Latest.Newer,
        )
        assertFalse(
            UpdatePolicy.evaluateLatest(latestJson(version = "1.0.3", draft = true), "1.0.2") is UpdatePolicy.Latest.Newer,
        )
    }

    @Test
    fun releaseWithoutApkHasNoAsset() {
        val json = """{"tag_name":"v1.0.3","body":"","assets":[{"name":"notes.txt","browser_download_url":"https://example.com/notes.txt","size":1}]}"""
        assertEquals(UpdatePolicy.Latest.MissingAsset, UpdatePolicy.evaluateLatest(json, "1.0.2"))
    }

    @Test
    fun invalidJsonOrTagIsInvalid() {
        assertEquals(UpdatePolicy.Latest.Invalid, UpdatePolicy.evaluateLatest("not-json", "1.0.2"))
        assertEquals(
            UpdatePolicy.Latest.Invalid,
            UpdatePolicy.evaluateLatest("""{"tag_name":"latest","assets":[]}""", "1.0.2"),
        )
    }

    @Test
    fun prefersAssetNamedAfterVersion() {
        val assets = JSONArray(
            """
            [
              {"name":"DeepDeep-other.apk","browser_download_url":"https://example.com/other.apk","size":1},
              {"name":"DeepDeep-1.0.3.apk","browser_download_url":"https://example.com/main.apk","size":2}
            ]
            """.trimIndent(),
        )
        assertEquals("https://example.com/main.apk", UpdatePolicy.chooseAsset(assets, "1.0.3")?.url)
    }

    @Test
    fun autoCheckRespectsThrottle() {
        val now = 10_000_000L
        assertTrue(UpdatePolicy.shouldAutoCheck(now, null, null, inFlight = false))
        assertFalse(UpdatePolicy.shouldAutoCheck(now, now - 60_000L, null, inFlight = false))
        assertTrue(UpdatePolicy.shouldAutoCheck(now, now - UpdatePolicy.CHECK_INTERVAL_MS, null, inFlight = false))
        assertFalse(UpdatePolicy.shouldAutoCheck(now, null, now - 60_000L, inFlight = false))
        assertTrue(UpdatePolicy.shouldAutoCheck(now, null, now - UpdatePolicy.RETRY_INTERVAL_MS, inFlight = false))
        assertFalse(UpdatePolicy.shouldAutoCheck(now, null, null, inFlight = true))
    }

    @Test
    fun verifiesDownloadByDigestWhenAvailable() {
        assertTrue(UpdatePolicy.verifyDownload(10, "abc", 10, "ABC"))
        assertFalse(UpdatePolicy.verifyDownload(10, "abc", 10, "def"))
        assertTrue(UpdatePolicy.verifyDownload(10, null, 10, null))
        assertFalse(UpdatePolicy.verifyDownload(9, null, 10, null))
        assertTrue(UpdatePolicy.verifyDownload(9, null, 0, null))
    }

    @Test
    fun acceptsOnlyOurPackageWithHigherVersionCode() {
        assertTrue(UpdatePolicy.acceptsArchive("io.github.whywood.deepdeep", 10003, "io.github.whywood.deepdeep", 10002))
        assertFalse(UpdatePolicy.acceptsArchive("io.github.whywood.deepdeep", 10002, "io.github.whywood.deepdeep", 10002))
        assertFalse(UpdatePolicy.acceptsArchive("other.app", 10003, "io.github.whywood.deepdeep", 10002))
        assertFalse(UpdatePolicy.acceptsArchive(null, 10003, "io.github.whywood.deepdeep", 10002))
    }

    @Test
    fun apkFileNameFollowsReleaseProtocol() {
        assertEquals("DeepDeep-1.0.3.apk", UpdatePolicy.apkFileName("1.0.3"))
    }

    private fun latestJson(
        version: String,
        assetName: String = "DeepDeep-$version.apk",
        prerelease: Boolean = false,
        draft: Boolean = false,
    ): String = """
        {
          "tag_name": "v$version",
          "body": "更新内容",
          "draft": $draft,
          "prerelease": $prerelease,
          "html_url": "https://github.com/example/releases/tag/v$version",
          "assets": [
            {
              "name": "$assetName",
              "browser_download_url": "https://example.com/$assetName",
              "size": 3,
              "digest": "sha256:${"b".repeat(64)}"
            }
          ]
        }
    """.trimIndent()
}
