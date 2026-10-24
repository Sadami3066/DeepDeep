package io.github.whywood.deepdeep

import android.content.Context
import dev.pi.ai.auth.oauth.OAuthRuntime
import dev.pi.ai.http.okHttpTransport
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.data.FileAttachmentStore
import io.github.whywood.deepdeep.data.FontPreferences
import io.github.whywood.deepdeep.data.LegacyWorkspaceMigration
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.StorageMigration
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.plugin.AndroidPluginStrings
import io.github.whywood.deepdeep.plugin.feishu.FeishuPlugin
import io.github.whywood.deepdeep.plugin.websearch.WebSearchPlugin
import io.github.whywood.deepdeep.plugin.PluginDownloader
import io.github.whywood.deepdeep.plugin.PluginManager
import io.github.whywood.deepdeep.plugin.PluginRecordStore
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
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.GoalStore
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.TurnSource
import io.github.whywood.deepdeep.runtime.goalTools
import io.github.whywood.deepdeep.runtime.jobTools
import io.github.whywood.deepdeep.runtime.presentTool
import io.github.whywood.deepdeep.runtime.AppToolContext
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.PiSessionEngine
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.ScheduleService
import io.github.whywood.deepdeep.runtime.ScheduleStore
import io.github.whywood.deepdeep.runtime.WorkspaceStore
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SessionCatalog
import io.github.whywood.deepdeep.runtime.SessionMetaStore
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.service.LinuxJobRunner
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxShellExecutor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobWake = MutableStateFlow(true)
    private val sharedStorage = MutableStateFlow(false)

    init {
        StorageMigration.migrate(appContext.filesDir)
        OAuthRuntime.callbackServerFactory = DeepDeepOAuthCallbackServerFactory()
    }

    val homeDir: File = File(appContext.filesDir, "home").apply { mkdirs() }
    val workspacesDir: File = File(appContext.filesDir, "workspaces").apply { mkdirs() }
    val agentDir: File = File(appContext.filesDir, "agent").apply { mkdirs() }

    private val secretStore = EncryptedSecretStore(appContext)
    val apiKeyStore = ApiKeyStore(secretStore)
    val oauthCredentialStore = OAuthCredentialStore(secretStore)
    val modelConfigStore = ModelConfigStore(appContext)
    val attachmentStore = FileAttachmentStore(appContext, homeDir)
    val linuxSettings = LinuxSettingsStore(appContext)
    val themePreferences = ThemePreferences(appContext)
    val appPreferences = AppPreferences(appContext)

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
    val personalizationStore = PersonalizationStore(appContext)
    val fontPreferences = FontPreferences(appContext)
    val linuxEnvironment = LinuxEnvironment(
        appContext,
        homeDir,
        sharedStorageEnabled = { sharedStorage.value },
    )
    private val httpTransport = okHttpTransport()
    val credentials = SdkCredentialStore(apiKeyStore, oauthCredentialStore, scope)
    val providerCatalog = ProviderCatalog(httpTransport, credentials)
    val providerOAuthService = ProviderOAuthService(
        credentials = credentials,
        transport = httpTransport,
        deviceId = { installationId.get() },
    )
    private val installationId = InstallationIdStore(agentDir)
    val mcpConfigStore = McpConfigStore(agentDir)
    private val mcpTokenStore = FileMcpTokenStore(defaultMcpAuthFile(agentDir.absolutePath.toPath()))
    val mcpServerProbe = McpServerProbe(httpTransport, mcpTokenStore)
    val mcpOAuthService = McpOAuthService(httpTransport, mcpTokenStore)
    val sessionMetaStore = SessionMetaStore(appContext)
    val sessionCatalog = SessionCatalog(agentDir, sessionMetaStore)
    val workspaceStore = WorkspaceStore(appContext)
    val skillRepository = SkillRepository(
        repoDir = File(appContext.filesDir, "skills"),
        agentDir = agentDir,
        homeDir = homeDir,
        workspacesDir = workspacesDir,
    )
    val agentDocStore = AgentDocStore()
    val scheduleStore = ScheduleStore(File(appContext.filesDir, "schedules"))
    val goalService: GoalService by lazy { GoalService(GoalStore(File(appContext.filesDir, "goals")), scope) }
    val jobRegistry: JobRegistry by lazy {
        JobRegistry(
            scope = scope,
            notifier = { sessionId, notice, prompt ->
                host.deliverBackgroundTurn(sessionId, notice, prompt, TurnSource.JOB)
            },
            wakeEnabled = { jobWake.value },
            runnerFactory = { cwd -> LinuxJobRunner(linuxEnvironmentFor(cwd)) },
        )
    }
    val questionCenter = QuestionCenter(isForeground = { AppVisibility.foreground })
    val pluginManager: PluginManager by lazy {
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
            ),
            recordStore = PluginRecordStore(File(appContext.filesDir, "plugins/plugins.json")),
            skillStore = PluginSkillStore(
                skillsDir = File(agentDir, "skills"),
                repoDir = File(appContext.filesDir, "skills"),
            ),
            strings = AndroidPluginStrings(appContext),
            scope = scope,
            isForeground = { AppVisibility.foreground },
            notifyInstall = { label, stage, progress ->
                notificationCenter.notifyInstall(label, stage, progress)
            },
            notifyInstallFinished = { label, success, error ->
                notificationCenter.notifyInstallFinished(label, success, error)
            },
            onContributionsChanged = {
                scope.launch {
                    host.busySessionIds.first { it.isEmpty() }
                    host.reconfigure()
                }
            },
            onSetupReady = { _ ->
                if (!AppVisibility.foreground) scope.launch { AppVisibility.bringToFront(appContext) }
            },
        )
    }
    private val appToolContext: AppToolContext by lazy {
        AppToolContext(
            scheduleService = scheduleService,
            questionCenter = questionCenter,
            pluginController = pluginManager,
            pluginTools = { pluginManager.tools() },
            extraToolFactories = listOf(
                { binding -> goalTools(binding, goalService) },
                { binding -> jobTools(binding, jobRegistry) },
                { binding -> listOf(presentTool { binding.current() }) },
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
                )
            }
        }
    }

    private val shellExecutorFor: suspend (File) -> LinuxShellExecutor? = { cwd ->
        if (!linuxSettings.isEnabled()) {
            null
        } else {
            LinuxShellExecutor(linuxEnvironmentFor(cwd.absolutePath))
        }
    }

    private val engineFactory: suspend (File) -> PiSessionEngine = { cwd ->
        PiSessionEngine(
            workspace = cwd,
            agentDir = agentDir,
            credentials = credentials,
            shellExecutor = shellExecutorFor(cwd),
            providers = providerCatalog,
            personalization = { personalizationStore.read().toRuntime() },
            sharedStorage = { linuxSettings.isSharedStorageEnabled() && isSharedStorageGranted() },
            pluginPromptBody = { pluginManager.promptBody() },
            toolContext = appToolContext,
        )
    }

    val host = AgentRuntimeHost(
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
    )

    val notificationCenter by lazy { NotificationCenter(appContext, host) }
    val scheduleService by lazy { ScheduleService(host, scheduleStore, scope) }
    val devPackageManager by lazy { DevPackageManager(appContext, linuxEnvironment, notificationCenter) }

    init {
        scope.launch { pluginManager.start() }
        scope.launch {
            host.events.collect { event ->
                when (event.event) {
                    is RuntimeEvent.RunFinished, is RuntimeEvent.RunFailed -> pluginManager.refreshIfStale()
                    else -> Unit
                }
            }
        }
    }

    suspend fun prepare() {
        LegacyWorkspaceMigration.migrate(appContext, workspaceStore)
        importDebugKey()
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
