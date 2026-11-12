package io.github.whywood.deepdeep.data.update

import org.json.JSONArray
import org.json.JSONObject

data class UpdateRelease(
    val tag: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?,
    val pageUrl: String,
)

enum class UpdateFailure {
    NETWORK,
    SERVICE,
    NO_RELEASE,
    DOWNLOAD,
    VERIFY,
    ARCHIVE,
    INSTALL,
}

object UpdatePolicy {
    const val RELEASE_REPO = "Sadami3066/DeepDeep"
    const val LATEST_URL = "https://api.github.com/repos/$RELEASE_REPO/releases/latest"
    const val RELEASE_PAGE_URL = "https://github.com/$RELEASE_REPO/releases/latest"
    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val RETRY_INTERVAL_MS = 15L * 60L * 1000L
    const val PAGE_REFRESH_INTERVAL_MS = 10L * 60L * 1000L

    private const val APK_PREFIX = "DeepDeep-"
    private const val USER_AGENT = "DeepDeep-Android"
    private val VERSION = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)$")
    private val SHA256 = Regex("^sha256:([0-9a-fA-F]{64})$", RegexOption.IGNORE_CASE)

    data class Asset(val name: String, val url: String, val size: Long, val sha256: String?)

    sealed interface Latest {
        data class Newer(val release: UpdateRelease) : Latest
        data object Current : Latest
        data object MissingAsset : Latest
        data object Invalid : Latest
    }

    fun requestHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to USER_AGENT,
    )

    fun parseVersion(value: String): List<Int>? {
        val match = VERSION.matchEntire(value.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toInt() }
    }

    fun compareVersions(a: String, b: String): Int {
        val left = parseVersion(a) ?: return 0
        val right = parseVersion(b) ?: return 0
        for (index in left.indices) {
            val result = left[index].compareTo(right[index])
            if (result != 0) return result
        }
        return 0
    }

    fun isNewer(version: String, current: String): Boolean = compareVersions(version, current) > 0

    fun parseDigest(raw: String?): String? {
        val match = SHA256.matchEntire(raw?.trim().orEmpty()) ?: return null
        return match.groupValues[1].lowercase()
    }

    fun evaluateLatest(json: String, currentVersion: String): Latest {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return Latest.Invalid
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return Latest.Current
        val tag = root.optString("tag_name").trim()
        if (parseVersion(tag) == null) return Latest.Invalid
        val versionName = tag.removePrefix("v").removePrefix("V")
        if (!isNewer(versionName, currentVersion)) return Latest.Current
        val asset = chooseAsset(root.optJSONArray("assets"), versionName) ?: return Latest.MissingAsset
        return Latest.Newer(
            UpdateRelease(
                tag = tag,
                versionName = versionName,
                notes = root.optString("body").trim(),
                apkUrl = asset.url,
                apkSize = asset.size,
                sha256 = asset.sha256,
                pageUrl = root.optString("html_url").takeIf { it.isNotBlank() } ?: RELEASE_PAGE_URL,
            ),
        )
    }

    fun chooseAsset(assets: JSONArray?, versionName: String): Asset? {
        val candidates = buildList {
            for (index in 0 until (assets?.length() ?: 0)) {
                val item = assets?.optJSONObject(index) ?: continue
                val name = item.optString("name")
                val url = item.optString("browser_download_url")
                if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
                add(Asset(name, url, item.optLong("size"), parseDigest(item.optString("digest"))))
            }
        }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.name.equals(apkFileName(versionName), ignoreCase = true) }
            ?: candidates.first()
    }

    fun shouldAutoCheck(now: Long, lastSuccessAt: Long?, lastAttemptAt: Long?, inFlight: Boolean): Boolean {
        if (inFlight) return false
        if (lastSuccessAt != null && now - lastSuccessAt < CHECK_INTERVAL_MS) return false
        if (lastAttemptAt != null && now - lastAttemptAt < RETRY_INTERVAL_MS) return false
        return true
    }

    fun verifyDownload(
        actualSize: Long,
        actualSha256: String?,
        expectedSize: Long,
        expectedSha256: String?,
    ): Boolean {
        if (expectedSha256 != null) {
            return actualSha256?.equals(expectedSha256, ignoreCase = true) == true
        }
        return expectedSize <= 0L || actualSize == expectedSize
    }

    fun acceptsArchive(
        archivePackage: String?,
        archiveVersionCode: Long?,
        ourPackage: String,
        installedVersionCode: Long,
    ): Boolean = archivePackage == ourPackage &&
        archiveVersionCode != null &&
        archiveVersionCode > installedVersionCode

    fun apkFileName(versionName: String): String = "$APK_PREFIX$versionName.apk"
}
