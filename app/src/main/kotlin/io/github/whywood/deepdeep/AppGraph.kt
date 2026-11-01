package io.github.whywood.deepdeep

import android.content.Context
import dev.pi.ai.auth.oauth.OAuthRuntime
import dev.pi.ai.http.okHttpTransport
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.data.FileAttachmentStore
import io.github.whywood.deepdeep.data.FontPreferences
import io.github.whywood.deepdeep.data.LegacyWorkspaceMigration
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.PrivilegePreferences
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.ShareStaging
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.StorageMigration
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.data.VoicePreferences
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.plugin.AndroidPluginStrings
import io.github.whywood.deepdeep.plugin.arrange.ArrangePlugin
import io.github.whywood.deepdeep.plugin.feishu.FeishuPlugin
import io.github.whywood.deepdeep.plugin.gui.GuiPlugin
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenPlugin
import io.github.whywood.deepdeep.plugin.micloud.MiCloudPlugin
import io.github.whywood.deepdeep.plugin.wecom.WecomPlugin
import io.github.whywood.deepdeep.plugin.websearch.WebSearchPlugin
import io.github.whywood.deepdeep.plugin.internal.GoalPlugin
import io.github.whywood.deepdeep.plugin.internal.JobsPlugin
import io.github.whywood.deepdeep.plugin.internal.SchedulePlugin
import io.github.whywood.deepdeep.plugin.internal.WorkshopPlugin
import io.github.whywood.deepdeep.plugin.PluginDownloader
import io.github.whywood.deepdeep.plugin.PluginManager
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.adb.AdbBridgeRuntime
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeControllerImpl
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.privilege.shellBackendsFor
import io.github.whywood.deepdeep.plugin.PluginRecordStore
import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.ApiKeyStore
import io.github.whywood.deepdeep.runtime.DeepDeepOAuthCallbackServerFactory
import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import io.github.whywood.deepdeep.runtime.InstallationIdStore
import io.github.whywood.deepdeep.runtime.OAuthCredentialStore
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpServerProbe
import dev.pi.mcp.oauth.FileMcpTokenStore
import dev.pi.mcp.oauth.defaultMcpAuthFile
import okio.Path.Companion.toPath
import io.github.whywood.deepdeep.runtime.ProviderOAuthService
import io.github.whywood.deepdeep.runtime.GoalPhase
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.RootCommandExecutor
import io.github.whywood.deepdeep.runtime.RootCommandResult
import io.github.whywood.deepdeep.runtime.RootToolStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.rootShellTool
import io.github.whywood.deepdeep.runtime.adbShellTool
import io.github.whywood.deepdeep.runtime.TurnSource
import io.github.whywood.deepdeep.runtime.presentTool
import io.github.whywood.deepdeep.runtime.workshopCreativeLookup
import io.github.whywood.deepdeep.runtime.AppToolContext
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.PiSessionEngine
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.QuestionPersistence
import io.github.whywood.deepdeep.runtime.RunLedger
import io.github.whywood.deepdeep.runtime.ScheduleService
import io.github.whywood.deepdeep.runtime.ScheduleStore
import io.github.whywood.deepdeep.runtime.SkillCatalog
import io.github.whywood.deepdeep.runtime.WorkshopAgentService
import io.github.whywood.deepdeep.runtime.WorkshopStore
import io.github.whywood.deepdeep.runtime.WorkspaceStore
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SessionCatalog
import io.github.whywood.deepdeep.runtime.SessionMetaStore
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.service.LinuxJobRunner
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.RootShell
import io.github.whywood.deepdeep.shell.RootStatus
import io.github.whywood.deepdeep.service.AppShellExecutor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppGraph(context: Context) : AppDependencies {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val appScope: CoroutineScope get() = scope
    private val jobWake = MutableStateFlow(true)
    private val sharedStorage = MutableStateFlow(false)
    val activeSessionId = MutableStateFlow<String?>(null)
    private var questionRecoveryLoaded = false

    init {
        scope.launch { StorageMigration.migrate(appContext.filesDir) }
        OAuthRuntime.callbackServerFactory = DeepDeepOAuthCallbackServerFactory()
    }

    override val homeDir: File = File(appContext.filesDir, "home").apply { mkdirs() }
    val workspacesDir: File = File(appContext.filesDir, "workspaces").apply { mkdirs() }
    val agentDir: File = File(appContext.filesDir, "agent").apply { mkdirs() }

    private val secretStore = EncryptedSecretStore(appContext)
    val apiKeyStore = ApiKeyStore(secretStore)
    val oauthCredentialStore = OAuthCredentialStore(secretStore)
    override val modelConfigStore = ModelConfigStore(appContext)
    override val attachmentStore = FileAttachmentStore(appContext, homeDir)
    override val linuxSettings = LinuxSettingsStore(appContext)
    private val rootShell = RootShell()
    val adbBridge: AdbBootstrap = AdbBridgeRuntime.install(appContext)
    private val privilegeImpl by lazy {
        PrivilegeControllerImpl(
            preferences = PrivilegePreferences(appContext) { linuxSettings.isRootEnabled() },
            rootShell = rootShell,
            adbBridge = adbBridge,
            scope = scope,
        ) { host.reconfigure() }
    }
    override val privilege: PrivilegeController get() = privilegeImpl
    val themePreferences = ThemePreferences(appContext)
    override val appPreferences = AppPreferences(appContext)
    override val voicePreferences = VoicePreferences(appContext)
    override val shareStaging = ShareStaging(appContext)

    fun skillBinds(): List<Pair<File, String>> = listOf(
        File(agentDir, "skills").let { it to it.absolutePath },
        File(appContext.filesDir, "skills").let { it to it.absolutePath },
    )

    fun linuxBinds(): List<Pair<File, String>> = listOf(
        appContext.filesDir.let { it to it.absolutePath },
        appContext.cacheDir.let { it to it.absolutePath },
    )

    init {
        scope.launch {
            appPreferences.preferences.collect { preferences -> jobWake.value = preferences.backgroundJobWake }
        }
    }
    init {
        scope.launch {
            linuxSettings.sharedStorageEnabled.collect { enabled ->
                sharedStorage.value = enabled && isSharedStorageGranted()
            }
        }
    }
    override val personalizationStore = PersonalizationStore(appContext)
    val fontPreferences = FontPreferences(appContext)
    override val linuxEnvironment = LinuxEnvironment(
        appContext,
        homeDir,
        sharedStorageEnabled = { sharedStorage.value },
        extraBinds = linuxBinds(),
    )
    private val httpTransport = okHttpTransport()
    override val credentials = SdkCredentialStore(apiKeyStore, oauthCredentialStore, scope)
    override val providerCatalog = ProviderCatalog(httpTransport, credentials)
    override val providerOAuthService = ProviderOAuthService(
        credentials = credentials,
        transport = httpTransport,
        deviceId = { installationId.get() },
    )
    private val installationId = InstallationIdStore(agentDir)
    override val mcpConfigStore = McpConfigStore(agentDir)
    private val mcpTokenStore = FileMcpTokenStore(defaultMcpAuthFile(agentDir.absolutePath.toPath()))
    override val mcpServerProbe = McpServerProbe(httpTransport, mcpTokenStore)
    override val mcpOAuthService = McpOAuthService(httpTransport, mcpTokenStore)
    val sessionMetaStore = SessionMetaStore(appContext)
    val sessionCatalog = SessionCatalog(agentDir, sessionMetaStore)
    val workspaceStore = WorkspaceStore(appContext)
    override val skillRepository = SkillRepository(
        repoDir = File(appContext.filesDir, "skills"),
        agentDir = agentDir,
        homeDir = homeDir,
        workspacesDir = workspacesDir,
    )
    override val agentDocStore = AgentDocStore()
    val scheduleStore = ScheduleStore(File(appContext.filesDir, "schedules"))
    override val workshopStore = WorkshopStore(File(appContext.filesDir, "workshop"))
    override val skillCatalog = SkillCatalog(agentDir)
    val pluginSettingsStore = PluginSettingsStore(File(appContext.filesDir, "plugins/settings.json"))
    private val pluginStrings = AndroidPluginStrings(appContext)
    override val goalService: GoalService by lazy { GoalService() }
    override val jobRegistry: JobRegistry by lazy {
        JobRegistry(
            scope = scope,
            notifier = { sessionId, notice, prompt ->
                host.deliverBackgroundTurn(sessionId, notice, prompt, TurnSource.JOB)
            },
            wakeEnabled = { jobWake.value },
            runnerFactory = { cwd -> LinuxJobRunner(linuxEnvironmentFor(cwd)) },
            maxRunningPerSession = { pluginSettingsStore.cachedInt("jobs", "max_running", 10) },
        )
    }
    override val questionCenter = QuestionCenter(
        persistence = QuestionPersistence(File(appContext.filesDir, "questions")),
    )
    override val pluginManager: PluginManager by lazy {
        PluginManager(
            plugins = listOf(
                FeishuPlugin(
                    context = appContext,
                    linux = linuxEnvironment,
                    linuxSettings = linuxSettings,
                    downloader = PluginDownloader(),
                    cacheDir = appContext.cacheDir,
                ),
                WebSearchPlugin(
                    context = appContext,
                    apiKeyProvider = {
                        credentials.cachedApiKey(ProviderCatalog.DEFAULT_PROVIDER_ID) ?: run {
                            credentials.preload(ProviderCatalog.DEFAULT_PROVIDER_ID)
                            credentials.cachedApiKey(ProviderCatalog.DEFAULT_PROVIDER_ID)
                        }
                    },
                ),
                GuiPlugin(
                    context = appContext,
                    screenshotDir = File(appContext.filesDir, "gui/screenshots"),
                    mainShell = io.github.whywood.deepdeep.screen.PrivilegedMainScreenShell(privilege, rootShell, adbBridge),
                ),
                VirtualScreenPlugin(
                    context = appContext,
                    privilege = privilege,
                    adb = adbBridge,
                ),
                ArrangePlugin(context = appContext),
                MiCloudPlugin(
                    context = appContext,
                    onCapabilitiesChanged = {
                        scope.launch { host.reconfigure() }
                    },
                    onSessionInvalidated = {
                        scope.launch { pluginManager.refresh() }
                    },
                ),
                WecomPlugin(
                    context = appContext,
                    linux = linuxEnvironment,
                    linuxSettings = linuxSettings,
                    downloader = PluginDownloader(),
                    workspaceDir = homeDir,
                ),
                GoalPlugin(goalService, pluginStrings),
                JobsPlugin(jobRegistry, pluginStrings),
                SchedulePlugin(scheduleService, pluginStrings),
                WorkshopPlugin(workshopStore, appContext.assets, pluginStrings),
            ),
            recordStore = PluginRecordStore(File(appContext.filesDir, "plugins/plugins.json")),
            skillStore = PluginSkillStore(
                skillsDir = File(agentDir, "skills"),
                repoDir = File(appContext.filesDir, "skills"),
            ),
            settingsStore = pluginSettingsStore,
            strings = pluginStrings,
            scope = scope,
            isForeground = { AppVisibility.foreground },
            notifyInstall = { label, stage, progress ->
                notificationCenter.notifyInstall(label, stage, progress)
            },
            notifyInstallFinished = { label, success, error ->
                notificationCenter.notifyInstallFinished(label, success, error)
            },
            onContributionsChanged = {
                scope.launch { host.reconfigure() }
            },
            onSetupReady = { _ ->
                if (!AppVisibility.foreground) scope.launch { AppVisibility.bringToFront(appContext) }
            },
        )
    }
    private val appToolContext: AppToolContext by lazy {
        AppToolContext(
            questionCenter = questionCenter,
            pluginController = pluginManager,
            pluginTools = { binding -> pluginManager.tools(binding) },
            extraToolFactories = listOf(
                { _, binding ->
                    buildList {
                        add(presentTool(workshopCreativeLookup(workshopStore)) { binding.current() })
                        for (backend in availableShellBackends()) {
                            when (backend) {
                                PrivilegeLevel.ROOT -> add(
                                    rootShellTool(
                                        executor = RootCommandExecutor { command, timeoutMillis ->
                                            val result = rootShell.execute(command, timeoutMillis)
                                            RootCommandResult(
                                                exitCode = result.exitCode,
                                                stdout = result.stdout,
                                                stderr = result.stderr,
                                                truncated = result.truncated,
                                                timedOut = result.timedOut,
                                            )
                                        },
                                        enabled = { PrivilegeLevel.ROOT in privilege.state.value.availableBackends },
                                        status = { privilege.state.value.root.toRootToolStatus() },
                                    ),
                                )

                                PrivilegeLevel.ADB -> add(
                                    adbShellTool(
                                        executor = privilegeImpl.adbExecutor,
                                        enabled = { PrivilegeLevel.ADB in privilege.state.value.availableBackends },
                                        status = { adbToolStatus() },
                                    ),
                                )

                                PrivilegeLevel.NORMAL -> Unit
                            }
                        }
                    }
                },
            ),
        )
    }
    private val shellEnvironments = mutableMapOf<String, LinuxEnvironment>()

    private fun linuxEnvironmentFor(cwd: String?): LinuxEnvironment {
        val directory = cwd?.takeIf { it.isNotBlank() }?.let(::File) ?: homeDir
        return synchronized(shellEnvironments) {
            shellEnvironments.getOrPut(directory.absolutePath) {
                LinuxEnvironment(
                    appContext,
                    directory,
                    sharedStorageEnabled = { sharedStorage.value },
                    extraBinds = linuxBinds(),
                )
            }
        }
    }

    private val shellExecutorFor: suspend (File) -> AppShellExecutor? = { cwd ->
        if (!linuxSettings.isEnabled()) {
            null
        } else {
            AppShellExecutor(linuxEnvironmentFor(cwd.absolutePath))
        }
    }

    override val workshopAgentService by lazy {
        WorkshopAgentService(
            providers = providerCatalog,
            credentials = credentials,
            transport = httpTransport,
            agentDir = agentDir,
            runRoot = File(appContext.filesDir, "agent-runs"),
            appToolContext = appToolContext,
            defaultConfig = { modelConfigStore.read() },
            configuredProviders = { credentials.authKinds.value.keys },
            personalization = { personalizationStore.read().toRuntime() },
            pluginPromptBody = { pluginManager.promptBody() },
            sharedStorage = { linuxSettings.isSharedStorageEnabled() && isSharedStorageGranted() },
            defaultMaxTurns = { pluginSettingsStore.cachedInt("workshop", "max_turns", 16) },
            workspace = homeDir,
            shellExecutorFor = shellExecutorFor,
            backgroundRunnerFactory = { cwd -> LinuxJobRunner(linuxEnvironmentFor(cwd)) },
        )
    }

    private val engineFactory: suspend (File) -> PiSessionEngine = { cwd ->
        PiSessionEngine(
            workspace = cwd,
            agentDir = agentDir,
            credentials = credentials,
            transport = httpTransport,
            shellExecutor = shellExecutorFor(cwd),
            providers = providerCatalog,
            personalization = { personalizationStore.read().toRuntime() },
            sharedStorage = { linuxSettings.isSharedStorageEnabled() && isSharedStorageGranted() },
            pluginPromptBody = { pluginManager.promptBody() },
            toolContext = appToolContext,
        )
    }

    override val host = AgentRuntimeHost(
        homeDir = homeDir,
        workspacesDir = workspacesDir,
        engineFactory = engineFactory,
        modelConfigStore = modelConfigStore,
        catalog = sessionCatalog,
        workspaceStore = workspaceStore,
        providers = providerCatalog,
        backgroundScope = scope,
        goalDriver = goalService,
        sessionCleaner = { sessionId: String ->
            jobRegistry.clearSession(sessionId)
            scope.launch { goalService.clearSession(sessionId) }
        },
        sessionArchived = { sessionId -> jobRegistry.clearSession(sessionId) },
        runLedger = RunLedger(File(appContext.filesDir, "tasks")),
        keepHandleAlive = { sessionId ->
            scheduleService.schedules.value.any { schedule ->
                schedule.sessionId == sessionId && schedule.enabled && schedule.nextRunAt != null
            } ||
                goalService.goals.value[sessionId]?.goal?.phase == GoalPhase.ACTIVE ||
                jobRegistry.jobs.value.any { job -> job.sessionId == sessionId && job.status == JobStatus.RUNNING } ||
                questionCenter.pending.value.any { question -> question.sessionId == sessionId }
        },
    )

    val notificationCenter: NotificationCenter by lazy {
        NotificationCenter(appContext, host, questionCenter.pending)
    }
    override val scheduleService: ScheduleService by lazy {
        ScheduleService(
            host,
            scheduleStore,
            scope,
            intentsFile = File(appContext.filesDir, "schedules/delivery-intents.json"),
        )
    }
    override val devPackageManager: DevPackageManager by lazy { DevPackageManager(appContext, linuxEnvironment, notificationCenter) }

    init {
        scope.launch {
            pluginManager.start()
            host.reconfigure()
        }
        scope.launch {
            host.events.collect { event ->
                when (event.event) {
                    is RuntimeEvent.RunFinished, is RuntimeEvent.RunFailed -> {
                        runCatching { skillRepository.syncIfChanged() }
                        runCatching { pluginManager.refreshIfStale(PLUGIN_REFRESH_MIN_INTERVAL_MS) }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun availableShellBackends(): List<PrivilegeLevel> =
        shellBackendsFor(privilege.state.value.effective)

    private fun adbToolStatus(): RootToolStatus {
        val adb = privilege.state.value.adb
        return when {
            adb.daemon -> RootToolStatus.Granted
            adb.busy -> RootToolStatus.NotRequested
            adb.error != null -> RootToolStatus.Error
            adb.paired -> RootToolStatus.NotRequested
            else -> RootToolStatus.Unsupported
        }
    }

    override suspend fun prepare() {
        if (!questionRecoveryLoaded) {
            questionRecoveryLoaded = true
            withContext(Dispatchers.IO) { questionCenter.loadPersisted() }
            host.start()
            notificationCenter.notifyInterruptedWork(questionCenter.interrupted.value, host.interruptedRuns.value)
        }
        withContext(Dispatchers.IO) {
            LegacyWorkspaceMigration.migrate(appContext, workspaceStore)
            importDebugKey()
        }
        credentials.preloadAll()
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

internal const val PLUGIN_REFRESH_MIN_INTERVAL_MS = 15_000L

private fun RootStatus.toRootToolStatus(): RootToolStatus = when (this) {
    RootStatus.Unsupported -> RootToolStatus.Unsupported
    RootStatus.NotRequested -> RootToolStatus.NotRequested
    RootStatus.Granted -> RootToolStatus.Granted
    RootStatus.Denied -> RootToolStatus.Denied
    RootStatus.Timeout -> RootToolStatus.Timeout
    RootStatus.Error -> RootToolStatus.Error
}
