package io.github.whywood.deepdeep

import android.content.Context
import dev.pi.ai.auth.oauth.OAuthRuntime
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.conversation.nextMessageId
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.CapabilitySettingsStore
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.LegacyWorkspaceMigration
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.LocalizedContext
import io.github.whywood.deepdeep.data.OverlayEntryPreferences
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.ShareStaging
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.StorageMigration
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.data.VoicePreferences
import io.github.whywood.deepdeep.graph.DataGraph
import io.github.whywood.deepdeep.graph.DataGraphDeps
import io.github.whywood.deepdeep.graph.PlatformGraph
import io.github.whywood.deepdeep.graph.PlatformGraphDeps
import io.github.whywood.deepdeep.graph.PluginGraph
import io.github.whywood.deepdeep.graph.PluginGraphDeps
import io.github.whywood.deepdeep.graph.RuntimeGraph
import io.github.whywood.deepdeep.graph.RuntimeGraphDeps
import io.github.whywood.deepdeep.graph.ServiceGraph
import io.github.whywood.deepdeep.graph.ServiceGraphDeps
import io.github.whywood.deepdeep.plugin.BuiltinPluginWiring
import io.github.whywood.deepdeep.plugin.manager.PluginHostManager
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.DeepDeepOAuthCallbackServerFactory
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderOAuthService
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SkillCatalog
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.runtime.MiniappAgentService
import io.github.whywood.deepdeep.runtime.MiniappStore
import io.github.whywood.deepdeep.service.Diagnostics
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.service.ProcessRuntime
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppGraph(context: Context) : AppDependencies {
    private val appContext = context.applicationContext
    val uiContext: Context = LocalizedContext(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val appScope: CoroutineScope get() = scope
    val activeSessionId = MutableStateFlow<String?>(null)
    private var questionRecoveryLoaded = false

    private val dataGraph by lazy { DataGraph(DataGraphDeps(appContext, uiContext, scope)) }
    private val platformGraph by lazy { PlatformGraph(platformGraphDeps()) }
    private val runtimeGraph by lazy { RuntimeGraph(runtimeGraphDeps()) }
    private val serviceGraph by lazy { ServiceGraph(serviceGraphDeps()) }

    override val homeDir: File get() = dataGraph.homeDir
    val workspacesDir: File get() = dataGraph.workspacesDir
    val agentDir: File get() = dataGraph.agentDir

    fun linuxBinds(): List<Pair<File, String>> = platformGraph.linuxBinds()

    override val attachmentStore: AttachmentStore get() = dataGraph.attachmentStore
    val themePreferences: ThemePreferences get() = dataGraph.themePreferences
    override val appPreferences: AppPreferences get() = dataGraph.appPreferences
    override val voicePreferences: VoicePreferences get() = dataGraph.voicePreferences
    override val overlayEntryPreferences: OverlayEntryPreferences get() = dataGraph.overlayEntryPreferences
    override val shareStaging: ShareStaging get() = dataGraph.shareStaging
    override val personalizationStore: PersonalizationStore get() = dataGraph.personalizationStore
    val fontPreferences get() = dataGraph.fontPreferences
    override val modelConfigStore: ModelSettings get() = dataGraph.modelConfigStore
    override val linuxSettings: LinuxSettingsStore get() = dataGraph.linuxSettings
    override val credentials: SdkCredentialStore get() = dataGraph.credentials
    override val providerCatalog: ProviderDirectory get() = dataGraph.providerCatalog
    override val providerOAuthService: ProviderOAuthService get() = dataGraph.providerOAuthService
    override val mcpConfigStore: McpConfigStore get() = dataGraph.mcpConfigStore
    override val mcpServerProbe: McpServerProbe get() = dataGraph.mcpServerProbe
    override val mcpOAuthService: McpOAuthService get() = dataGraph.mcpOAuthService
    override val skillRepository: SkillRepository get() = dataGraph.skillRepository
    override val agentDocStore: AgentDocStore get() = dataGraph.agentDocStore
    override val capabilitySettingsStore: CapabilitySettingsStore get() = dataGraph.capabilitySettingsStore
    override val miniappStore: MiniappStore get() = dataGraph.miniappStore
    override val skillCatalog: SkillCatalog get() = dataGraph.skillCatalog
    override val questionCenter: QuestionCenter get() = dataGraph.questionCenter
    override val goalService: GoalService get() = dataGraph.goalService

    override val privilege: PrivilegeController get() = platformGraph.privilege
    val adbBridge get() = platformGraph.adbBridge
    override val linuxEnvironment: LinuxEnvironment get() = platformGraph.linuxEnvironment

    override val host: AgentRuntimeHost get() = runtimeGraph.host
    override val conversationStore: ConversationStore get() = runtimeGraph.conversationStore
    override val miniappAgentService: MiniappAgentService get() = runtimeGraph.miniappAgentService

    val notificationCenter: NotificationCenter get() = serviceGraph.notificationCenter
    override val timerService: TimerService get() = serviceGraph.timerService
    override val jobRegistry: JobRegistry get() = serviceGraph.jobRegistry
    override val devPackageManager: DevPackageManager get() = serviceGraph.devPackageManager
    override val processRuntime: ProcessRuntime get() = serviceGraph.processRuntime

    private val builtinWiring: BuiltinPluginWiring by lazy {
        BuiltinPluginWiring(
            uiContext = uiContext,
            cacheDir = appContext.cacheDir,
            linux = platformGraph.linuxEnvironment,
            linuxSettings = dataGraph.linuxSettings,
            workspaceDir = homeDir,
            onCapabilitiesChanged = { scope.launch { host.reconfigure() } },
            onSessionInvalidated = { scope.launch { pluginManager.refresh() } },
        )
    }
    private val pluginGraph: PluginGraph by lazy { PluginGraph(pluginGraphDeps()) }

    override val pluginManager: PluginHostManager get() = pluginGraph.manager

    init {
        scope.launch { StorageMigration.migrate(appContext.filesDir) }
        OAuthRuntime.callbackServerFactory = DeepDeepOAuthCallbackServerFactory()
        scope.launch {
            host.sdkEvents.collect { event ->
                pluginManager.onSessionSdkEvent(event.sessionId, event.event)
            }
        }
        scope.launch {
            host.events.collect { event ->
                pluginManager.onRuntimeEvent(event)
                when (event.event) {
                    is RuntimeEvent.RunFinished, is RuntimeEvent.RunFailed -> {
                        runCatching { skillRepository.syncIfChanged() }.onFailure {
                            Diagnostics.log(appContext, "app: skill sync failed", it)
                        }
                        runCatching { pluginManager.refreshIfStale(ProcessRuntime.PLUGIN_REFRESH_MIN_INTERVAL_MS) }.onFailure {
                            Diagnostics.log(appContext, "app: plugin refresh failed", it)
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun platformGraphDeps(): PlatformGraphDeps = PlatformGraphDeps(
        appContext = appContext,
        uiContext = uiContext,
        scope = scope,
        homeDir = homeDir,
        sharedStorage = dataGraph.sharedStorage,
        linuxSettings = dataGraph.linuxSettings,
        httpTransport = dataGraph.httpTransport,
        mcpTokenStore = dataGraph.mcpTokenStore,
        pluginManager = { pluginManager },
        host = { host },
    )

    private fun runtimeGraphDeps(): RuntimeGraphDeps = RuntimeGraphDeps(
        appContext = appContext,
        scope = scope,
        homeDir = homeDir,
        workspacesDir = workspacesDir,
        agentDir = agentDir,
        modelConfigStore = dataGraph.modelConfigStore,
        sessionCatalog = dataGraph.sessionCatalog,
        workspaceStore = dataGraph.workspaceStore,
        providerCatalog = dataGraph.providerCatalog,
        credentials = dataGraph.credentials,
        httpTransport = dataGraph.httpTransport,
        personalizationStore = dataGraph.personalizationStore,
        linuxSettings = dataGraph.linuxSettings,
        questionCenter = dataGraph.questionCenter,
        goalService = dataGraph.goalService,
        miniappStore = dataGraph.miniappStore,
        rootShell = platformGraph.rootShell,
        adbExecutor = platformGraph.adbExecutor,
        privilege = { platformGraph.privilege },
        pluginManager = { pluginManager },
        shellExecutorFor = platformGraph.shellExecutorFor,
        linuxEnvironmentFor = platformGraph::linuxEnvironmentFor,
        decorateInterrupted = ::decorateInterrupted,
        timerService = { serviceGraph.timerService },
        jobRegistry = { serviceGraph.jobRegistry },
        capabilitySettingsStore = dataGraph.capabilitySettingsStore,
        apiKeyProvider = {
            dataGraph.credentials.cachedApiKey(ProviderCatalog.DEFAULT_PROVIDER_ID) ?: run {
                dataGraph.credentials.preload(ProviderCatalog.DEFAULT_PROVIDER_ID)
                dataGraph.credentials.cachedApiKey(ProviderCatalog.DEFAULT_PROVIDER_ID)
            }
        },
        screenTools = { platformGraph.screenToolset.tools() },
        virtualScreenTools = { platformGraph.virtualScreenFeature.tools() },
    )

    private fun serviceGraphDeps(): ServiceGraphDeps = ServiceGraphDeps(
        appContext = appContext,
        uiContext = uiContext,
        scope = scope,
        host = { host },
        conversationStore = { conversationStore },
        questionCenter = dataGraph.questionCenter,
        timerStore = dataGraph.timerStore,
        linuxEnvironment = platformGraph.linuxEnvironment,
        pluginManager = { pluginManager },
        skillRepository = dataGraph.skillRepository,
        bundledSkills = dataGraph.bundledSkills,
        prepare = ::prepare,
        jobWake = dataGraph.jobWake,
        linuxEnvironmentFor = platformGraph::linuxEnvironmentFor,
    )

    private fun pluginGraphDeps(): PluginGraphDeps = PluginGraphDeps(
        appContext = appContext,
        uiContext = uiContext,
        scope = scope,
        agentDir = agentDir,
        homeDir = homeDir,
        pluginSettingsStore = dataGraph.pluginSettingsStore,
        pluginStrings = dataGraph.pluginStrings,
        host = host,
        installationId = dataGraph.installationId,
        linuxEnvironment = platformGraph.linuxEnvironment,
        privilege = { platformGraph.privilege },
        rootShell = platformGraph.rootShell,
        adbBridge = platformGraph.adbBridge,
        secretStore = dataGraph.secretStore,
        httpTransport = dataGraph.httpTransport,
        activeSessionId = activeSessionId,
        pluginBus = dataGraph.pluginBus,
        mcpConfigStore = dataGraph.mcpConfigStore,
        mcpServerProbe = dataGraph.mcpServerProbe,
        miniappStore = dataGraph.miniappStore,
        builtinWiring = { builtinWiring },
        notificationCenter = { serviceGraph.notificationCenter },
        miniappAgentService = { runtimeGraph.miniappAgentService },
        jobRegistry = { serviceGraph.jobRegistry },
        timerService = { serviceGraph.timerService },
        goalService = { dataGraph.goalService },
        virtualScreenFeature = { platformGraph.virtualScreenFeature },
        pluginScreenController = { platformGraph.pluginScreenController },
        pluginMainScreenController = { platformGraph.pluginMainScreenController },
        mcpPluginBridge = { platformGraph.mcpPluginBridge },
    )

    override suspend fun prepare() {
        if (!questionRecoveryLoaded) {
            questionRecoveryLoaded = true
            withContext(Dispatchers.IO) { questionCenter.loadPersisted() }
            host.start()
            notificationCenter.notifyInterruptedWork(questionCenter.interrupted.value, host.interruptedRuns.value)
        }
        withContext(Dispatchers.IO) {
            LegacyWorkspaceMigration.migrate(appContext, dataGraph.workspaceStore)
            importDebugKey()
        }
        credentials.preloadAll()
        dataGraph.modelSelection.ensure(credentials.authKinds.value.keys)
    }

    private suspend fun decorateInterrupted(state: ConversationState, sessionId: String): ConversationState {
        var next = state
        val questions = questionCenter.interrupted.value.count { it.sessionId == sessionId }
        if (questions > 0 && next.messages.none { it.notice is TurnNotice.InterruptedQuestions }) {
            next = next.copy(
                messages = next.messages + noticeMessage(next.messages, TurnNotice.InterruptedQuestions(questions)),
            )
        }
        val runs = host.interruptedRuns.value.count { it.sessionId == sessionId }
        if (runs > 0 && next.messages.none { it.notice is TurnNotice.InterruptedRun }) {
            next = next.copy(
                messages = next.messages + noticeMessage(next.messages, TurnNotice.InterruptedRun(runs)),
            )
        }
        return next
    }

    private fun noticeMessage(messages: List<AssistantMessage>, notice: TurnNotice): AssistantMessage {
        val now = System.currentTimeMillis()
        return AssistantMessage(
            id = nextMessageId(messages),
            role = AssistantRole.ASSISTANT,
            text = "",
            status = MessageStatus.DONE,
            startedAt = now,
            finishedAt = now,
            notice = notice,
        )
    }

    private suspend fun importDebugKey() {
        if (!BuildConfig.DEBUG) return
        val directory = File(appContext.filesDir, "import")
        val keyFile = File(directory, "deepseek.key")
        if (!keyFile.isFile) return
        val key = keyFile.readText().trim()
        if (key.isNotEmpty()) {
            credentials.writeApiKey(ProviderCatalog.DEFAULT_PROVIDER_ID, key, null)
        }
        keyFile.delete()
        directory.delete()
    }
}
