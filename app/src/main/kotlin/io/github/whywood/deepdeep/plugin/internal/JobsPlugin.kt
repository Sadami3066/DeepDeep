package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
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
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.jobTools

class JobsPlugin(
    private val jobRegistry: JobRegistry,
    strings: PluginStrings,
) : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus =
        PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugins_status_builtin)))

    override fun settings(): List<SettingSpec> = listOf(
        SettingSpec.Number(
            key = MAX_RUNNING_KEY,
            title = LocalizedText.Resource(R.string.plugins_setting_max_running),
            min = 1,
            max = 20,
            defaultValue = 10,
        ),
    )

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
        val live = session ?: return Contributions()
        val binding = SessionBinding(live.supportsChatDelivery) {
            live.snapshot()?.let { BoundSession(it.sessionId, it.file, it.cwd) }
        }
        return Contributions(tools = CodingToolPluginAdapter.toPluginTools(jobTools(binding, jobRegistry)))
    }

    private companion object {
        const val MAX_RUNNING_KEY = "max_running"
    }
}
