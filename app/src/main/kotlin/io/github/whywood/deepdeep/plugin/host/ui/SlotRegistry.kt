package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.api.HOST_UI_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.UiManifest
import io.github.whywood.deepdeep.plugin.host.PluginBindingEntry
import io.github.whywood.deepdeep.plugin.host.PluginPaths
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import java.io.File

internal data class HostUiVersions(
    val kotlin: String?,
    val compose: String?,
    val composeCompatibilityId: Int? = null,
) {
    companion object {
        fun current(): HostUiVersions {
            val detected = ComposeVersionDetector.detect()
            return HostUiVersions(
                kotlin = KotlinVersion.CURRENT.toString(),
                compose = detected.version,
                composeCompatibilityId = detected.compatibilityId,
            )
        }
    }
}

sealed interface SlotDisabledReason {
    data class UiApiLevelUnsupported(val declared: Int, val host: Int) : SlotDisabledReason

    data class ComposeApiLevelUnsupported(val declared: Int, val host: Int) : SlotDisabledReason

    data class KotlinVersionMismatch(val declared: String, val host: String) : SlotDisabledReason

    data class ComposeVersionMismatch(val declared: String, val host: String) : SlotDisabledReason

    data class ComposeVersionUnavailable(val compatibilityId: Int?) : SlotDisabledReason
}

data class SlotRenderEntry(
    val pluginId: String,
    val manifestVersion: String,
    val slotId: String,
    val priority: Int,
    val content: SlotContent.Compose,
    val assetsDir: File,
    val disabledReason: SlotDisabledReason? = null,
) {
    val composeCompatible: Boolean get() = disabledReason == null
}

class SlotRegistry internal constructor(
    private val snapshotProvider: () -> PluginRegistrySnapshot,
    private val pluginsRoot: File,
    private val hostVersions: HostUiVersions = HostUiVersions.current(),
) {
    fun bindings(slotId: String): List<SlotRenderEntry> = resolve { it.binding.slotId == slotId }

    fun entriesOf(pluginId: String): List<SlotRenderEntry> = resolve { it.pluginId == pluginId }

    private fun resolve(predicate: (PluginBindingEntry) -> Boolean): List<SlotRenderEntry> {
        val snapshot = snapshotProvider()
        val order = snapshot.plugins.mapIndexed { index, descriptor -> descriptor.id to index }.toMap()
        return snapshot.bindings
            .filter(predicate)
            .mapNotNull { entry ->
                val descriptor = snapshot.plugin(entry.pluginId) ?: return@mapNotNull null
                if (!descriptor.phase.contributes) return@mapNotNull null
                val content = entry.binding.content as? SlotContent.Compose ?: return@mapNotNull null
                SlotRenderEntry(
                    pluginId = entry.pluginId,
                    manifestVersion = descriptor.manifest.version,
                    slotId = entry.binding.slotId,
                    priority = entry.binding.priority,
                    content = content,
                    assetsDir = PluginPaths.assetsDir(pluginsRoot, descriptor.id, descriptor.manifest.version),
                    disabledReason = disabledReasonFor(content, descriptor.manifest.ui),
                )
            }
            .sortedWith(compareByDescending<SlotRenderEntry> { it.priority }.thenBy { order[it.pluginId] ?: Int.MAX_VALUE })
    }

    private fun disabledReasonFor(content: SlotContent.Compose, manifest: UiManifest?): SlotDisabledReason? {
        if (content.uiApiLevel > HOST_UI_API_LEVEL) {
            return SlotDisabledReason.ComposeApiLevelUnsupported(content.uiApiLevel, HOST_UI_API_LEVEL)
        }
        if (manifest == null) return null
        if (manifest.apiLevel > HOST_UI_API_LEVEL) {
            return SlotDisabledReason.UiApiLevelUnsupported(manifest.apiLevel, HOST_UI_API_LEVEL)
        }
        manifest.kotlin?.let { declared ->
            val host = hostVersions.kotlin
            if (host != null && !sameKotlinMinor(declared, host)) {
                return SlotDisabledReason.KotlinVersionMismatch(declared, host)
            }
        }
        manifest.compose?.let { declared ->
            val host = hostVersions.compose
                ?: return SlotDisabledReason.ComposeVersionUnavailable(hostVersions.composeCompatibilityId)
            val declaredVersion = ComposeVersionDetector.normalizeVersion(declared) ?: declared
            if (declaredVersion != host) return SlotDisabledReason.ComposeVersionMismatch(declaredVersion, host)
        }
        return null
    }

    private fun sameKotlinMinor(plugin: String, host: String): Boolean {
        fun core(value: String): String = value.split('-', '+').first()
        val pluginCore = core(plugin).split('.')
        val hostCore = core(host).split('.')
        return pluginCore.size >= 2 &&
            hostCore.size >= 2 &&
            pluginCore[0] == hostCore[0] &&
            pluginCore[1] == hostCore[1]
    }
}
