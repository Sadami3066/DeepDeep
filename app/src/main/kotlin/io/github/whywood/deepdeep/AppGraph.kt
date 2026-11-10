package io.github.whywood.deepdeep

import android.content.Context
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthRuntime
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.runtime.memory.MemoryRecap
import io.github.whywood.deepdeep.runtime.memory.MemoryStore
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.conversation.nextMessageId
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.BundledSkills
import io.github.whywood.deepdeep.data.CapabilitySettingsStore
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.LegacyWorkspaceMigration
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.StorageMountStore
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.data.LocalizedContext
import io.github.whywood.deepdeep.data.OverlayEntryPreferences
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.ShareStaging
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.StorageMigration
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.data.VoicePreferences
import io.github.whywood.deepdeep.data.remote.RemoteConnection
import io.github.whywood.deepdeep.graph.DataGraph
import io.github.whywood.deepdeep.graph.DataGraphDeps
import io.github.whywood.deepdeep.graph.PlatformGraph
import io.github.whywood.deepdeep.graph.PlatformGraphDeps
import io.github.whywood.deepdeep.graph.PluginGraph
import io.github.whywood.deepdeep.graph.PluginGraphDeps
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.market.MarketRepositoryImpl
import io.github.whywood.deepdeep.graph.RuntimeGraph
import io.github.whywood.deepdeep.graph.RuntimeGraphDeps
import io.github.whywood.deepdeep.graph.ServiceGraph
import io.github.whywood.deepdeep.graph.ServiceGraphDeps
import io.github.whywood.deepdeep.graph.isScreenOpsActive
import io.github.whywood.deepdeep.plugin.manager.PluginHostManager
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.host.HostAccessController
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
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
import io.github.whywood.deepdeep.service.RootBashController
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.shouldAutoInstallLinux
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
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

    fun linuxBinds(): () -> List<Pair<File, String>> = platformGraph.linuxBinds()

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

    override val storageMounts: StorageMountStore get() = dataGraph.storageMountStore
    override val workspaceToolPolicies: WorkspaceToolPolicyStore get() = dataGraph.workspaceToolPolicies
    override val credentials: SdkCredentialStore get() = dataGraph.credentials
    override val providerCatalog: ProviderDirectory get() = dataGraph.providerCatalog
    override val providerOAuthService: ProviderOAuthService get() = dataGraph.providerOAuthService
    override val mcpConfigStore: McpConfigStore get() = dataGraph.mcpConfigStore
    override val mcpServerProbe: McpServerProbe get() = dataGraph.mcpServerProbe
    override val mcpOAuthService: McpOAuthService get() = dataGraph.mcpOAuthService
    override val skillRepository: SkillRepository get() = dataGraph.skillRepository
    override val agentDocStore: AgentDocStore get() = dataGraph.agentDocStore
    override val capabilitySettingsStore: CapabilitySettingsStore get() = dataGraph.capabilitySettingsStore
    override val webSearchSettings: io.github.whywood.deepdeep.data.WebSearchSettingsStore get() = dataGraph.webSearchSettings
    override val miniappStore: MiniappStore get() = dataGraph.miniappStore
    override val skillCatalog: SkillCatalog get() = dataGraph.skillCatalog
    override val questionCenter: QuestionCenter get() = dataGraph.questionCenter
    override val goalService: GoalService get() = dataGraph.goalService
    override val remoteConnection: RemoteConnection get() = dataGraph.remoteConnection

    override val privilege: PrivilegeController get() = platformGraph.privilege
    override val rootBash: RootBashController get() = platformGraph.rootBash
    override val hostAccess: HostAccessController get() = platformGraph.hostAccess
    override val hostEnvironmentProvider: HostEnvironmentProvider get() = platformGraph.hostEnvironmentProvider
    val adbBridge get() = platformGraph.adbBridge
    override val linuxEnvironment: LinuxEnvironment get() = platformGraph.linuxEnvironment

    override val host: AgentRuntimeHost get() = runtimeGraph.host
    override val conversationStore: ConversationStore get() = runtimeGraph.conversationStore
    override val memory: MemoryStore get() = runtimeGraph.memory
    override val memoryRecap: MemoryRecap get() = runtimeGraph.memoryRecapService
    override val miniappAgentService: MiniappAgentService get() = runtimeGraph.miniappAgentService

    val notificationCenter: NotificationCenter get() = serviceGraph.notificationCenter
    override val timerService: TimerService get() = serviceGraph.timerService
    override val jobRegistry: JobRegistry get() = serviceGraph.jobRegistry
    override val devPackageManager: DevPackageManager get() = serviceGraph.devPackageManager
    override val processRuntime: ProcessRuntime get() = serviceGraph.processRuntime

    private val pluginGraph: PluginGraph by lazy { PluginGraph(pluginGraphDeps()) }

    override val pluginManager: PluginHostManager get() = pluginGraph.manager

    override val marketRepository: MarketRepository by lazy {
        MarketRepositoryImpl(
            context = appContext,
            scope = scope,
            skillRepository = skillRepository,
            pluginManager = pluginManager,
            mcpConfigStore = mcpConfigStore,
        )
    }

    init {
        scope.launch { StorageMigration.migrate(appContext.filesDir) }
        scope.launch {
            val environment = linuxEnvironment
            if (!shouldAutoInstallLinux(environment.isSupported, environment.state.value)) return@launch
            runCatching { environment.install() }.onFailure {
                Diagnostics.log(appContext, "app: linux environment preparation failed", it)
            }
        }
        scope.launch {
            runCatching { marketRepository.refresh() }.onFailure {
                Diagnostics.log(appContext, "app: market refresh failed", it)
            }
        }
        scope.launch {
            runCatching { hostAccess.refresh() }.onFailure {
                Diagnostics.log(appContext, "app: host access refresh failed", it)
            }
        }
        OAuthRuntime.callbackServerFactory = DeepDeepOAuthCallbackServerFactory()
        scope.launch {
            host.sdkEvents.collect { event ->
                pluginManager.onSessionSdkEvent(event.sessionId, event.event)
            }
        }
        scope.launch {
            capabilitySettingsStore.screenOps.drop(1).collect {
                syncBundledSkills()
                host.reconfigure()
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
        storageMountStore = dataGraph.storageMountStore,
        httpTransport = dataGraph.httpTransport,
        mcpTokenStore = dataGraph.mcpTokenStore,
        pluginManager = { pluginManager },
        host = { host },
        syncBundledSkills = { syncBundledSkills() },
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
        storageMountStore = dataGraph.storageMountStore,
        workspaceToolPolicies = dataGraph.workspaceToolPolicies,
        questionCenter = dataGraph.questionCenter,
        goalService = dataGraph.goalService,
        miniappStore = dataGraph.miniappStore,
        privilege = { platformGraph.privilege },
        pluginManager = { pluginManager },
        hostAccess = hostAccess,
        hostEnvironment = hostEnvironmentProvider,
        shellExecutorFor = platformGraph.shellExecutorFor,
        linuxEnvironmentFor = platformGraph::linuxEnvironmentFor,
        decorateInterrupted = ::decorateInterrupted,
        timerService = { serviceGraph.timerService },
        jobRegistry = { serviceGraph.jobRegistry },
        capabilitySettingsStore = dataGraph.capabilitySettingsStore,
        appPreferences = dataGraph.appPreferences,
        apiKeyProvider = {
            dataGraph.credentials.cachedApiKey(ProviderCatalog.DEFAULT_PROVIDER_ID) ?: run {
                dataGraph.credentials.preload(ProviderCatalog.DEFAULT_PROVIDER_ID)
                dataGraph.credentials.cachedApiKey(ProviderCatalog.DEFAULT_PROVIDER_ID)
            }
        },
        webSearchKeyProvider = { providerId -> dataGraph.webSearchSettings.key(providerId) },
        postCustomMessage = { sessionId, customType, payload, text ->
            host.postPluginMessage(sessionId, customType, payload, text)
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
        installBundledSkills = { syncBundledSkills() },
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

    private suspend fun syncBundledSkills() {
        runCatching {
            dataGraph.bundledSkills.sync { id ->
                id != BundledSkills.SCREEN_OPS_SKILL || screenOpsActive()
            }
        }.onFailure {
            Diagnostics.log(appContext, "app: bundled skill sync failed", it)
        }
    }

    private fun screenOpsActive(): Boolean = isScreenOpsActive(
        screenOps = capabilitySettingsStore.screenOps.value,
        effective = privilege.state.value.effective,
    )

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
