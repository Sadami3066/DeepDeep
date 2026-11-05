package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.host.PluginPaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class ActivationMarker(
    val version: String,
    val attempts: Int,
    val startedAt: Long,
)

internal class ExternalPluginRecovery(
    private val pluginsRoot: File,
    private val recordStore: ExternalPluginRecordStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val stager = PluginPackageStager(pluginsRoot)

    suspend fun beginActivation(pluginId: String, targetVersion: String): Int? = withContext(ioDispatcher) {
        if (recordStore.get(pluginId) == null) return@withContext null
        val previous = readMarker(pluginId)
        val attempts = if (previous != null && previous.version == targetVersion) previous.attempts + 1 else 1
        writeMarker(pluginId, ActivationMarker(targetVersion, attempts, clock()))
        attempts
    }

    suspend fun completeActivation(pluginId: String): Boolean = withContext(ioDispatcher) {
        deleteMarker(pluginId)
    }

    suspend fun interruptedActivation(pluginId: String): ActivationMarker? = withContext(ioDispatcher) {
        readMarker(pluginId)
    }

    suspend fun noteStartup(pluginId: String, version: String): Int = withContext(ioDispatcher) {
        val previous = readMarker(pluginId)
        val attempts = if (previous != null && previous.version == version) previous.attempts + 1 else 1
        writeMarker(pluginId, ActivationMarker(version, attempts, clock()))
        attempts
    }

    suspend fun clearStartupCount(pluginId: String): Boolean = withContext(ioDispatcher) {
        deleteMarker(pluginId)
    }

    suspend fun rollbackToPrevious(pluginId: String): ExternalPluginRecord? = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext null
        val previousVersion = record.previousVersion ?: return@withContext null
        val previousPath = record.previousInstallPath ?: return@withContext null
        val previousSha256 = record.previousBundleSha256 ?: return@withContext null
        val previousBundle = stager.bundleState(pluginId, previousVersion, previousPath, previousSha256)
        if (previousBundle !is BundleState.Ready) return@withContext null
        val rolledBack = record.copy(
            version = previousVersion,
            installPath = previousPath,
            activeBundleSha256 = previousSha256,
            pendingVersion = null,
            pendingBundleSha256 = null,
            previousVersion = null,
            previousInstallPath = null,
            previousBundleSha256 = null,
            lastError = null,
        )
        recordStore.upsert(rolledBack)
        stager.pruneAbandonedVersion(pluginId, record.installPath, keep = setOf(previousPath))
        deleteMarker(pluginId)
        rolledBack
    }

    suspend fun pruneVersions(pluginId: String): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        val pluginRoot = PluginPaths.pluginRoot(pluginsRoot, pluginId)
        val keep = buildSet {
            add(record.installPath)
            record.pendingVersion?.let { add(stager.pendingInstallPath(pluginId, it)) }
            record.previousInstallPath?.let(::add)
        }
        val children = pluginRoot.listFiles().orEmpty()
        children.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (child.name == PluginPaths.DATA_DIR) return@forEach
            if (child.absolutePath in keep) return@forEach
            runCatching { child.deleteRecursively() }
        }
        true
    }

    private fun markerFile(pluginId: String): File =
        File(PluginPaths.pluginRoot(pluginsRoot, pluginId), MARKER_FILE)

    private fun readMarker(pluginId: String): ActivationMarker? {
        val file = markerFile(pluginId)
        if (!file.isFile) return null
        val root = JsonFiles.read(file) ?: return null
        val version = (root["version"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return ActivationMarker(
            version = version,
            attempts = (root["attempts"] as? JsonPrimitive)?.intOrNull ?: 1,
            startedAt = (root["startedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
        )
    }

    private fun writeMarker(pluginId: String, marker: ActivationMarker) {
        JsonFiles.write(
            markerFile(pluginId),
            buildJsonObject {
                put("version", marker.version)
                put("attempts", marker.attempts)
                put("startedAt", marker.startedAt)
            },
        )
    }

    private fun deleteMarker(pluginId: String): Boolean {
        val file = markerFile(pluginId)
        return !file.exists() || file.delete()
    }

    private companion object {
        const val MARKER_FILE: String = "activation.json"
    }
}
