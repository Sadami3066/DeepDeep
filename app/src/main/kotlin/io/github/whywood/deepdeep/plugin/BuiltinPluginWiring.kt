package io.github.whywood.deepdeep.plugin

import android.content.Context
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.plugin.manager.BuiltinPluginEntry
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import java.io.File

internal class BuiltinPluginWiring(
    val uiContext: Context,
    val cacheDir: File,
    val linux: LinuxEnvironment,
    val linuxSettings: LinuxSettingsStore,
    val workspaceDir: File,
    val onCapabilitiesChanged: () -> Unit,
    val onSessionInvalidated: () -> Unit,
)

internal fun builtinPlugins(wiring: BuiltinPluginWiring): List<BuiltinPluginEntry> = listOf(
    cliFeishu(wiring),
    androidMiCloud(wiring),
    cliWecom(wiring),
    cliJwxt(wiring),
)
