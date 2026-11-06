package io.github.whywood.deepdeep.graph

import dev.pi.ai.http.okHttpTransport
import dev.pi.mcp.oauth.FileMcpTokenStore
import dev.pi.mcp.oauth.defaultMcpAuthFile
import io.github.whywood.deepdeep.core.ModelSelection
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.FileAttachmentStore
import io.github.whywood.deepdeep.data.FontPreferences
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.OverlayEntryPreferences
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.ShareStaging
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.data.VoicePreferences
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.plugin.AndroidPluginStrings
import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.host.services.PluginBusImpl
import io.github.whywood.deepdeep.runtime.ApiKeyStore
import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.InstallationIdStore
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.OAuthCredentialStore
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.ProviderOAuthService
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.QuestionPersistence
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SessionCatalog
import io.github.whywood.deepdeep.runtime.SessionMetaStore
import io.github.whywood.deepdeep.runtime.SkillCatalog
import io.github.whywood.deepdeep.runtime.TimerStore
import io.github.whywood.deepdeep.runtime.WorkshopStore
import io.github.whywood.deepdeep.runtime.WorkspaceStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

internal class DataGraph(private val deps: DataGraphDeps) {
    val homeDir: File = File(deps.appContext.filesDir, "home").apply { mkdirs() }
    val workspacesDir: File = File(deps.appContext.filesDir, "workspaces").apply { mkdirs() }
    val agentDir: File = File(deps.appContext.filesDir, "agent").apply { mkdirs() }

    val jobWake = MutableStateFlow(true)
    val sharedStorage = MutableStateFlow(false)

    val secretStore = EncryptedSecretStore(deps.appContext)
    val apiKeyStore = ApiKeyStore(secretStore)
    val oauthCredentialStore = OAuthCredentialStore(secretStore)
    val modelConfigStore = ModelConfigStore(deps.appContext)
    val attachmentStore = FileAttachmentStore(deps.appContext, homeDir)
    val linuxSettings = LinuxSettingsStore(deps.appContext)
    val themePreferences = ThemePreferences(deps.appContext)
    val appPreferences = AppPreferences(deps.appContext)
    val voicePreferences = VoicePreferences(deps.appContext)
    val overlayEntryPreferences = OverlayEntryPreferences(deps.appContext)
    val shareStaging = ShareStaging(deps.uiContext)
    val personalizationStore = PersonalizationStore(deps.appContext)
    val fontPreferences = FontPreferences(deps.appContext)

    val httpTransport = okHttpTransport()
    val credentials = SdkCredentialStore(apiKeyStore, oauthCredentialStore, deps.scope)
    val providerCatalog = ProviderCatalog(httpTransport, credentials)
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
    val timerStore = TimerStore(TimerStore.prepareDataDirectory(deps.appContext.filesDir))
    val workshopStore = WorkshopStore(File(deps.appContext.filesDir, "workshop"))
    val skillCatalog = SkillCatalog(agentDir)
    val pluginSettingsStore = PluginSettingsStore(File(deps.appContext.filesDir, "plugins/settings.json"))
    val pluginStrings = AndroidPluginStrings(deps.uiContext)
    val pluginBus = PluginBusImpl()
    val goalService: GoalService by lazy { GoalService() }
    val questionCenter = QuestionCenter(
        persistence = QuestionPersistence(File(deps.appContext.filesDir, "questions")),
    )


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
