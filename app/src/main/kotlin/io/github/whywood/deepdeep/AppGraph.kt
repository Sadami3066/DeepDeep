package io.github.whywood.deepdeep

import android.content.Context
import dev.pi.ai.http.okHttpTransport
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.FileAttachmentStore
import io.github.whywood.deepdeep.data.LegacyWorkspaceMigration
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.StorageMigration
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.ApiKeyStore
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.PiSessionEngine
import io.github.whywood.deepdeep.runtime.WorkspaceStore
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SessionCatalog
import io.github.whywood.deepdeep.runtime.SessionMetaStore
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxShellExecutor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        StorageMigration.migrate(appContext.filesDir)
    }

    val homeDir: File = File(appContext.filesDir, "home").apply { mkdirs() }
    val workspacesDir: File = File(appContext.filesDir, "workspaces").apply { mkdirs() }
    val agentDir: File = File(appContext.filesDir, "agent").apply { mkdirs() }

    val apiKeyStore = ApiKeyStore(appContext)
    val modelConfigStore = ModelConfigStore(appContext)
    val attachmentStore = FileAttachmentStore(appContext, homeDir)
    val linuxSettings = LinuxSettingsStore(appContext)
    val themePreferences = ThemePreferences(appContext)
    val appPreferences = AppPreferences(appContext)
    val linuxEnvironment = LinuxEnvironment(appContext, homeDir)
    val credentials = SdkCredentialStore(apiKeyStore, scope)
    val providerCatalog = ProviderCatalog(okHttpTransport(), credentials)
    val sessionMetaStore = SessionMetaStore(appContext)
    val sessionCatalog = SessionCatalog(agentDir, sessionMetaStore)
    val workspaceStore = WorkspaceStore(appContext)
    private val shellEnvironments = mutableMapOf<String, LinuxEnvironment>()

    private val shellExecutorFor: suspend (File) -> LinuxShellExecutor? = { cwd ->
        if (!linuxSettings.isEnabled()) {
            null
        } else {
            val environment = synchronized(shellEnvironments) {
                shellEnvironments.getOrPut(cwd.absolutePath) {
                    LinuxEnvironment(appContext, cwd)
                }
            }
            LinuxShellExecutor(environment)
        }
    }

    private val engineFactory: suspend (File) -> PiSessionEngine = { cwd ->
        PiSessionEngine(cwd, agentDir, credentials, shellExecutorFor(cwd), providerCatalog)
    }

    val host = AgentRuntimeHost(
        homeDir = homeDir,
        workspacesDir = workspacesDir,
        engineFactory = engineFactory,
        modelConfigStore = modelConfigStore,
        catalog = sessionCatalog,
        workspaceStore = workspaceStore,
        providers = providerCatalog,
    )

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
            apiKeyStore.save(ProviderCatalog.DEFAULT_PROVIDER_ID, key)
        }
        keyFile.delete()
        directory.delete()
    }
}
