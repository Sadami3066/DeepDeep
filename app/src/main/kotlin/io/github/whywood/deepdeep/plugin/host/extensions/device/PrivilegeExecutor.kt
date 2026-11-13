package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.PackageInstallResult
import io.github.whywood.deepdeep.plugin.api.PrivilegeLevel
import io.github.whywood.deepdeep.plugin.api.PrivilegeResult
import io.github.whywood.deepdeep.runtime.host.HostAccess
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class PrivilegeExecutor(private val deps: DeviceServiceDeps) {
    suspend fun exec(
        command: String,
        level: PrivilegeLevel = PrivilegeLevel.AUTO,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): PrivilegeResult = withContext(Dispatchers.IO) {
        val commandText = command.trim()
        if (commandText.isEmpty()) {
            return@withContext errorResult("command must not be blank")
        }
        val timeout = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        when (level) {
            PrivilegeLevel.AUTO -> when (deps.hostAccess.value.effective) {
                HostAccess.ROOT -> runRoot(commandText, timeout)
                HostAccess.ADB -> runAdb(commandText, timeout)
                HostAccess.LOCAL -> errorResult("privileged access is not available")
            }

            PrivilegeLevel.ROOT -> runRoot(commandText, timeout)

            PrivilegeLevel.ADB -> runAdb(commandText, timeout)

            else -> errorResult("unsupported privilege level: ${level.wire}")
        }
    }

    suspend fun installSilently(apk: File): PackageInstallResult = withContext(Dispatchers.IO) {
        if (!apk.isFile) return@withContext PackageInstallResult(accepted = false, error = "apk not found")
        val command = "pm install -r -g ${shellQuote(apk.absolutePath)}"
        val result = exec(command, PrivilegeLevel.AUTO, INSTALL_TIMEOUT_MS)
        if (result.exitCode == 0 && !result.timedOut) {
            PackageInstallResult(accepted = true)
        } else {
            PackageInstallResult(
                accepted = false,
                error = result.stderr.ifBlank { result.stdout }.ifBlank { "pm install failed" }.trim(),
            )
        }
    }

    suspend fun uninstallSilently(packageName: String, keepData: Boolean = false): PackageInstallResult =
        withContext(Dispatchers.IO) {
            val name = validPackageName(packageName)
                ?: return@withContext PackageInstallResult(accepted = false, error = "invalid package name")
            val keep = if (keepData) " -k" else ""
            val result = exec("pm uninstall$keep $name", PrivilegeLevel.AUTO, UNINSTALL_TIMEOUT_MS)
            if (result.exitCode == 0 && !result.timedOut) {
                PackageInstallResult(accepted = true)
            } else {
                PackageInstallResult(
                    accepted = false,
                    error = result.stderr.ifBlank { result.stdout }.ifBlank { "pm uninstall failed" }.trim(),
                )
            }
        }

    private suspend fun runRoot(command: String, timeout: Long): PrivilegeResult {
        val result = runCatching {
            withTimeoutOrNull(timeout + TIMEOUT_GRACE_MS) { deps.rootShell.execute(command, timeout) }
        }
        return result.fold(
            onSuccess = { rootResult ->
                if (rootResult == null) {
                    errorResult("root command timed out").copy(timedOut = true)
                } else {
                    PrivilegeResult(
                        exitCode = rootResult.exitCode,
                        stdout = rootResult.stdout,
                        stderr = rootResult.stderr,
                        timedOut = rootResult.timedOut,
                        truncated = rootResult.truncated,
                    )
                }
            },
            onFailure = { error -> errorResult(error.message ?: "root command failed") },
        )
    }

    private suspend fun runAdb(command: String, timeout: Long): PrivilegeResult {
        val result = runCatching {
            withTimeoutOrNull(timeout + TIMEOUT_GRACE_MS) { deps.adb.exec(command, timeout) }
        }
        return result.fold(
            onSuccess = { adbResult ->
                if (adbResult == null) {
                    errorResult("adb command timed out").copy(timedOut = true)
                } else {
                    PrivilegeResult(
                        exitCode = adbResult.exitCode,
                        stdout = adbResult.stdout,
                        stderr = adbResult.stderr,
                        timedOut = adbResult.timedOut,
                        truncated = adbResult.truncated,
                    )
                }
            },
            onFailure = { error -> errorResult(error.message ?: "adb command failed") },
        )
    }

    private fun errorResult(message: String): PrivilegeResult =
        PrivilegeResult(exitCode = -1, stdout = "", stderr = message)

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 10 * 60_000L
        const val TIMEOUT_GRACE_MS = 5_000L
        const val INSTALL_TIMEOUT_MS = 120_000L
        const val UNINSTALL_TIMEOUT_MS = 60_000L
    }
}

internal fun validPackageName(value: String): String? {
    val name = value.trim()
    if (name.isEmpty() || name.length > 255) return null
    return name.takeIf { PACKAGE_NAME_PATTERN.matches(it) }
}

private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
