package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.builtinPluginManifest
import io.github.whywood.deepdeep.plugin.internal.GoalPlugin
import io.github.whywood.deepdeep.plugin.internal.JobsPlugin
import io.github.whywood.deepdeep.plugin.internal.TimerPlugin
import io.github.whywood.deepdeep.plugin.internal.MiniappPlugin
import io.github.whywood.deepdeep.plugin.manager.BuiltinPluginEntry
import io.github.whywood.deepdeep.plugin.websearch.WebSearchPlugin

internal fun internalWebSearch(wiring: BuiltinPluginWiring): BuiltinPluginEntry = BuiltinPluginEntry(
    manifest = internalWebSearchManifest(),
    plugin = WebSearchPlugin(
        context = wiring.uiContext,
        apiKeyProvider = {
            wiring.credentials.cachedApiKey(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
                ?: run {
                    wiring.credentials.preload(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
                    wiring.credentials.cachedApiKey(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
                }
        },
    ),
)

internal fun internalWebSearchManifest(): PluginManifest = builtinPluginManifest(
    id = "web-search",
    version = "1.0.0",
    nameRes = R.string.plugin_web_search_name,
    summaryRes = R.string.plugin_web_search_summary,
    iconRes = DdIcons.Globe.res,
    sizeLabelRes = R.string.plugin_web_search_size,
    tools = listOf("web_search", "web_fetch"),
)

internal fun internalGoal(wiring: BuiltinPluginWiring): BuiltinPluginEntry = BuiltinPluginEntry(
    manifest = internalGoalManifest(),
    plugin = GoalPlugin(wiring.goalService),
)

internal fun internalGoalManifest(): PluginManifest = builtinPluginManifest(
    id = "goal",
    version = "1.0",
    nameRes = R.string.plugins_internal_goal_name,
    summaryRes = R.string.plugins_internal_goal_summary,
    iconRes = DdIcons.Target.res,
    tools = listOf("get_goal", "create_goal", "update_goal"),
    settings = listOf("max_rounds"),
)

internal fun internalJobs(wiring: BuiltinPluginWiring): BuiltinPluginEntry = BuiltinPluginEntry(
    manifest = internalJobsManifest(),
    plugin = JobsPlugin(
        wiring.jobRegistry,
        wiring.pluginStrings,
    ),
)

internal fun internalJobsManifest(): PluginManifest = builtinPluginManifest(
    id = "jobs",
    version = "1.0",
    nameRes = R.string.plugins_internal_jobs_name,
    summaryRes = R.string.plugins_internal_jobs_summary,
    iconRes = DdIcons.Clock.res,
    tools = listOf("job_start", "job_output", "job_list", "job_kill"),
    settings = listOf("max_running"),
)

internal fun internalTimer(wiring: BuiltinPluginWiring): BuiltinPluginEntry = BuiltinPluginEntry(
    manifest = internalTimerManifest(),
    plugin = TimerPlugin(
        wiring.timerService,
        wiring.pluginStrings,
    ),
)

internal fun internalTimerManifest(): PluginManifest = builtinPluginManifest(
    id = "timer",
    version = "1.0",
    nameRes = R.string.plugins_internal_timer_name,
    summaryRes = R.string.plugins_internal_timer_summary,
    iconRes = DdIcons.Clock.res,
    tools = listOf("timer_create", "timer_list", "timer_update", "timer_delete"),
)

internal fun internalMiniapp(wiring: BuiltinPluginWiring): BuiltinPluginEntry = BuiltinPluginEntry(
    manifest = internalMiniappManifest(),
    plugin = MiniappPlugin(
        wiring.miniappStore,
        wiring.assets,
        wiring.pluginStrings,
    ),
)

internal fun internalMiniappManifest(): PluginManifest = builtinPluginManifest(
    id = "miniapp",
    version = "1.0",
    nameRes = R.string.plugins_internal_miniapp_name,
    summaryRes = R.string.plugins_internal_miniapp_summary,
    iconRes = DdIcons.Apps.res,
    tools = listOf("miniapp_create", "miniapp_load", "miniapp_list", "miniapp_delete", "miniapp_edit"),
    skills = listOf("miniapp"),
    settings = listOf("skill_enabled", "max_turns"),
)
