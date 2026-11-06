package io.github.whywood.deepdeep.graph

import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.arrange.AndroidArrangeCalendarGateway
import io.github.whywood.deepdeep.arrange.arrangeTools
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.privilege.shellBackendsFor
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.AppToolContext
import io.github.whywood.deepdeep.runtime.GoalPhase
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.MiniappAgentService
import io.github.whywood.deepdeep.runtime.PiSessionEngine
import io.github.whywood.deepdeep.runtime.RootCommandExecutor
import io.github.whywood.deepdeep.runtime.RootCommandResult
import io.github.whywood.deepdeep.runtime.RootToolStatus
import io.github.whywood.deepdeep.runtime.RunLedger
import io.github.whywood.deepdeep.runtime.RuntimeHooks
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.adbShellTool
import io.github.whywood.deepdeep.runtime.goalTools
import io.github.whywood.deepdeep.runtime.jobTools
import io.github.whywood.deepdeep.runtime.miniappEditTool
import io.github.whywood.deepdeep.runtime.miniappLookup
import io.github.whywood.deepdeep.runtime.miniappTools
import io.github.whywood.deepdeep.runtime.presentTool
import io.github.whywood.deepdeep.runtime.rootShellTool
import io.github.whywood.deepdeep.runtime.timerTools
import io.github.whywood.deepdeep.service.LinuxJobRunner
import io.github.whywood.deepdeep.shell.RootStatus
import io.github.whywood.deepdeep.web.DeepSeekWebProvider
import io.github.whywood.deepdeep.web.webTools
import java.io.File
import kotlinx.coroutines.launch

internal class RuntimeGraph(private val deps: RuntimeGraphDeps) {
    private val webProvider by lazy { DeepSeekWebProvider(deps.apiKeyProvider) }

    private val arrangeCalendar by lazy { AndroidArrangeCalendarGateway(deps.appContext) }

    private fun availableShellBackends(): List<PrivilegeLevel> =
        shellBackendsFor(deps.privilege().state.value.effective)

    private fun adbToolStatus(): RootToolStatus {
        val adb = deps.privilege().state.value.adb
        return when {
            adb.daemon -> RootToolStatus.Granted
            adb.busy -> RootToolStatus.NotRequested
            adb.error != null -> RootToolStatus.Error
            adb.paired -> RootToolStatus.NotRequested
            else -> RootToolStatus.Unsupported
        }
    }

    val appToolContext: AppToolContext by lazy {
        AppToolContext(
            questionCenter = deps.questionCenter,
            pluginTools = { binding -> deps.pluginManager().tools(binding) },
            pluginDeferredTools = { binding -> deps.pluginManager().deferredTools(binding) },
            extraToolFactories = listOf({ _, binding -> builtInTools(binding) }),
        )
    }

    private fun builtInTools(binding: SessionBinding): List<CodingTool> = buildList {
        add(presentTool(miniappLookup(deps.miniappStore)) { binding.current() })
        for (backend in availableShellBackends()) {
            when (backend) {
                PrivilegeLevel.ROOT -> add(
                    rootShellTool(
                        executor = RootCommandExecutor { command, timeoutMillis ->
                            val result = deps.rootShell.execute(command, timeoutMillis)
                            RootCommandResult(
                                exitCode = result.exitCode,
                                stdout = result.stdout,
                                stderr = result.stderr,
                                truncated = result.truncated,
                                timedOut = result.timedOut,
                            )
                        },
                        enabled = { PrivilegeLevel.ROOT in deps.privilege().state.value.availableBackends },
                        status = { deps.privilege().state.value.root.toRootToolStatus() },
                    ),
                )

                PrivilegeLevel.ADB -> add(
                    adbShellTool(
                        executor = deps.adbExecutor,
                        enabled = { PrivilegeLevel.ADB in deps.privilege().state.value.availableBackends },
                        status = { adbToolStatus() },
                    ),
                )

                PrivilegeLevel.NORMAL -> Unit
            }
        }
        addAll(webTools(webProvider))
        addAll(arrangeTools(arrangeCalendar))
        addAll(goalTools(binding, deps.goalService))
        addAll(jobTools(binding, deps.jobRegistry()))
        addAll(timerTools(deps.timerService(), binding))
        addAll(miniappTools(deps.miniappStore, binding.supportsChatDelivery))
        add(miniappEditTool(deps.miniappStore))
        if (isScreenOpsActive(deps.capabilitySettingsStore.screenOps.value, deps.privilege().state.value.effective)) {
            addAll(deps.screenTools())
            addAll(deps.virtualScreenTools())
        }
    }

    private fun pluginRuntimeHooks(): RuntimeHooks {
        val delegate = PluginHooksDelegate()
        return RuntimeHooks(
            promptTransform = delegate.promptTransform,
            systemContext = delegate.systemContext,
            inputHandler = delegate.inputHandler,
            toolHooks = delegate.toolHooks,
            onSessionBound = { binding -> deps.pluginManager().bindSessionHooks(binding, delegate) },
            onSessionClosed = { sessionId -> deps.pluginManager().onSessionClosed(sessionId) },
        )
    }

    private val engineFactory: suspend (File) -> PiSessionEngine = { cwd ->
        PiSessionEngine(
            workspace = cwd,
            agentDir = deps.agentDir,
            credentials = deps.credentials,
            transport = deps.httpTransport,
            shellExecutor = deps.shellExecutorFor(cwd),
            providers = deps.providerCatalog,
            personalization = { deps.personalizationStore.read().toRuntime() },
            sharedStorage = { deps.linuxSettings.isSharedStorageEnabled() && isSharedStorageGranted() },
            pluginPromptBody = { deps.pluginManager().promptBody() },
            toolContext = appToolContext,
            hooks = pluginRuntimeHooks(),
        )
    }

    val miniappAgentService: MiniappAgentService by lazy {
        MiniappAgentService(
            providers = deps.providerCatalog,
            credentials = deps.credentials,
            transport = deps.httpTransport,
            agentDir = deps.agentDir,
            runRoot = File(deps.appContext.filesDir, "agent-runs"),
            appToolContext = appToolContext,
            defaultConfig = { deps.modelConfigStore.read() },
            configuredProviders = { deps.credentials.authKinds.value.keys },
            personalization = { deps.personalizationStore.read().toRuntime() },
            pluginPromptBody = { deps.pluginManager().promptBody() },
            sharedStorage = { deps.linuxSettings.isSharedStorageEnabled() && isSharedStorageGranted() },
            defaultMaxTurns = { 16 },
            workspace = deps.homeDir,
            shellExecutorFor = deps.shellExecutorFor,
            backgroundRunnerFactory = { cwd -> LinuxJobRunner(deps.linuxEnvironmentFor(cwd)) },
            hooksFactory = { pluginRuntimeHooks() },
        )
    }

    val host: AgentRuntimeHost by lazy {
        AgentRuntimeHost(
            homeDir = deps.homeDir,
            workspacesDir = deps.workspacesDir,
            engineFactory = engineFactory,
            modelConfigStore = deps.modelConfigStore,
            catalog = deps.sessionCatalog,
            workspaceStore = deps.workspaceStore,
            providers = deps.providerCatalog,
            backgroundScope = deps.scope,
            goalDriver = deps.goalService,
            sessionCleaner = { sessionId: String ->
                deps.jobRegistry().clearSession(sessionId)
                deps.scope.launch { deps.goalService.clearSession(sessionId) }
            },
            sessionArchived = { sessionId -> deps.jobRegistry().clearSession(sessionId) },
            runLedger = RunLedger(File(deps.appContext.filesDir, "tasks")),
            keepHandleAlive = { sessionId ->
                deps.timerService().timers.value.any { timer ->
                    timer.sessionId == sessionId && timer.enabled && timer.nextRunAt != null
                } ||
                    deps.goalService.goals.value[sessionId]?.goal?.phase == GoalPhase.ACTIVE ||
                    deps.jobRegistry().jobs.value.any { job ->
                        job.sessionId == sessionId && job.status == JobStatus.RUNNING
                    } ||
                    deps.questionCenter.pending.value.any { question -> question.sessionId == sessionId }
            },
        )
    }

    val conversationStore: ConversationStore by lazy {
        ConversationStore(
            runtime = host,
            scope = deps.scope,
            decorate = deps.decorateInterrupted,
        ).also { it.start() }
    }
}

internal fun isScreenOpsActive(screenOps: Boolean, effective: PrivilegeLevel): Boolean =
    screenOps && (effective == PrivilegeLevel.ADB || effective == PrivilegeLevel.ROOT)

private fun RootStatus.toRootToolStatus(): RootToolStatus = when (this) {
    RootStatus.Unsupported -> RootToolStatus.Unsupported
    RootStatus.NotRequested -> RootToolStatus.NotRequested
    RootStatus.Granted -> RootToolStatus.Granted
    RootStatus.Denied -> RootToolStatus.Denied
    RootStatus.Timeout -> RootToolStatus.Timeout
    RootStatus.Error -> RootToolStatus.Error
}
