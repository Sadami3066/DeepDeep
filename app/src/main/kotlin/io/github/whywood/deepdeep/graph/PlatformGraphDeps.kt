package io.github.whywood.deepdeep.graph

import android.content.Context
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.StorageMountStore
import io.github.whywood.deepdeep.plugin.manager.PluginHostManager
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal class PlatformGraphDeps(
    val appContext: Context,
    val uiContext: Context,
    val scope: CoroutineScope,
    val homeDir: File,
    val sharedStorage: StateFlow<Boolean>,
    val linuxSettings: LinuxSettingsStore,
    val storageMountStore: StorageMountStore,
    val httpTransport: HttpTransport,
    val mcpTokenStore: FileMcpTokenStore,
    val pluginManager: () -> PluginHostManager,
    val host: () -> AgentRuntimeHost,
    val syncBundledSkills: suspend () -> Unit,
)
