package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.api.ComposerAction
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginPageState
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import io.github.whywood.deepdeep.plugin.host.PluginPaths
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import java.io.File

internal data class PluginPageRef(
    val pluginId: String,
    val page: PluginPage,
    val phase: PluginPhase,
    val assetsDir: File,
) {
    val available: Boolean get() = phase.contributes

    val slotId: String get() = PluginPageSlots.forPage(page.id)

    fun state(sessionId: String? = null): PluginPageState =
        PluginPageState(pluginId = pluginId, pageId = page.id, sessionId = sessionId)
}

internal data class PluginComposerActionRef(
    val pluginId: String,
    val action: ComposerAction,
    val phase: PluginPhase,
    val assetsDir: File,
) {
    val available: Boolean get() = phase.contributes
}

internal data class PluginUiBindingRef(
    val pluginId: String,
    val binding: SlotBinding,
    val phase: PluginPhase,
    val assetsDir: File,
) {
    val available: Boolean get() = phase.contributes

    val slotId: String get() = binding.slotId
}

internal data class PluginUiPlugin(
    val pluginId: String,
    val name: LocalizedText,
    val summary: LocalizedText,
    val version: String,
    val source: PluginSource,
    val phase: PluginPhase,
    val assetsDir: File,
    val pages: List<PluginPageRef>,
    val composerActions: List<PluginComposerActionRef>,
    val bindings: List<PluginUiBindingRef>,
) {
    val available: Boolean get() = phase.contributes
}

internal data class PluginUiCatalog(val plugins: List<PluginUiPlugin>) {
    val pages: List<PluginPageRef> get() = plugins.flatMap { it.pages }

    val composerActions: List<PluginComposerActionRef> get() = plugins.flatMap { it.composerActions }

    val bindings: List<PluginUiBindingRef> get() = plugins.flatMap { it.bindings }

    fun plugin(pluginId: String): PluginUiPlugin? = plugins.firstOrNull { it.pluginId == pluginId }

    fun pagesOf(pluginId: String, includeUnavailable: Boolean = false): List<PluginPageRef> =
        plugin(pluginId)?.pages.orEmpty().filter { includeUnavailable || it.available }

    fun page(pluginId: String, pageId: String): PluginPageRef? =
        plugin(pluginId)?.pages?.firstOrNull { it.page.id == pageId }

    fun composerActionsFor(includeUnavailable: Boolean = false): List<PluginComposerActionRef> =
        composerActions.filter { includeUnavailable || it.available }

    fun bindingsFor(slotId: String, includeUnavailable: Boolean = false): List<PluginUiBindingRef> =
        bindings.filter { it.slotId == slotId && (includeUnavailable || it.available) }

    fun slotIds(includeUnavailable: Boolean = false): Set<String> =
        bindings.filter { includeUnavailable || it.available }.mapTo(linkedSetOf()) { it.slotId }

    companion object {
        fun empty(): PluginUiCatalog = PluginUiCatalog(emptyList())

        fun from(snapshot: PluginRegistrySnapshot, pluginsRoot: File): PluginUiCatalog =
            PluginUiCatalog(snapshot.plugins.mapNotNull { it.toUiPlugin(pluginsRoot) })

        fun from(source: PluginUiCatalogProvider): PluginUiCatalog = source.current()

        private fun PluginDescriptor.toUiPlugin(pluginsRoot: File): PluginUiPlugin? {
            val ui = contributions.ui
            if (ui.bindings.isEmpty() && ui.pages.isEmpty() && ui.composerActions.isEmpty()) return null
            val assetsDir = PluginPaths.assetsDir(pluginsRoot, id, manifest.version)
            val pages = ui.pages.map { PluginPageRef(pluginId = id, page = it, phase = phase, assetsDir = assetsDir) }
            val actions = ui.composerActions.map {
                PluginComposerActionRef(pluginId = id, action = it, phase = phase, assetsDir = assetsDir)
            }
            val bindings = ui.bindings.map {
                PluginUiBindingRef(pluginId = id, binding = it, phase = phase, assetsDir = assetsDir)
            }
            return PluginUiPlugin(
                pluginId = id,
                name = manifest.name,
                summary = manifest.summary,
                version = manifest.version,
                source = manifest.source,
                phase = phase,
                assetsDir = assetsDir,
                pages = pages,
                composerActions = actions,
                bindings = bindings,
            )
        }
    }
}

internal fun interface PluginUiCatalogProvider {
    fun current(): PluginUiCatalog
}

internal fun pluginUiCatalogProvider(registry: PluginRegistry, pluginsRoot: File): PluginUiCatalogProvider =
    PluginUiCatalogProvider { PluginUiCatalog.from(registry.snapshot(), pluginsRoot) }
