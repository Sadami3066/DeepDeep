package io.github.whywood.deepdeep.runtime

interface PluginController {
    val setupPluginIds: List<String> get() = emptyList()

    suspend fun setup(pluginId: String): PluginSetupResult
}

data class PluginSetupResult(
    val ok: Boolean,
    val pluginId: String,
    val installed: Boolean,
    val ready: Boolean,
    val status: String? = null,
    val account: String? = null,
    val error: String? = null,
)
