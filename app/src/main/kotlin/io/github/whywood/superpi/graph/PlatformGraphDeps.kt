package io.github.whywood.superpi.graph

import android.content.Context
import io.github.whywood.superpi.sdk.ai.http.HttpTransport
import io.github.whywood.superpi.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.superpi.data.LinuxSettingsStore
import io.github.whywood.superpi.data.StorageMountStore
import io.github.whywood.superpi.plugin.manager.PluginHostManager
import io.github.whywood.superpi.runtime.AgentRuntimeHost
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
