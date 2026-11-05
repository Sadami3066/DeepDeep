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
import io.github.whywood.deepdeep.plugin.host.CodingToolPluginAdapter
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.timerTools

class TimerPlugin(
    private val timerService: TimerService,
    strings: PluginStrings,
) : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus =
        PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugins_status_builtin)))

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
        val bound = session ?: return Contributions()
        val binding = SessionBinding(bound.supportsChatDelivery).apply {
            bind(BoundSession(bound.sessionId, bound.file, bound.cwd))
        }
        return Contributions(tools = CodingToolPluginAdapter.toPluginTools(timerTools(timerService, binding)))
    }
}
