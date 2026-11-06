package io.github.whywood.deepdeep.plugin.host

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class PluginFailureMarker(
    val key: String,
    val detail: String? = null,
    val markedAt: Long = 0,
)

class PluginFailureGuard(private val dir: File) {
    fun mark(key: String, detail: String? = null, markedAt: Long = System.currentTimeMillis()) {
        val file = fileFor(key)
        file.parentFile?.mkdirs()
        file.writeText(
            buildJsonObject {
                put("key", key)
                put("detail", detail)
                put("markedAt", markedAt)
            }.toString(),
        )
    }

    fun clear(key: String): Boolean = fileFor(key).delete()

    fun detect(key: String, now: Long = System.currentTimeMillis()): PluginFailureMarker? {
        val file = fileFor(key)
        if (!file.isFile) return null
        val marker = readMarker(file)
        if (isStale(marker, now)) {
            file.delete()
            return null
        }
        return marker
    }

    fun detectAll(now: Long = System.currentTimeMillis()): List<PluginFailureMarker> {
        val files = markerFiles().sortedBy { it.name }
        return files.mapNotNull { file ->
            val marker = readMarker(file)
            if (isStale(marker, now)) {
                file.delete()
                null
            } else {
                marker
            }
        }
    }

    fun clearAll(): Int {
        val files = markerFiles()
        return files.count { it.delete() }
    }

    fun detectFor(pluginId: String, now: Long = System.currentTimeMillis()): List<PluginFailureMarker> {
        val prefix = "$pluginId@"
        return detectAll(now).filter { marker -> marker.key.startsWith(prefix) }
    }

    fun clearFor(pluginId: String): Int = detectFor(pluginId).count { marker -> clear(marker.key) }

    private fun isStale(marker: PluginFailureMarker, now: Long): Boolean =
        marker.markedAt > 0 && now - marker.markedAt > MARKER_TTL_MS

    private fun markerFiles(): List<File> =
        dir.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }?.toList().orEmpty()

    private fun readMarker(file: File): PluginFailureMarker = runCatching {
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        PluginFailureMarker(
            key = json.getValue("key").jsonPrimitive.content,
            detail = json["detail"]?.jsonPrimitive?.contentOrNull,
            markedAt = json["markedAt"]?.jsonPrimitive?.longOrNull ?: file.lastModified(),
        )
    }.getOrElse {
        PluginFailureMarker(decodeFallbackName(file), null, file.lastModified())
    }

    private fun fileFor(key: String): File = File(dir, FILE_PREFIX + encodeKey(key) + FILE_SUFFIX)

    private fun encodeKey(key: String): String {
        val encoded = key.replace(UNSAFE_KEY, "_")
        return if (encoded.length <= MAX_KEY_LENGTH) {
            encoded
        } else {
            encoded.take(MAX_KEY_LENGTH) + "_" + Integer.toHexString(key.hashCode())
        }
    }

    private fun decodeFallbackName(file: File): String =
        file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)

    internal companion object {
        const val FILE_PREFIX = "guard_"

        const val FILE_SUFFIX = ".json"

        const val MAX_KEY_LENGTH = 80

        const val MARKER_TTL_MS = 60L * 60L * 1000L

        val UNSAFE_KEY = Regex("[^A-Za-z0-9._-]")
    }
}
