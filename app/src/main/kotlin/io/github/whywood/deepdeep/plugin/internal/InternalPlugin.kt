package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginProgress
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginStrings

abstract class InternalPlugin(
    private val strings: PluginStrings,
) : OfficialPlugin {
    final override val kind: PluginKind = PluginKind.INTERNAL

    final override val sizeLabelRes: Int
        get() = R.string.plugins_size_builtin

    @Volatile
    private var values: Map<String, String> = emptyMap()

    protected fun settingInt(key: String, default: Int): Int =
        values[key]?.toIntOrNull() ?: default

    protected fun settingBool(key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    protected fun settingString(key: String, default: String): String =
        values[key] ?: default

    override suspend fun onSettingsChanged(values: Map<String, String>) {
        this.values = values
    }

    override suspend fun availability(): PluginAvailability = PluginAvailability.Available

    override suspend fun install(progress: PluginProgress) = Unit

    override suspend fun uninstall() = Unit

    override suspend fun status(): PluginStatus =
        PluginStatus.Ready(strings.get(R.string.plugins_status_builtin))
}
