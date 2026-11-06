package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class FakePluginFetcher : PluginFetcher {
    val texts = LinkedHashMap<String, String>()
    val packages = LinkedHashMap<String, ByteArray>()
    var textFailure: Exception? = null
    var downloadFailure: Exception? = null

    override suspend fun readText(url: String, maxBytes: Long): String {
        textFailure?.let { throw it }
        val text = texts[url] ?: throw IOException("no fake text for $url")
        if (text.toByteArray(Charsets.UTF_8).size.toLong() > maxBytes) throw IOException("too large")
        return text
    }

    override suspend fun download(url: String, target: File, maxBytes: Long, onProgress: (Int?) -> Unit) {
        downloadFailure?.let { throw it }
        val bytes = packages[url] ?: throw IOException("no fake package for $url")
        if (bytes.size.toLong() > maxBytes) throw IOException("too large")
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        onProgress(100)
    }
}

internal object UpdateFixtures {
    const val INDEX_URL: String = "https://index.example.test/plugins/index.json"

    const val PACKAGE_URL: String = "https://index.example.test/plugins/template-1.1.0.ddp"

    const val PLUGIN_ID: String = "io.github.whywood.deepdeep.plugintest"

    val PUBLISHER_KEY_ID: String = "ed25519:" + "b".repeat(64)

    val OTHER_KEY_ID: String = "ed25519:" + "c".repeat(64)

    fun entryJson(
        id: String = PLUGIN_ID,
        version: String = "1.1.0",
        url: String = PACKAGE_URL,
        sha256: String = "a".repeat(64),
        size: Long = 1024L,
        publisherKeyId: String = PUBLISHER_KEY_ID,
        apiLevel: Int = 3,
        minHostVersion: String? = null,
    ): String = buildJsonObject {
        put("id", id)
        put("version", version)
        put("apiLevel", apiLevel)
        minHostVersion?.let { put("minHostVersion", it) }
        put("url", url)
        put("sha256", sha256)
        put("size", size)
        put("publisherKeyId", publisherKeyId)
    }.toString()

    fun indexJson(vararg entries: String): String =
        """{"schemaVersion":1,"plugins":[${entries.joinToString(",")}]}"""

    fun index(vararg entries: PluginIndexEntry): PluginIndex = PluginIndex(1, entries.toList())

    fun entry(
        id: String = PLUGIN_ID,
        version: String = "1.1.0",
        apiLevel: Int = 3,
        minHostVersion: String? = null,
        sha256: String = "a".repeat(64),
        size: Long = 1024L,
        publisherKeyId: String = PUBLISHER_KEY_ID,
        url: String = PACKAGE_URL,
    ): PluginIndexEntry = PluginIndexEntry(
        id = id,
        version = version,
        apiLevel = apiLevel,
        minHostVersion = minHostVersion,
        url = url,
        sha256 = sha256,
        size = size,
        publisherKeyId = publisherKeyId,
    )

    fun record(
        id: String = PLUGIN_ID,
        version: String = "1.0.0",
        publisherKeyId: String = PUBLISHER_KEY_ID,
        pendingVersion: String? = null,
    ): ExternalPluginRecord = ExternalPluginRecord(
        id = id,
        version = version,
        installedAt = 0L,
        enabled = true,
        installPath = "/tmp/plugins/$id/$version",
        activeBundleSha256 = "0".repeat(64),
        size = 100L,
        publisherKeyId = publisherKeyId,
        apiLevel = 3,
        uiApiLevel = 1,
        pendingVersion = pendingVersion,
        pendingBundleSha256 = pendingVersion?.let { "1".repeat(64) },
    )
}
