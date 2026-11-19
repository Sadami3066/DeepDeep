package io.github.whywood.superpi.graph

import io.github.whywood.superpi.sdk.ai.http.okHttpTransport
import io.github.whywood.superpi.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.superpi.sdk.mcp.oauth.defaultMcpAuthFile
import io.github.whywood.superpi.core.ModelSelection
import io.github.whywood.superpi.data.AgentDocStore
import io.github.whywood.superpi.data.AppPreferences
import io.github.whywood.superpi.data.appHomeDirectory
import io.github.whywood.superpi.data.BundledSkills
import io.github.whywood.superpi.data.CustomProviderStore
import io.github.whywood.superpi.data.toProviderConfig
import io.github.whywood.superpi.data.FileAttachmentStore
import io.github.whywood.superpi.data.HostFiles
import io.github.whywood.superpi.data.FontPreferences
import io.github.whywood.superpi.data.LinuxSettingsStore
import io.github.whywood.superpi.data.StorageMountStore
import io.github.whywood.superpi.data.WorkspaceToolPolicyStore
import io.github.whywood.superpi.data.OverlayEntryPreferences
import io.github.whywood.superpi.data.PersonalizationStore
import io.github.whywood.superpi.data.ShareStaging
import io.github.whywood.superpi.data.SkillRepository
import io.github.whywood.superpi.data.ThemePreferences
import io.github.whywood.superpi.data.VoicePreferences
import io.github.whywood.superpi.data.remote.RemoteBrowserClient
import io.github.whywood.superpi.data.remote.RemoteComputerStore
import io.github.whywood.superpi.data.remote.RemoteConnection
import io.github.whywood.superpi.data.remote.RemoteGatewayClient
import io.github.whywood.superpi.data.remote.RemoteSecrets
import io.github.whywood.superpi.data.WebSearchSettingsStore
import io.github.whywood.superpi.data.isSharedStorageGranted
import io.github.whywood.superpi.plugin.AndroidPluginStrings
import io.github.whywood.superpi.plugin.host.services.PluginBusImpl
import io.github.whywood.superpi.runtime.ApiKeyStore
import io.github.whywood.superpi.runtime.EncryptedSecretStore
import io.github.whywood.superpi.runtime.GoalService
import io.github.whywood.superpi.runtime.InstallationIdStore
import io.github.whywood.superpi.runtime.McpConfigStore
import io.github.whywood.superpi.runtime.McpOAuthService
import io.github.whywood.superpi.runtime.McpServerProbe
import io.github.whywood.superpi.runtime.ModelConfigStore
import io.github.whywood.superpi.runtime.OAuthCredentialStore
import io.github.whywood.superpi.runtime.ProviderCatalog
import io.github.whywood.superpi.runtime.ProviderOAuthService
import io.github.whywood.superpi.runtime.QuestionCenter
import io.github.whywood.superpi.runtime.QuestionPersistence
import io.github.whywood.superpi.runtime.SdkCredentialStore
import io.github.whywood.superpi.runtime.SessionCatalog
import io.github.whywood.superpi.runtime.SessionMetaStore
import io.github.whywood.superpi.runtime.SkillCatalog
import io.github.whywood.superpi.runtime.TimerStore
import io.github.whywood.superpi.runtime.MiniappStore
import io.github.whywood.superpi.runtime.WorkspaceStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

internal class DataGraph(private val deps: DataGraphDeps) {
    val homeDir: File = appHomeDirectory(deps.appContext).apply { mkdirs() }
    val workspacesDir: File = File(deps.appContext.filesDir, "workspaces").apply { mkdirs() }
    val agentDir: File = File(deps.appContext.filesDir, "agent").apply { mkdirs() }

    val jobWake = MutableStateFlow(true)
    val sharedStorage = MutableStateFlow(false)

    val secretStore = EncryptedSecretStore(deps.appContext)
    val apiKeyStore = ApiKeyStore(secretStore)
    val oauthCredentialStore = OAuthCredentialStore(secretStore)
    val modelConfigStore = ModelConfigStore(deps.appContext)
    val hostFiles = HostFiles(deps.hostEnvironmentProvider, deps.stagingRoot)
    val attachmentStore = FileAttachmentStore(deps.appContext, homeDir, hostFiles) { sharedStorage.value }
    val linuxSettings = LinuxSettingsStore(deps.appContext)
    val storageMountStore = StorageMountStore(File(deps.appContext.filesDir, "storage-mounts.json"))
    val workspaceToolPolicies = WorkspaceToolPolicyStore(File(deps.appContext.filesDir, "workspace-tool-policies.json"))
    val remoteComputerStore = RemoteComputerStore(File(deps.appContext.filesDir, "remote-computers.json"))
    val remoteConnection = RemoteConnection(
        store = remoteComputerStore,
        secrets = RemoteSecrets(secretStore),
        browser = RemoteBrowserClient(deps.appContext),
        client = RemoteGatewayClient(),
        scope = deps.scope,
    )
    val themePreferences = ThemePreferences(deps.appContext)
    val appPreferences = AppPreferences(deps.appContext)
    val voicePreferences = VoicePreferences(deps.appContext)
    val overlayEntryPreferences = OverlayEntryPreferences(deps.appContext)
    val shareStaging = ShareStaging(deps.uiContext)
    val personalizationStore = PersonalizationStore(deps.appContext)
    val fontPreferences = FontPreferences(deps.appContext)

    val httpTransport = okHttpTransport()
    val credentials = SdkCredentialStore(apiKeyStore, oauthCredentialStore, deps.scope)
    val customProviderStore = CustomProviderStore(File(agentDir, "models.json"))
    val providerCatalog = ProviderCatalog(
        httpTransport,
        credentials,
        customProviderStore.entries.value.map { entry -> entry.toProviderConfig() },
    )
    val modelSelection = ModelSelection(providerCatalog, modelConfigStore)
    val installationId = InstallationIdStore(agentDir)
    val providerOAuthService = ProviderOAuthService(
        credentials = credentials,
        transport = httpTransport,
        deviceId = { installationId.get() },
    )
    val mcpConfigStore = McpConfigStore(agentDir)
    val mcpTokenStore = FileMcpTokenStore(defaultMcpAuthFile(agentDir.absolutePath.toPath()))
    val mcpServerProbe = McpServerProbe(httpTransport, mcpTokenStore)
    val mcpOAuthService = McpOAuthService(httpTransport, mcpTokenStore)

    val sessionMetaStore = SessionMetaStore(deps.appContext)
    val sessionCatalog = SessionCatalog(agentDir, sessionMetaStore)
    val workspaceStore = WorkspaceStore(deps.appContext)
    val skillRepository = SkillRepository(
        repoDir = File(deps.appContext.filesDir, "skills"),
        agentDir = agentDir,
        homeDir = homeDir,
        workspacesDir = workspacesDir,
    )
    val agentDocStore = AgentDocStore()
    val webSearchSettings = WebSearchSettingsStore(secretStore)
    val bundledSkills = BundledSkills.fromAssets(
        assets = deps.appContext.assets,
        repoDir = File(deps.appContext.filesDir, "skills"),
        activeDir = File(agentDir, "skills"),
    )
    val timerStore = TimerStore(TimerStore.prepareDataDirectory(deps.appContext.filesDir))
    val miniappStore = MiniappStore(File(deps.appContext.filesDir, "miniapp"))
    val skillCatalog = SkillCatalog(agentDir)
    val pluginStrings = AndroidPluginStrings(deps.uiContext)
    val pluginBus = PluginBusImpl()
    val goalService: GoalService by lazy { GoalService() }
    val questionCenter = QuestionCenter(
        persistence = QuestionPersistence(File(deps.appContext.filesDir, "questions")),
    )

    fun reloadCustomProviders() {
        providerCatalog.setCustomProviders(customProviderStore.entries.value.map { entry -> entry.toProviderConfig() })
    }


    init {
        deps.scope.launch {
            appPreferences.preferences.collect { preferences -> jobWake.value = preferences.backgroundJobWake }
        }
        deps.scope.launch {
            linuxSettings.sharedStorageEnabled.collect { enabled ->
                sharedStorage.value = enabled && isSharedStorageGranted()
            }
        }
    }
}
