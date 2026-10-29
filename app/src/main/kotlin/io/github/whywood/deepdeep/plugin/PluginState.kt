package io.github.whywood.deepdeep.plugin

data class PluginSnapshot(
    val plugin: OfficialPlugin,
    val installedVersion: String?,
    val availability: PluginAvailability,
    val status: PluginStatus?,
    val enabled: Boolean,
    val settings: Map<String, String> = emptyMap(),
) {
    val installed: Boolean get() = installedVersion != null

    val updateAvailable: Boolean
        get() = installedVersion != null && PluginVersions.isOlder(installedVersion, plugin.version)
}

data class PluginOperation(
    val pluginId: String,
    val title: String,
    val stage: String? = null,
    val progress: Int? = null,
)

data class PluginInteraction(
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

data class PluginsUiState(
    val plugins: List<PluginSnapshot> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val operation: PluginOperation? = null,
    val interaction: PluginInteraction? = null,
    val error: String? = null,
) {
    fun snapshot(pluginId: String): PluginSnapshot? = plugins.firstOrNull { it.plugin.id == pluginId }
}

internal object PluginVersions {
    fun isOlder(installed: String, candidate: String): Boolean {
        val left = numericParts(installed)
        val right = numericParts(candidate)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a < b
        }
        return false
    }

    private fun numericParts(version: String): List<Long> = version
        .split('.', '-', '+')
        .map { part -> part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }
}
