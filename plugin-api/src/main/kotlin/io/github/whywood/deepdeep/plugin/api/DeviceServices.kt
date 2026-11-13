package io.github.whywood.deepdeep.plugin.api

import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

public data class DeviceInfo(
    public val manufacturer: String,
    public val brand: String,
    public val model: String,
    public val device: String,
    public val product: String,
    public val hardware: String,
    public val androidRelease: String,
    public val sdkInt: Int,
    public val abis: List<String>,
    public val emulator: Boolean,
    public val language: String,
    public val timezone: String,
    public val screenWidthPx: Int,
    public val screenHeightPx: Int,
    public val density: Float,
    public val refreshRate: Float,
    public val totalMemoryBytes: Long,
    public val totalStorageBytes: Long,
    public val installationId: String,
)

public data class DeviceState(
    public val batteryPercent: Int,
    public val charging: Boolean,
    public val powerSave: Boolean,
    public val networkType: String,
    public val connected: Boolean,
    public val screenOn: Boolean,
    public val locked: Boolean,
    public val language: String,
    public val timezone: String,
    public val uptimeMs: Long,
    public val freeMemoryBytes: Long,
    public val freeStorageBytes: Long,
    public val hostAccess: HostAccessLevel,
)

@JvmInline
public value class HostAccessLevel private constructor(public val wire: String) {
    public companion object {
        public val LOCAL: HostAccessLevel = HostAccessLevel("local")
        public val ADB: HostAccessLevel = HostAccessLevel("adb")
        public val ROOT: HostAccessLevel = HostAccessLevel("root")
        public val ALL: List<HostAccessLevel> = listOf(LOCAL, ADB, ROOT)

        public fun fromWire(value: String): HostAccessLevel? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public enum class DeviceSettings {
    APP,
    NOTIFICATIONS,
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    OVERLAY,
    USAGE_STATS,
    BATTERY_OPTIMIZATION,
    INSTALL_PACKAGES,
    STORAGE,
    DEVELOPER,
    WIRELESS_DEBUGGING,
    LOCATION,
}

public interface DeviceService {
    public val info: DeviceInfo

    public val state: DeviceState

    public fun observe(): Flow<DeviceState>

    public fun vibrate(durationMs: Long = 200)

    public fun openSettings(section: DeviceSettings): Boolean
}

public interface ClipboardService {
    public fun text(): String?

    public fun setText(text: String)

    public fun uri(): String?

    public fun setUri(uri: String)

    public fun has(): Boolean

    public fun clear()
}

@JvmInline
public value class PluginSpecialAccess private constructor(public val wire: String) {
    public companion object {
        public val NOTIFICATION_LISTENER: PluginSpecialAccess = PluginSpecialAccess("notification-listener")
        public val ACCESSIBILITY: PluginSpecialAccess = PluginSpecialAccess("accessibility")
        public val OVERLAY: PluginSpecialAccess = PluginSpecialAccess("overlay")
        public val USAGE_STATS: PluginSpecialAccess = PluginSpecialAccess("usage-stats")
        public val BATTERY_OPTIMIZATION: PluginSpecialAccess = PluginSpecialAccess("battery-optimization")
        public val INSTALL_PACKAGES: PluginSpecialAccess = PluginSpecialAccess("install-packages")
        public val MANAGE_STORAGE: PluginSpecialAccess = PluginSpecialAccess("manage-storage")
        public val ALL: List<PluginSpecialAccess> = listOf(
            NOTIFICATION_LISTENER,
            ACCESSIBILITY,
            OVERLAY,
            USAGE_STATS,
            BATTERY_OPTIMIZATION,
            INSTALL_PACKAGES,
            MANAGE_STORAGE,
        )

        public fun fromWire(value: String): PluginSpecialAccess? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public data class PluginAccessState(
    public val granted: Boolean,
    public val available: Boolean = true,
    public val reason: String? = null,
)

public interface PermissionService {
    public fun granted(permission: String): Boolean

    public suspend fun request(vararg permissions: String): Map<String, Boolean>

    public fun special(access: PluginSpecialAccess): PluginAccessState

    public fun openSettings(access: PluginSpecialAccess): Boolean
}

public data class PluginPackageInfo(
    public val packageName: String,
    public val label: String,
    public val versionName: String,
    public val versionCode: Long,
    public val system: Boolean,
    public val enabled: Boolean,
    public val installedAtMs: Long = 0,
    public val updatedAtMs: Long = 0,
)

public data class PackageInstallResult(
    public val accepted: Boolean,
    public val pendingUserAction: Boolean = false,
    public val error: String? = null,
)

public interface PackageService {
    public suspend fun list(includeSystem: Boolean = false): List<PluginPackageInfo>

    public suspend fun get(packageName: String): PluginPackageInfo?

    public suspend fun install(apkPath: String, silent: Boolean = false): PackageInstallResult

    public suspend fun uninstall(packageName: String, keepData: Boolean = false): PackageInstallResult

    public fun launch(packageName: String): Boolean

    public fun openAppInfo(packageName: String): Boolean

    public suspend fun icon(packageName: String, sizePx: Int = 256): ByteArray?
}

@JvmInline
public value class PrivilegeLevel private constructor(public val wire: String) {
    public companion object {
        public val AUTO: PrivilegeLevel = PrivilegeLevel("auto")
        public val ROOT: PrivilegeLevel = PrivilegeLevel("root")
        public val ADB: PrivilegeLevel = PrivilegeLevel("adb")
        public val ALL: List<PrivilegeLevel> = listOf(AUTO, ROOT, ADB)

        public fun fromWire(value: String): PrivilegeLevel? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public data class RootState(
    public val status: String,
    public val granted: Boolean,
    public val error: String? = null,
)

public data class AdbState(
    public val paired: Boolean,
    public val connected: Boolean,
    public val busy: Boolean = false,
    public val host: String? = null,
    public val port: Int = 0,
    public val error: String? = null,
)

public data class PrivilegeResult(
    public val exitCode: Int,
    public val stdout: String,
    public val stderr: String,
    public val timedOut: Boolean = false,
    public val truncated: Boolean = false,
)

public data class AdbPairingResult(
    public val started: Boolean,
    public val paired: Boolean,
    public val error: String? = null,
)

public interface PrivilegeService {
    public val access: HostAccessLevel

    public val root: RootState

    public val adb: AdbState

    public suspend fun probeRoot(): RootState

    public suspend fun exec(
        command: String,
        level: PrivilegeLevel = PrivilegeLevel.AUTO,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): PrivilegeResult

    public suspend fun install(apkPath: String, silent: Boolean = true): PackageInstallResult

    public suspend fun uninstall(packageName: String): PackageInstallResult

    public fun openAccessSettings(): Boolean

    public fun openWirelessDebugging(): Boolean

    public suspend fun startAdbPairing(): AdbPairingResult

    public suspend fun submitAdbPairingCode(code: String): AdbPairingResult

    public suspend fun connectAdb(): Boolean

    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 30_000
    }
}

public data class PluginActivityResult(
    public val resultCode: Int,
    public val data: Intent?,
    public val cancelled: Boolean = false,
    public val timedOut: Boolean = false,
)

public data class ResolvedIntent(
    public val packageName: String,
    public val className: String?,
    public val label: String?,
    public val exported: Boolean,
)

public interface IntentsService {
    public fun startActivity(intent: Intent): Boolean

    public suspend fun startActivityForResult(intent: Intent, timeoutMs: Long = DEFAULT_TIMEOUT_MS): PluginActivityResult

    public fun sendBroadcast(intent: Intent): Boolean

    public fun startService(intent: Intent): Boolean

    public fun stopService(intent: Intent): Boolean

    public fun canResolve(intent: Intent): Boolean

    public suspend fun resolve(intent: Intent): ResolvedIntent?

    public suspend fun queryActivities(intent: Intent): List<ResolvedIntent>

    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 120_000
    }
}

public interface PluginActivityOptions {
    public val pageId: String
    public val arguments: JsonObject
    public val newTask: Boolean
}

public fun pluginActivityOptions(
    pageId: String,
    arguments: JsonObject = JsonObject(emptyMap()),
    newTask: Boolean = true,
): PluginActivityOptions = PluginActivityOptionsImpl(pageId, arguments, newTask)

internal data class PluginActivityOptionsImpl(
    override val pageId: String,
    override val arguments: JsonObject,
    override val newTask: Boolean,
) : PluginActivityOptions

public interface ForegroundServiceOptions {
    public val id: String
    public val title: String
    public val text: String
    public val ongoing: Boolean
    public val progress: Int?
}

public fun foregroundServiceOptions(
    id: String,
    title: String,
    text: String,
    ongoing: Boolean = true,
    progress: Int? = null,
): ForegroundServiceOptions = ForegroundServiceOptionsImpl(id, title, text, ongoing, progress)

internal data class ForegroundServiceOptionsImpl(
    override val id: String,
    override val title: String,
    override val text: String,
    override val ongoing: Boolean,
    override val progress: Int?,
) : ForegroundServiceOptions

public interface PluginReceiverHandle {
    public val id: String

    public fun events(): Flow<Intent>

    public fun close()
}

public interface ComponentService {
    public fun startActivity(options: PluginActivityOptions): Boolean

    public fun registerReceiver(filter: IntentFilter): PluginReceiverHandle
}

public data class PluginBackgroundTaskInfo(
    public val id: String,
    public val kind: String,
    public val nextRunAtMs: Long? = null,
    public val intervalMs: Long? = null,
    public val lastRunAtMs: Long? = null,
)

public interface BackgroundService {
    public suspend fun schedule(
        id: String,
        kind: PluginBackgroundKind,
        triggerAtMs: Long? = null,
        intervalMs: Long? = null,
        requiresNetwork: Boolean = false,
        requiresCharging: Boolean = false,
    ): Boolean

    public suspend fun cancel(id: String): Boolean

    public suspend fun tasks(): List<PluginBackgroundTaskInfo>

    public fun startForeground(options: ForegroundServiceOptions): Boolean

    public fun updateForeground(options: ForegroundServiceOptions): Boolean

    public fun stopForeground(id: String): Boolean

    public val batteryOptimizationIgnored: Boolean

    public fun requestIgnoreBatteryOptimizations(): Boolean

    public fun openBatteryOptimizationSettings(): Boolean
}

@JvmInline
public value class PluginBackgroundKind private constructor(public val wire: String) {
    public companion object {
        public val ONCE: PluginBackgroundKind = PluginBackgroundKind("once")
        public val PERIODIC: PluginBackgroundKind = PluginBackgroundKind("periodic")
        public val EXACT: PluginBackgroundKind = PluginBackgroundKind("exact")
        public val BOOT: PluginBackgroundKind = PluginBackgroundKind("boot")
        public val ALL: List<PluginBackgroundKind> = listOf(ONCE, PERIODIC, EXACT, BOOT)

        public fun fromWire(value: String): PluginBackgroundKind? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}
