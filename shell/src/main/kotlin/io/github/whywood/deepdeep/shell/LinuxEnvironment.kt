package io.github.whywood.deepdeep.shell

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.StateFlow

class LinuxEnvironment(
    context: Context,
    val workspaceDir: File = File(context.applicationContext.filesDir, "workspace"),
    private val sharedStorageEnabled: () -> Boolean = { false },
    private val extraBinds: List<Pair<File, String>> = emptyList(),
) : LinuxExecutor {
    private val appContext = context.applicationContext
    val rootfsDir: File = File(appContext.filesDir, "linux/rootfs")
    val prootBinary: File = File(appContext.applicationInfo.nativeLibraryDir, "libproot.so")
    val prootLoader: File = File(appContext.applicationInfo.nativeLibraryDir, "libproot-loader.so")

    private val installer = LinuxRootfsInstaller(
        rootfsDir = rootfsDir,
        assetOpener = { appContext.assets.open(LinuxRootfsInstaller.ROOTFS_ASSET) },
        expectedSha256 = LinuxRootfsInstaller.EXPECTED_SHA256,
    )

    private val executor = PRootExecutor(
        prootBinary = prootBinary,
        prootLoader = prootLoader,
        rootfsDir = rootfsDir,
        workspaceDir = workspaceDir,
        tempDir = File(appContext.cacheDir, "proot-tmp"),
        sharedStorageEnabled = sharedStorageEnabled,
        extraBinds = extraBinds,
    )

    val state: StateFlow<LinuxInstallState> = installer.state

    val isSupported: Boolean
        get() = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    suspend fun install(): LinuxInstallState {
        requireSupported()
        val result = installer.install()
        if (result is LinuxInstallState.Installed) {
            workspaceDir.mkdirs()
            File(rootfsDir, "workspace").mkdirs()
            prepareBindDirectories(rootfsDir, extraBinds)
            writeNotices()
            refreshDns()
        }
        return result
    }

    suspend fun reset(): LinuxInstallState = installer.reset()

    suspend fun refreshState(): LinuxInstallState = installer.refreshState()

    override suspend fun execute(
        command: String,
        timeoutMillis: Long,
    ): LinuxResult {
        requireSupported()
        require(installer.currentState() is LinuxInstallState.Installed) {
            "Linux environment is not installed"
        }
        return executor.execute(command, timeoutMillis)
    }

    suspend fun executeStreaming(
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit,
    ): LinuxResult {
        requireSupported()
        require(installer.currentState() is LinuxInstallState.Installed) {
            "Linux environment is not installed"
        }
        return executor.executeStreaming(command, timeoutMillis, onOutputLine)
    }

    suspend fun refreshDns() {
        if (!rootfsDir.isDirectory) return
        val servers = runCatching {
            val manager = appContext.getSystemService(ConnectivityManager::class.java)
            val network = manager?.activeNetwork
            manager?.getLinkProperties(network)?.dnsServers
                ?.mapNotNull { it.hostAddress }
                ?.filter { it.isNotBlank() }
                .orEmpty()
        }.getOrDefault(emptyList())

        val resolved = servers.ifEmpty { listOf("8.8.8.8", "8.8.4.4") }
        val content = resolved.joinToString(separator = "\n", postfix = "\n") { "nameserver $it" }
        runCatching {
            File(rootfsDir, "etc").mkdirs()
            File(rootfsDir, "etc/resolv.conf").writeText(content)
        }
    }

    private fun writeNotices() {
        runCatching {
            val text = appContext.assets.open("THIRD_PARTY_NOTICES.txt")
                .bufferedReader()
                .use { it.readText() }
            File(rootfsDir, "THIRD_PARTY_NOTICES").writeText(text)
        }
    }

    private fun requireSupported() {
        check(isSupported) { "Linux environment requires arm64-v8a" }
    }
}
