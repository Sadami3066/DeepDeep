package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.host.builtinPluginManifest
import io.github.whywood.deepdeep.plugin.internal.GoalPlugin
import io.github.whywood.deepdeep.plugin.internal.JobsPlugin
import io.github.whywood.deepdeep.plugin.internal.TimerPlugin
import io.github.whywood.deepdeep.plugin.internal.WorkshopPlugin
import io.github.whywood.deepdeep.plugin.websearch.WebSearchPlugin

internal fun internalWebSearch(wiring: BuiltinPluginWiring): OfficialPlugin = WebSearchPlugin(
    context = wiring.uiContext,
    apiKeyProvider = {
        wiring.credentials.cachedApiKey(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
            ?: run {
                wiring.credentials.preload(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
                wiring.credentials.cachedApiKey(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
            }
    },
)

internal fun internalGoal(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    GoalPlugin(wiring.goalService),
    builtinPluginManifest(
        id = "goal",
        version = "1.0",
        nameRes = R.string.plugins_internal_goal_name,
        summaryRes = R.string.plugins_internal_goal_summary,
        iconRes = DdIcons.Target.res,
        tools = listOf("get_goal", "create_goal", "update_goal"),
        settings = listOf("max_rounds"),
    ),
)

internal fun internalJobs(wiring: BuiltinPluginWiring): OfficialPlugin = JobsPlugin(
    wiring.jobRegistry,
    wiring.pluginStrings,
)

internal fun internalTimer(wiring: BuiltinPluginWiring): OfficialPlugin = TimerPlugin(
    wiring.timerService,
    wiring.pluginStrings,
)

internal fun internalWorkshop(wiring: BuiltinPluginWiring): OfficialPlugin = WorkshopPlugin(
    wiring.workshopStore,
    wiring.assets,
    wiring.pluginStrings,
)
