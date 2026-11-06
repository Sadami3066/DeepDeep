package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PublisherInfo
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver

internal class PluginUiProjection(
    private val textResolver: PluginTextResolver,
) {
    fun snapshots(
        views: List<PluginRuntimeView>,
        registrySnapshot: PluginRegistrySnapshot,
    ): List<PluginSnapshot> = views.mapNotNull { view ->
        val descriptor = registrySnapshot.plugin(view.id) ?: return@mapNotNull null
        PluginSnapshot(
            id = descriptor.id,
            name = textResolver.resolve(descriptor.manifest.name),
            summary = textResolver.resolve(descriptor.manifest.summary),
            description = descriptor.manifest.description?.let(textResolver::resolve),
            version = descriptor.manifest.version,
            installedVersion = view.record.installedVersion,
            previousVersion = view.record.previousVersion,
            icon = descriptor.manifest.icon,
            enabled = view.enabled,
            phase = phaseUi(view, descriptor.phase),
            availability = view.availability,
            updateAvailable = PluginRules.updateAvailable(
                descriptor.manifest.version,
                view.record.installedVersion,
                view.record.pendingVersion,
            ),
            pages = descriptor.contributions.ui.pages.map { page ->
                PluginPageUi(page.id, textResolver.resolve(page.title))
            },
            skills = descriptor.contributions.skills.map { it.id },
            installedSizeBytes = view.externalRecord?.size,
            lastError = view.record.lastError,
            publisher = view.externalRecord?.let { record -> PublisherInfo("", record.publisherKeyId) },
        )
    }

    private fun phaseUi(view: PluginRuntimeView, phase: PluginPhase): PluginPhaseUi = when {
        !view.enabled -> PluginPhaseUi.DISABLED
        view.record.pendingVersion != null -> PluginPhaseUi.PENDING_RESTART
        else -> phase.toUi()
    }

    private fun PluginPhase.toUi(): PluginPhaseUi = when (this) {
        PluginPhase.INSTALLED -> PluginPhaseUi.INSTALLED
        PluginPhase.ACTIVE -> PluginPhaseUi.ACTIVE
        PluginPhase.DEGRADED -> PluginPhaseUi.DEGRADED
        PluginPhase.FAILED -> PluginPhaseUi.FAILED
        PluginPhase.DISABLED -> PluginPhaseUi.DISABLED
    }
}
