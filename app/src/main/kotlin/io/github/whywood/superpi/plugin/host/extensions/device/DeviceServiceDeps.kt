package io.github.whywood.superpi.plugin.host.extensions.device

import android.content.Context
import io.github.whywood.superpi.adb.AdbBootstrap
import io.github.whywood.superpi.data.update.AndroidUpdateInstaller
import io.github.whywood.superpi.data.update.UpdateInstaller
import io.github.whywood.superpi.plugin.api.BackgroundService
import io.github.whywood.superpi.plugin.api.ClipboardService
import io.github.whywood.superpi.plugin.api.ComponentService
import io.github.whywood.superpi.plugin.api.DeviceService
import io.github.whywood.superpi.plugin.api.IntentsService
import io.github.whywood.superpi.plugin.api.PackageService
import io.github.whywood.superpi.plugin.api.PermissionService
import io.github.whywood.superpi.plugin.api.PrivilegeService
import io.github.whywood.superpi.plugin.host.interaction.PluginInteraction
import io.github.whywood.superpi.runtime.host.HostAccessState
import io.github.whywood.superpi.shell.RootShell
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

internal data class DevicePluginServices(
    val device: DeviceService,
    val clipboard: ClipboardService,
    val permissions: PermissionService,
    val packages: PackageService,
    val privilege: PrivilegeService,
    val intents: IntentsService,
    val components: ComponentService,
    val background: BackgroundService,
)

internal data class DeviceServiceDeps(
    val context: Context,
    val appContext: Context,
    val scope: CoroutineScope,
    val pluginId: String,
    val pluginDataDir: File,
    val pluginCacheDir: File,
    val rootDir: File,
    val interaction: PluginInteraction,
    val rootShell: RootShell,
    val adb: AdbBootstrap,
    val hostAccess: StateFlow<HostAccessState>,
    val launchPluginActivity: (pluginId: String, pageId: String, arguments: JsonObject, newTask: Boolean) -> Boolean,
    val installationId: suspend () -> String = { "" },
    val pluginEnabled: ((String) -> Boolean)? = null,
    val firstPageId: String? = null,
    val updateInstaller: UpdateInstaller = AndroidUpdateInstaller(appContext),
)

internal fun buildDevicePluginServices(deps: DeviceServiceDeps): DevicePluginServices {
    deps.pluginDataDir.mkdirs()
    deps.pluginCacheDir.mkdirs()
    deps.rootDir.mkdirs()
    val privilege = PrivilegeExecutor(deps)
    return DevicePluginServices(
        device = DevicePluginService(deps),
        clipboard = ClipboardPluginService(deps.appContext),
        permissions = PermissionPluginService(deps),
        packages = PackagePluginService(deps, privilege),
        privilege = PrivilegePluginService(deps, privilege),
        intents = IntentsPluginService(deps),
        components = ComponentsPluginService(deps),
        background = BackgroundPluginService(deps),
    )
}
