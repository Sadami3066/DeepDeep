package io.github.whywood.superpi.plugin.host.ui

import io.github.whywood.superpi.plugin.api.ToolResultMatch
import io.github.whywood.superpi.plugin.api.ToolResultRenderer
import io.github.whywood.superpi.plugin.api.ToolResultSource
import io.github.whywood.superpi.plugin.host.PluginPaths
import io.github.whywood.superpi.plugin.host.PluginRegistrySnapshot
import java.io.File

data class ToolRendererEntry(
    val pluginId: String,
    val manifestVersion: String,
    val renderer: ToolResultRenderer,
    val assetsDir: File,
    val disabledReason: SlotDisabledReason? = null,
) {
    val composeCompatible: Boolean get() = disabledReason == null

    val priority: Int get() = renderer.priority

    val match: ToolResultMatch get() = renderer.match
}

class ToolRendererRegistry internal constructor(
    private val snapshotProvider: () -> PluginRegistrySnapshot,
    private val pluginsRoot: File,
    private val hostVersions: HostUiVersions = HostUiVersions.current(),
) {
    fun resolve(
        source: ToolResultSource,
        server: String?,
        tool: String,
    ): ToolRendererEntry? = candidates(source, server, tool).firstOrNull { it.composeCompatible }

    fun candidates(
        source: ToolResultSource,
        server: String?,
        tool: String,
    ): List<ToolRendererEntry> {
        val snapshot = snapshotProvider()
        val order = snapshot.plugins.mapIndexed { index, descriptor -> descriptor.id to index }.toMap()
        return snapshot.toolResultRenderers
            .mapNotNull { entry ->
                val descriptor = snapshot.plugin(entry.pluginId) ?: return@mapNotNull null
                if (!descriptor.phase.contributes) return@mapNotNull null
                if (!entry.renderer.match.matches(source, server, tool)) return@mapNotNull null
                ToolRendererEntry(
                    pluginId = entry.pluginId,
                    manifestVersion = descriptor.manifest.version,
                    renderer = entry.renderer,
                    assetsDir = PluginPaths.assetsDir(pluginsRoot, descriptor.id, descriptor.manifest.version),
                    disabledReason = uiDisabledReason(descriptor.manifest.ui, hostVersions),
                )
            }
            .sortedWith(
                compareByDescending<ToolRendererEntry> { it.priority }
                    .thenByDescending { it.match.specificity() }
                    .thenBy { order[it.pluginId] ?: Int.MAX_VALUE },
            )
    }

    fun entriesOf(pluginId: String): List<ToolRendererEntry> {
        val snapshot = snapshotProvider()
        val descriptor = snapshot.plugin(pluginId) ?: return emptyList()
        return snapshot.toolResultRenderers
            .filter { it.pluginId == pluginId }
            .map { entry ->
                ToolRendererEntry(
                    pluginId = pluginId,
                    manifestVersion = descriptor.manifest.version,
                    renderer = entry.renderer,
                    assetsDir = PluginPaths.assetsDir(pluginsRoot, descriptor.id, descriptor.manifest.version),
                    disabledReason = uiDisabledReason(descriptor.manifest.ui, hostVersions),
                )
            }
    }
}
