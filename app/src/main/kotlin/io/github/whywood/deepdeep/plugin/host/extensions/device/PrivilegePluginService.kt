package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.deeplink.DeepLink
import io.github.whywood.deepdeep.plugin.api.AdbPairingResult
import io.github.whywood.deepdeep.plugin.api.AdbState
import io.github.whywood.deepdeep.plugin.api.HostAccessLevel
import io.github.whywood.deepdeep.plugin.api.PackageInstallResult
import io.github.whywood.deepdeep.plugin.api.PrivilegeLevel
import io.github.whywood.deepdeep.plugin.api.PrivilegeResult
import io.github.whywood.deepdeep.plugin.api.PrivilegeService
import io.github.whywood.deepdeep.plugin.api.RootState
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.shell.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class PrivilegePluginService(
    private val deps: DeviceServiceDeps,
    private val privilege: PrivilegeExecutor,
) : PrivilegeService {
    private val rootState = MutableStateFlow(RootState(status = STATUS_UNKNOWN, granted = false))

    init {
        deps.scope.launch {
            runCatching { probeRoot() }
        }
    }

    override val access: HostAccessLevel
        get() = hostAccessLevelOf(deps.hostAccess.value.effective)

    override val root: RootState
        get() = rootState.value

    override val adb: AdbState
        get() {
            val state = deps.adb.state.value
            return AdbState(
                paired = state.paired,
                connected = state.daemon,
                busy = state.busy,
                host = null,
                port = deps.adb.daemonPort,
                error = state.error?.name,
            )
        }

    override suspend fun probeRoot(): RootState = withContext(Dispatchers.IO) {
        val status = runCatching { deps.rootShell.probe() }.getOrDefault(RootStatus.Error)
        val state = RootState(status = rootStatusWire(status), granted = status == RootStatus.Granted)
        rootState.value = state
        state
    }

    override suspend fun exec(command: String, level: PrivilegeLevel, timeoutMs: Long): PrivilegeResult =
        privilege.exec(command, level, timeoutMs)

    override suspend fun install(apkPath: String, silent: Boolean): PackageInstallResult {
        val apk = resolveInstallableApk(deps, apkPath)
            ?: return PackageInstallResult(
                accepted = false,
                error = "apk must be inside the plugin data or cache directory",
            )
        return if (silent) privilege.installSilently(apk) else performUserInstall(deps, apk)
    }

    override suspend fun uninstall(packageName: String): PackageInstallResult {
        val name = validPackageName(packageName)
            ?: return PackageInstallResult(accepted = false, error = "invalid package name")
        return if (deps.hostAccess.value.effective == HostAccess.LOCAL) {
            runCatching {
                deps.appContext.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:$name"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.fold(
                onSuccess = { PackageInstallResult(accepted = true, pendingUserAction = true) },
                onFailure = { error ->
                    PackageInstallResult(accepted = false, error = error.message ?: "cannot open uninstaller")
                },
            )
        } else {
            privilege.uninstallSilently(name)
        }
    }

    override fun openAccessSettings(): Boolean = runCatching {
        val deepLink = ACCESS_SETTINGS_DEEP_LINK
        if (DeepLink.parse(deepLink) != null) {
            val intent = Intent(deps.context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(deepLink))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivitySafely(deps.appContext, intent)
        } else {
            openSettingsScreen(deps.context, DeviceSettingsScreen.APP_DETAILS)
        }
    }.getOrDefault(false)

    override fun openWirelessDebugging(): Boolean = startActivitySafely(
        deps.appContext,
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    )

    override suspend fun startAdbPairing(): AdbPairingResult = withContext(Dispatchers.IO) {
        val outcome = runCatching {
            withTimeoutOrNull(PAIRING_START_TIMEOUT_MS) { deps.adb.startPairing() }
        }.getOrNull()
        AdbPairingResult(
            started = outcome?.isSuccess == true,
            paired = deps.adb.state.value.paired,
            error = outcome?.exceptionOrNull()?.message ?: if (outcome == null) "pairing timed out" else null,
        )
    }

    override suspend fun submitAdbPairingCode(code: String): AdbPairingResult = withContext(Dispatchers.IO) {
        val normalized = code.trim()
        if (normalized.isEmpty()) {
            return@withContext AdbPairingResult(started = false, paired = false, error = "pairing code required")
        }
        val outcome = runCatching {
            withTimeoutOrNull(PAIRING_CODE_TIMEOUT_MS) { deps.adb.pairAndStart(normalized) }
        }.getOrNull()
        AdbPairingResult(
            started = outcome != null,
            paired = deps.adb.state.value.paired,
            error = outcome?.exceptionOrNull()?.message
                ?: if (outcome == null) "pairing timed out" else deps.adb.state.value.error?.name,
        )
    }

    override suspend fun connectAdb(): Boolean = withContext(Dispatchers.IO) {
        val outcome = runCatching {
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { deps.adb.connectAndStart() }
        }.getOrNull()
        outcome?.isSuccess == true
    }

    private companion object {
        const val STATUS_UNKNOWN = "unknown"
        const val ACCESS_SETTINGS_DEEP_LINK = "deepdeep://settings/access"
        const val PAIRING_START_TIMEOUT_MS = 15_000L
        const val PAIRING_CODE_TIMEOUT_MS = 120_000L
        const val CONNECT_TIMEOUT_MS = 120_000L
    }
}

internal fun rootStatusWire(status: RootStatus): String = when (status) {
    RootStatus.Granted -> "granted"
    RootStatus.Denied -> "denied"
    RootStatus.Timeout -> "timeout"
    RootStatus.Unsupported -> "unsupported"
    RootStatus.NotRequested -> "not-requested"
    RootStatus.Error -> "error"
}
