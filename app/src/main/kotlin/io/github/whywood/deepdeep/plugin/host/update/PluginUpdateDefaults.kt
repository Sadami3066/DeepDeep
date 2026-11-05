package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.PluginPaths
import java.io.File
import java.net.URI

internal object PluginUpdateDefaults {
    const val SETTINGS_FILE_NAME: String = "sources.json"
    const val CACHE_FILE_NAME: String = "sources_cache.json"
    const val DOWNLOADS_DIR_NAME: String = "downloads"

    const val INDEX_SCHEMA_VERSION: Int = 1
    const val SETTINGS_SCHEMA_VERSION: Int = 1
    const val CACHE_SCHEMA_VERSION: Int = 1

    const val MAX_INDEX_BYTES: Long = 2L * 1024L * 1024L
    const val MAX_INDEX_ENTRIES: Int = 512
    const val MAX_PACKAGE_BYTES: Long = 100L * 1024L * 1024L

    const val DEFAULT_INTERVAL_HOURS: Int = 24
    const val MIN_INTERVAL_HOURS: Int = 1
    const val MAX_INTERVAL_HOURS: Int = 720

    const val CONNECT_TIMEOUT_MILLIS: Int = 10_000
    const val READ_TIMEOUT_MILLIS: Int = 30_000
    const val DOWNLOAD_READ_TIMEOUT_MILLIS: Int = 60_000
    const val USER_AGENT: String = "DeepDeep-Android"

    const val CORRUPT_SUFFIX: String = ".corrupt"

    private val SUPPORTED_SCHEMES = setOf("http", "https", "file")

    fun settingsFile(filesDir: File): File = File(PluginPaths.pluginsRoot(filesDir), SETTINGS_FILE_NAME)

    fun cacheFile(filesDir: File): File = File(PluginPaths.pluginsRoot(filesDir), CACHE_FILE_NAME)

    fun downloadsDir(filesDir: File): File = File(PluginPaths.pluginsRoot(filesDir), DOWNLOADS_DIR_NAME)

    fun supportsUrl(url: String): Boolean {
        val scheme = runCatching { URI(url.trim()).scheme?.lowercase() }.getOrNull() ?: return false
        return scheme in SUPPORTED_SCHEMES
    }
}
