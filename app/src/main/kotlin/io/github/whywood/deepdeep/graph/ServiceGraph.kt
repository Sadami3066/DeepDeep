package io.github.whywood.deepdeep.graph

import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.service.LinuxJobRunner
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.service.ProcessRuntime
import java.io.File

internal class ServiceGraph(private val deps: ServiceGraphDeps) {
    val notificationCenter: NotificationCenter by lazy {
        NotificationCenter(deps.uiContext, deps.host(), deps.conversationStore(), deps.questionCenter.pending)
    }

    val timerService: TimerService by lazy {
        TimerService(
            deps.host(),
            deps.timerStore,
            deps.scope,
            intentsFile = File(deps.appContext.filesDir, "timers/delivery-intents.json"),
        )
    }

    val jobRegistry: JobRegistry by lazy {
        JobRegistry(
            scope = deps.scope,
            notifier = { sessionId, notice, prompt ->
                deps.host().deliverBackgroundTurn(sessionId, notice, prompt, io.github.whywood.deepdeep.runtime.TurnSource.JOB)
            },
            wakeEnabled = { deps.jobWake.value },
            runnerFactory = { cwd -> LinuxJobRunner(deps.linuxEnvironmentFor(cwd)) },
            maxRunningPerSession = { deps.pluginSettingsStore.cachedInt("jobs", "max_running", 10) },
        )
    }

    val devPackageManager: DevPackageManager by lazy {
        DevPackageManager(deps.uiContext, deps.linuxEnvironment, notificationCenter)
    }

    val processRuntime: ProcessRuntime by lazy {
        ProcessRuntime(
            scope = deps.scope,
            prepare = deps.prepare,
            startPlugins = { deps.pluginManager().start() },
            onForegroundPlugins = { deps.pluginManager().onAppForeground() },
            refreshPluginsIfStale = { deps.pluginManager().refreshIfStale(it) },
            reconfigureHost = { deps.host().reconfigure() },
            ensureHostStarted = { deps.host().ensureStarted() },
            syncSkills = { deps.skillRepository.syncIfChanged() },
            diagnosticsDir = File(deps.appContext.filesDir, "diagnostics"),
        )
    }
}
