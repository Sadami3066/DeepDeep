package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.external.JsonFiles
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

data class PluginRepositorySettings(
    val indexUrl: String? = null,
    val autoCheck: Boolean = true,
    val intervalHours: Int = PluginUpdateDefaults.DEFAULT_INTERVAL_HOURS,
)

internal class PluginRepositorySettingsStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(): PluginRepositorySettings = withContext(ioDispatcher) { loadLocked() }

    suspend fun save(settings: PluginRepositorySettings): PluginRepositorySettings = withContext(ioDispatcher) {
        val normalized = normalize(settings)
        JsonFiles.write(file, toJson(normalized))
        normalized
    }

    fun normalize(settings: PluginRepositorySettings): PluginRepositorySettings = settings.copy(
        indexUrl = settings.indexUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { PluginUpdateDefaults.supportsUrl(it) },
        autoCheck = settings.autoCheck,
        intervalHours = settings.intervalHours.coerceIn(
            PluginUpdateDefaults.MIN_INTERVAL_HOURS,
            PluginUpdateDefaults.MAX_INTERVAL_HOURS,
        ),
    )

    private fun loadLocked(): PluginRepositorySettings {
        if (!file.isFile) return PluginRepositorySettings()
        val root = JsonFiles.read(file) ?: return resetLocked()
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
        if (schemaVersion != PluginUpdateDefaults.SETTINGS_SCHEMA_VERSION) return resetLocked()
        return parse(root)
    }

    private fun parse(root: JsonObject): PluginRepositorySettings {
        val indexUrl = (root["indexUrl"] as? JsonPrimitive)?.contentOrNull
        val autoCheck = (root["autoCheck"] as? JsonPrimitive)?.booleanOrNull ?: true
        val intervalHours = (root["intervalHours"] as? JsonPrimitive)?.intOrNull
            ?: PluginUpdateDefaults.DEFAULT_INTERVAL_HOURS
        return normalize(
            PluginRepositorySettings(
                indexUrl = indexUrl,
                autoCheck = autoCheck,
                intervalHours = intervalHours,
            ),
        )
    }

    private fun resetLocked(): PluginRepositorySettings {
        JsonFiles.backup(file, PluginUpdateDefaults.CORRUPT_SUFFIX)
        val defaults = PluginRepositorySettings()
        JsonFiles.write(file, toJson(defaults))
        return defaults
    }

    private fun toJson(settings: PluginRepositorySettings): JsonObject = buildJsonObject {
        put("schemaVersion", PluginUpdateDefaults.SETTINGS_SCHEMA_VERSION)
        settings.indexUrl?.let { put("indexUrl", it) }
        put("autoCheck", settings.autoCheck)
        put("intervalHours", settings.intervalHours)
    }
}
