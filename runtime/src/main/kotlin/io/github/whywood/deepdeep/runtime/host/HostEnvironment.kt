package io.github.whywood.deepdeep.runtime.host

import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import okio.FileSystem

class HostAccessUnavailableException(message: String) : IllegalStateException(message)

data class HostCapabilities(
    val isRoot: Boolean = false,
    val hasSystemShell: Boolean = false,
    val canReadSystemData: Boolean = false,
    val bashAvailable: Boolean = false,
    val hasScreenControl: Boolean = false,
    val rootLinuxReady: Boolean = false,
)

interface HostEnvironment {
    val access: HostAccess
    val capabilities: HostCapabilities
    val sharedStorageEnabled: Boolean
    fun bash(cwd: String): ShellExecutor
    fun bashStreaming(cwd: String): StreamingShellExecutor? = null
    val files: FileSystem
    val system: ShellExecutor?
    val promptLines: List<String>
}

interface HostEnvironmentProvider {
    fun current(): HostEnvironment
}
