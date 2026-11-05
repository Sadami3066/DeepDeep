package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.PluginAction
import io.github.whywood.deepdeep.plugin.api.PluginDetailRow
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PublisherInfo
import io.github.whywood.deepdeep.plugin.api.SettingSpec

internal enum class PluginPhaseUi {
    IMPORTED,
    INSTALLED,
    ACTIVE,
    DEGRADED,
    FAILED,
    DISABLED,
    PENDING_RESTART,
    UNLOADED,
}

internal sealed interface PluginAvailabilityUi {
    data object Available : PluginAvailabilityUi

    data class Unavailable(val reason: String) : PluginAvailabilityUi
}

internal sealed interface PluginStatusUi {
    val label: String

    data class Ready(
        override val label: String,
        val account: String? = null,
    ) : PluginStatusUi

    data class NotReady(override val label: String) : PluginStatusUi

    data class Failed(override val label: String) : PluginStatusUi
}

internal data class PluginSnapshot(
    val id: String,
    val name: String,
    val summary: String,
    val description: String?,
    val version: String,
    val installedVersion: String?,
    val sizeLabel: String?,
    val icon: IconRef?,
    val source: PluginSource,
    val installable: Boolean,
    val requiresPrivilege: Boolean,
    val enabled: Boolean,
    val phase: PluginPhaseUi,
    val availability: PluginAvailabilityUi,
    val status: PluginStatusUi?,
    val updateAvailable: Boolean,
    val settingsValues: Map<String, String>,
    val settings: List<SettingSpec>,
    val actions: List<PluginAction>,
    val detailRows: List<PluginDetailRow>,
    val skills: List<String>,
    val installedSizeBytes: Long?,
    val lastError: String?,
    val publisher: PublisherInfo?,
) {
    val installed: Boolean get() = installedVersion != null

    val ready: Boolean get() = status is PluginStatusUi.Ready
}

internal data class PluginOperation(
    val pluginId: String,
    val title: String,
    val stage: String? = null,
    val progress: Int? = null,
)

internal data class PluginInteraction(
    val pluginId: String,
    val title: String,
    val stageTitle: String,
    val stageMessage: String? = null,
    val url: String? = null,
    val imagePath: String? = null,
    val progress: Int? = null,
    val confirmInstall: Boolean = false,
    val cancellable: Boolean = true,
)

internal data class PluginSetupNotice(
    val pluginId: String,
    val pluginName: String,
    val stage: String,
)

internal data class PluginsUiState(
    val plugins: List<PluginSnapshot> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val operation: PluginOperation? = null,
    val interaction: PluginInteraction? = null,
    val error: String? = null,
) {
    fun snapshot(pluginId: String): PluginSnapshot? = plugins.firstOrNull { it.id == pluginId }

    val installablePlugins: List<PluginSnapshot> get() = plugins.filter { it.installable }
}

internal fun PluginStatus.toUi(): PluginStatusUi = when (this) {
    is PluginStatus.Ready -> PluginStatusUi.Ready(label, account)
    is PluginStatus.NotReady -> PluginStatusUi.NotReady(label)
    is PluginStatus.Failed -> PluginStatusUi.Failed(label)
}
