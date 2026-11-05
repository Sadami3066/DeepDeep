package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.host.builtinPluginManifest
import io.github.whywood.deepdeep.plugin.internal.GoalPlugin
import io.github.whywood.deepdeep.plugin.internal.JobsPlugin
import io.github.whywood.deepdeep.plugin.internal.TimerPlugin
import io.github.whywood.deepdeep.plugin.internal.WorkshopPlugin
import io.github.whywood.deepdeep.plugin.websearch.WebSearchPlugin

internal fun internalWebSearch(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    WebSearchPlugin(
        context = wiring.uiContext,
        apiKeyProvider = {
            wiring.credentials.cachedApiKey(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
                ?: run {
                    wiring.credentials.preload(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
                    wiring.credentials.cachedApiKey(io.github.whywood.deepdeep.runtime.ProviderCatalog.DEFAULT_PROVIDER_ID)
                }
        },
    ),
    builtinPluginManifest(
        id = "web-search",
        version = "1.0.0",
        nameRes = R.string.plugin_web_search_name,
        summaryRes = R.string.plugin_web_search_summary,
        iconRes = DdIcons.Globe.res,
        sizeLabelRes = R.string.plugin_web_search_size,
        tools = listOf("web_search", "web_fetch"),
    ),
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

internal fun internalJobs(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    JobsPlugin(
        wiring.jobRegistry,
        wiring.pluginStrings,
    ),
    builtinPluginManifest(
        id = "jobs",
        version = "1.0",
        nameRes = R.string.plugins_internal_jobs_name,
        summaryRes = R.string.plugins_internal_jobs_summary,
        iconRes = DdIcons.Clock.res,
        tools = listOf("job_start", "job_output", "job_list", "job_kill"),
        settings = listOf("max_running"),
    ),
)

internal fun internalTimer(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    TimerPlugin(
        wiring.timerService,
        wiring.pluginStrings,
    ),
    builtinPluginManifest(
        id = "timer",
        version = "1.0",
        nameRes = R.string.plugins_internal_timer_name,
        summaryRes = R.string.plugins_internal_timer_summary,
        iconRes = DdIcons.Clock.res,
        tools = listOf("timer_create", "timer_list", "timer_update", "timer_delete"),
    ),
)

internal fun internalWorkshop(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    WorkshopPlugin(
        wiring.workshopStore,
        wiring.assets,
        wiring.pluginStrings,
    ),
    builtinPluginManifest(
        id = "workshop",
        version = "1.0",
        nameRes = R.string.plugins_internal_workshop_name,
        summaryRes = R.string.plugins_internal_workshop_summary,
        iconRes = DdIcons.Apps.res,
        tools = listOf("workshop_create", "workshop_load", "workshop_list", "workshop_delete", "workshop_edit"),
        skills = listOf("workshop"),
        settings = listOf("skill_enabled", "max_turns"),
    ),
)
