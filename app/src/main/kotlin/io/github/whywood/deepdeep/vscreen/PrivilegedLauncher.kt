package io.github.whywood.deepdeep.vscreen

import android.content.Context
import android.content.pm.PackageManager
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.shell.RootStatus
import java.io.FileInputStream
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

class PrivilegedLauncher(
    private val context: Context,
    private val privilege: PrivilegeController,
) {
    fun backends(): List<String> {
        val state = privilege.state.value
        val shizukuReady = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val rootReady = state.root == RootStatus.Granted || state.effective == PrivilegeLevel.ROOT
        return when (state.effective) {
            PrivilegeLevel.ROOT -> buildList {
                add(BACKEND_ROOT)
                if (shizukuReady) add(BACKEND_SHIZUKU)
            }
            PrivilegeLevel.SHIZUKU -> buildList {
                if (shizukuReady) add(BACKEND_SHIZUKU)
                if (rootReady) add(BACKEND_ROOT)
            }
            PrivilegeLevel.NORMAL -> emptyList()
        }
    }

    fun start(backend: String, socketName: String, nonce: String): HelperProcess {
        val command = helperCommand(socketName, nonce)
        return when (backend) {
            BACKEND_ROOT -> HelperProcess(
                backend = backend,
                process = ProcessBuilder("su", "-c", "su $SHELL_UID -c '$command'")
                    .redirectErrorStream(true)
                    .start(),
            )
            BACKEND_SHIZUKU -> startShizuku(backend, command)
            else -> throw IllegalArgumentException("未知特权后端：$backend")
        }
    }

    private fun startShizuku(backend: String, command: String): HelperProcess {
        val service = shizukuService()
        val uid = shizukuProcessUid(service)
        val remote = when (uid) {
            SHELL_UID -> service.newProcess(arrayOf("/system/bin/sh", "-c", command), null, null)
            ROOT_UID -> service.newProcess(
                arrayOf("/system/bin/sh", "-c", "su $SHELL_UID -c '$command'"),
                null,
                null,
            )
            else -> throw IllegalStateException("Shizuku 子进程身份为 uid=$uid，无法启动 shell helper")
        }
        return HelperProcess(backend = backend, remote = remote)
    }

    private fun shizukuProcessUid(service: IShizukuService): Int {
        val probed = runCatching { probeUid(service) }.getOrNull()
        if (probed != null) return probed
        val reported = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        if (reported >= 0) return reported
        throw IllegalStateException("无法确认 Shizuku 子进程身份")
    }

    private fun probeUid(service: IShizukuService): Int {
        val remote = service.newProcess(arrayOf("/system/bin/sh", "-c", "id -u"), null, null)
        return try {
            val descriptor = remote.inputStream
            val output = descriptor.use { stream ->
                FileInputStream(stream.fileDescriptor).bufferedReader().readText()
            }
            output.trim().toIntOrNull()
                ?: throw IllegalStateException("无法解析 Shizuku 子进程身份：${output.trim()}")
        } finally {
            runCatching { remote.destroy() }
        }
    }

    private fun shizukuService(): IShizukuService {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            throw IllegalStateException("Shizuku 服务未连接，请先启动 Shizuku")
        }
        if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(-1) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("Shizuku 未授权，请在权限设置中允许 DeepDeep 使用 Shizuku")
        }
        val binder = runCatching { Shizuku.getBinder() }.getOrNull()
            ?: throw IllegalStateException("Shizuku 服务未连接，请先启动 Shizuku")
        return IShizukuService.Stub.asInterface(binder)
    }

    private fun helperCommand(socketName: String, nonce: String): String {
        val apk = context.applicationInfo.sourceDir
        return "CLASSPATH=$apk /system/bin/setsid /system/bin/app_process /system/bin --nice-name=deepdeep-vscreen " +
            "${VirtualScreenHelperMain::class.java.name} --socket $socketName --nonce $nonce"
    }

    companion object {
        const val BACKEND_ROOT = "root"
        const val BACKEND_SHIZUKU = "shizuku"

        private const val SHELL_UID = 2000
        private const val ROOT_UID = 0
    }
}

class HelperProcess internal constructor(
    val backend: String,
    private val process: Process? = null,
    private val remote: IRemoteProcess? = null,
) {
    fun destroy() {
        runCatching { process?.destroy() }
        runCatching { remote?.destroy() }
    }
}
