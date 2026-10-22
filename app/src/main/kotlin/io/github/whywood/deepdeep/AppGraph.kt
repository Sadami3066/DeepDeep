package io.github.whywood.deepdeep

import android.content.Context
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.ApiKeyStore
import io.github.whywood.deepdeep.runtime.DeepSeekModels
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.PiSessionEngine
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxShellExecutor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val workspace: File = File(appContext.filesDir, "workspace").apply { mkdirs() }
    val agentDir: File = File(appContext.filesDir, "agent").apply { mkdirs() }

    val apiKeyStore = ApiKeyStore(appContext)
    val modelConfigStore = ModelConfigStore(appContext)
    val linuxSettings = LinuxSettingsStore(appContext)
    val themePreferences = ThemePreferences(appContext)
    val linuxEnvironment = LinuxEnvironment(appContext, workspace)
    val shellExecutor = LinuxShellExecutor(linuxEnvironment)
    val credentials = SdkCredentialStore(apiKeyStore, scope)
    val engine = PiSessionEngine(workspace, agentDir, credentials, shellExecutor)
    val host = AgentRuntimeHost(engine, modelConfigStore) { linuxSettings.isEnabled() }

    suspend fun prepare() {
        importDebugKey()
        credentials.preload(DeepSeekModels.PROVIDER_ID)
    }

    private suspend fun importDebugKey() {
        if (!BuildConfig.DEBUG) return
        val directory = File(appContext.filesDir, "import")
        val keyFile = File(directory, "deepseek.key")
        if (!keyFile.isFile) return
        val key = keyFile.readText().trim()
        if (key.isNotEmpty()) {
            apiKeyStore.save(DeepSeekModels.PROVIDER_ID, key)
        }
        keyFile.delete()
        directory.delete()
    }
}
