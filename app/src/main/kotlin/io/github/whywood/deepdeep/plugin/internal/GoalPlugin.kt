package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.host.CodingToolPluginAdapter
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.goalTools

class GoalPlugin(
    private val goalService: GoalService,
) : DeepDeepPlugin {
    @Volatile
    private var values: Map<String, String> = emptyMap()

    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus =
        PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugins_status_builtin)))

    override fun settings(): List<SettingSpec> = listOf(
        SettingSpec.Number(
            key = MAX_ROUNDS_KEY,
            title = LocalizedText.Resource(R.string.plugins_setting_max_rounds),
            min = 0,
            max = 50,
            defaultValue = 0,
        ),
    )

    override suspend fun onSettingsChanged(host: PluginHost, values: Map<String, String>) {
        this.values = values
    }

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
        val live = session ?: return Contributions()
        val binding = SessionBinding(live.supportsChatDelivery) {
            live.snapshot()?.let { BoundSession(it.sessionId, it.file, it.cwd) }
        }
        val tools = goalTools(
            binding,
            goalService,
            defaultMaxRounds = { settingInt(MAX_ROUNDS_KEY, 0) },
        )
        return Contributions(tools = CodingToolPluginAdapter.toPluginTools(tools))
    }

    private fun settingInt(key: String, default: Int): Int = values[key]?.toIntOrNull() ?: default

    private companion object {
        const val MAX_ROUNDS_KEY = "max_rounds"
    }
}
