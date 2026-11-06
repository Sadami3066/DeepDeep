package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PublisherInfo

enum class PluginPhaseUi {
    INSTALLED,
    ACTIVE,
    DEGRADED,
    FAILED,
    DISABLED,
    PENDING_RESTART,
}

sealed interface PluginAvailabilityUi {
    data object Available : PluginAvailabilityUi

    data class Unavailable(val reason: String) : PluginAvailabilityUi
}

data class PluginPageUi(
    val id: String,
    val title: String,
)

data class PluginSnapshot(
    val id: String,
    val name: String,
    val summary: String,
    val description: String?,
    val version: String,
    val installedVersion: String?,
    val previousVersion: String?,
    val icon: IconRef?,
    val enabled: Boolean,
    val phase: PluginPhaseUi,
    val availability: PluginAvailabilityUi,
    val updateAvailable: Boolean,
    val pages: List<PluginPageUi>,
    val skills: List<String>,
    val installedSizeBytes: Long?,
    val lastError: String?,
    val publisher: PublisherInfo?,
) {
    val installed: Boolean get() = installedVersion != null

    val hasPages: Boolean get() = pages.isNotEmpty()
}

data class PluginRecoveryResult(
    val ok: Boolean,
    val error: String? = null,
)

data class PluginsUiState(
    val plugins: List<PluginSnapshot> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val availableUpdates: List<PluginUpdateUi> = emptyList(),
) {
    fun snapshot(pluginId: String): PluginSnapshot? = plugins.firstOrNull { it.id == pluginId }
}

internal fun PluginAvailability.toUi(): PluginAvailabilityUi = when (this) {
    is PluginAvailability.Available -> PluginAvailabilityUi.Available
    is PluginAvailability.Unavailable -> PluginAvailabilityUi.Unavailable(reason)
}
