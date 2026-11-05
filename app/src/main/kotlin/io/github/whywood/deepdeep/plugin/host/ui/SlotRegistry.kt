package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.api.HOST_UI_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.UiManifest
import io.github.whywood.deepdeep.plugin.host.PluginPaths
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import java.io.File

internal data class HostUiVersions(
    val kotlin: String?,
    val compose: String?,
) {
    companion object {
        fun current(): HostUiVersions = HostUiVersions(
            kotlin = KotlinVersion.CURRENT.toString(),
            compose = composeVersion(),
        )

        private fun composeVersion(): String? = runCatching {
            val type = Class.forName("androidx.compose.runtime.ComposeVersion")
            type.getField("version").get(null) as String
        }.getOrNull()
    }
}

internal data class SlotRenderEntry(
    val pluginId: String,
    val slotId: String,
    val priority: Int,
    val content: SlotContent,
    val assetsDir: File,
    val composeCompatible: Boolean,
)

internal class SlotRegistry(
    private val snapshotProvider: () -> PluginRegistrySnapshot,
    private val pluginsRoot: File,
    private val hostVersions: HostUiVersions = HostUiVersions.current(),
) {
    fun bindings(slotId: String): List<SlotRenderEntry> {
        val snapshot = snapshotProvider()
        val order = snapshot.plugins.mapIndexed { index, descriptor -> descriptor.id to index }.toMap()
        return snapshot.bindings
            .filter { it.binding.slotId == slotId }
            .mapNotNull { entry ->
                val descriptor = snapshot.plugin(entry.pluginId) ?: return@mapNotNull null
                if (!descriptor.phase.contributes) return@mapNotNull null
                SlotRenderEntry(
                    pluginId = entry.pluginId,
                    slotId = slotId,
                    priority = entry.binding.priority,
                    content = entry.binding.content,
                    assetsDir = PluginPaths.assetsDir(pluginsRoot, descriptor.id, descriptor.manifest.version),
                    composeCompatible = isComposeCompatible(entry.binding.content, descriptor.manifest.ui),
                )
            }
            .sortedWith(
                compareByDescending<SlotRenderEntry> { it.priority }
                    .thenBy { order[it.pluginId] ?: Int.MAX_VALUE },
            )
    }

    fun firstRenderable(slotId: String): SlotRenderEntry? = bindings(slotId).firstOrNull { it.isRenderable() }

    fun hasOverride(slotId: String): Boolean = firstRenderable(slotId) != null

    private fun SlotRenderEntry.isRenderable(): Boolean = when (content) {
        is SlotContent.Declarative -> true
        is SlotContent.Compose -> composeCompatible
    }

    private fun isComposeCompatible(content: SlotContent, manifest: UiManifest?): Boolean {
        if (content !is SlotContent.Compose) return true
        if (content.uiApiLevel > HOST_UI_API_LEVEL) return false
        manifest ?: return true
        if (manifest.apiLevel > HOST_UI_API_LEVEL) return false
        val pluginKotlin = manifest.kotlin
        val hostKotlin = hostVersions.kotlin
        if (pluginKotlin != null && hostKotlin != null && !sameKotlinMinor(pluginKotlin, hostKotlin)) {
            return false
        }
        val pluginCompose = manifest.compose
        val hostCompose = hostVersions.compose
        if (pluginCompose != null && hostCompose != null && pluginCompose != hostCompose) {
            return false
        }
        return true
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
