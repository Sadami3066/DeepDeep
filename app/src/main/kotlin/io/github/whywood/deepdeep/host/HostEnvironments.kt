package io.github.whywood.deepdeep.host

import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import io.github.whywood.deepdeep.runtime.host.HostEnvironment
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import okio.FileSystem

class HostAccessUnavailableException(message: String) : IllegalStateException(message)

enum class RootBashReadiness {
    PREPARING,
    READY,
    FAILED,
}

private val localPromptLines = listOf(
    "Env: Alpine Linux via proot and Android.",
)

private val adbPromptLines = localPromptLines + listOf(
    "Android system commands run through system_shell = adb shell.",
)

private val rootPromptLines = listOf(
    "Env: Alpine Linux chroot as real root (uid 0), Android filesystems at native paths.",
    "- /data (rw): Android /data. Other apps' private data: /data/data/<package>.",
    "- /system /vendor /product /system_ext /odm (ro): Android system files.",
    "- /sdcard = /storage/emulated/0 (rw), same data as /data/media/0.",
    "- /proc /sys /dev: host.",
    "Android system commands run directly via the root shell capability.",
    "read, edit and write reach the same native tree as bash; pass absolute paths without an /android prefix.",
)

class LocalHostEnvironment(
    private val prootBash: (String) -> ShellExecutor,
    sharedStorageEnabled: () -> Boolean,
) : HostEnvironment {
    private val sharedStorage = sharedStorageEnabled

    override val access = HostAccess.LOCAL
    override val capabilities = HostCapabilities()
    override val sharedStorageEnabled: Boolean get() = sharedStorage()
    override val files: FileSystem = FileSystem.SYSTEM
    override val system: ShellExecutor? = null
    override val promptLines = localPromptLines

    override fun bash(cwd: String): ShellExecutor = prootBash(cwd)
}

class AdbHostEnvironment(
    private val prootBash: (String) -> ShellExecutor,
    sharedStorageEnabled: () -> Boolean,
    private val adbShell: ShellExecutor,
) : HostEnvironment {
    private val sharedStorage = sharedStorageEnabled

    override val access = HostAccess.ADB
    override val capabilities = HostCapabilities(hasSystemShell = true)
    override val sharedStorageEnabled: Boolean get() = sharedStorage()
    override val files: FileSystem = FileSystem.SYSTEM
    override val system: ShellExecutor = adbShell
    override val promptLines = adbPromptLines

    override fun bash(cwd: String): ShellExecutor = prootBash(cwd)
}

class RootHostEnvironment(
    private val chrootBash: (String) -> ShellExecutor,
    private val rootFiles: FileSystem,
    private val rootShell: ShellExecutor,
    sharedStorageEnabled: () -> Boolean,
    private val readiness: () -> RootBashReadiness,
) : HostEnvironment {
    private val sharedStorage = sharedStorageEnabled

    override val access = HostAccess.ROOT
    override val capabilities = HostCapabilities(
        isRoot = true,
        hasSystemShell = true,
        canReadSystemData = true,
    )
    override val sharedStorageEnabled: Boolean get() = sharedStorage()
    override val promptLines = rootPromptLines

    override fun bash(cwd: String): ShellExecutor {
        requireReady()
        return chrootBash(cwd)
    }

    override val files: FileSystem
        get() {
            requireReady()
            return rootFiles
        }

    override val system: ShellExecutor
        get() {
            requireReady()
            return rootShell
        }

    private fun requireReady() {
        when (readiness()) {
            RootBashReadiness.READY -> Unit
            RootBashReadiness.PREPARING -> throw HostAccessUnavailableException("Root Linux is being prepared")
            RootBashReadiness.FAILED -> throw HostAccessUnavailableException("Root Linux preparation failed")
        }
    }
}
