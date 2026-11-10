package io.github.whywood.deepdeep.shell

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface RootfsState {
    data object Idle : RootfsState
    data object Preparing : RootfsState
    data object Ready : RootfsState
    data class Failed(val reason: String) : RootfsState
}

data class RootfsStatus(val state: RootfsState = RootfsState.Idle)

class RootfsBootstrap internal constructor(
    private val appRootfsDir: File,
    private val statusFile: File,
    private val scratchDir: File,
    private val linuxDir: File,
    private val runner: RootCommandRunner,
    private val ioDispatcher: CoroutineDispatcher,
) {
    constructor(
        appRootfsDir: File,
        statusFile: File,
        scratchDir: File,
        linuxDir: File = File(DEFAULT_LINUX_DIR),
    ) : this(
        appRootfsDir = appRootfsDir,
        statusFile = statusFile,
        scratchDir = scratchDir,
        linuxDir = linuxDir,
        runner = ProcessRootCommandRunner(),
        ioDispatcher = Dispatchers.IO,
    )

    private val mutex = Mutex()
    private val _status = MutableStateFlow(initialStatus())

    val status: StateFlow<RootfsStatus> = _status.asStateFlow()

    fun cachedStatus(): RootfsStatus = _status.value

    suspend fun ensureReady(): RootfsStatus = mutex.withLock {
        val current = _status.value.state
        if (current is RootfsState.Preparing) return@withLock RootfsStatus(current)
        prepareLocked()
    }

    suspend fun rebuild(): RootfsStatus = mutex.withLock {
        val current = _status.value.state
        if (current is RootfsState.Preparing) return@withLock RootfsStatus(current)
        publish(RootfsState.Preparing)
        val cleanup = withContext(ioDispatcher) {
            runCatching {
                runner.run(
                    "rm -rf ${ChrootExecutor.shellQuote(rootfsDir.absolutePath)} " +
                        ChrootExecutor.shellQuote(tempRootfsDir.absolutePath),
                    PREPARE_TIMEOUT_MILLIS,
                    null,
                )
            }
        }
        val cleanupFailure = cleanup.exceptionOrNull()
        if (cleanupFailure != null) {
            return@withLock fail("Root Linux cleanup failed: ${cleanupFailure.message ?: cleanupFailure.javaClass.simpleName}")
        }
        val cleanupResult = cleanup.getOrThrow()
        if (!cleanupResult.started) return@withLock fail("su is not available")
        if (cleanupResult.timedOut) return@withLock fail("Root Linux preparation timed out")
        prepareLocked()
    }

    private suspend fun prepareLocked(): RootfsStatus {
        publish(RootfsState.Preparing)
        val result = withContext(ioDispatcher) {
            runCatching {
                writeScratchFiles()
                runner.run(
                    "sh ${ChrootExecutor.shellQuote(bootstrapScriptFile.absolutePath)}",
                    PREPARE_TIMEOUT_MILLIS,
                    null,
                )
            }
        }
        val failure = result.exceptionOrNull()
        if (failure != null) {
            return fail("Root Linux preparation failed: ${failure.message ?: failure.javaClass.simpleName}")
        }
        val output = result.getOrThrow()
        if (!output.started) return fail("su is not available")
        if (output.timedOut) return fail("Root Linux preparation timed out")
        val text = output.stdout
        return when {
            text.contains(PREPARE_READY) || text.contains(PREPARE_ALREADY) -> {
                persistStatus(STATUS_READY)
                publish(RootfsState.Ready)
                RootfsStatus(RootfsState.Ready)
            }
            text.contains(PREPARE_NO_BUSYBOX) -> fail("busybox not found")
            text.contains(PREPARE_COPY_FAILED) -> fail("copy failed")
            text.contains(PREPARE_PROBE_FAILED) -> fail("chroot probe failed")
            else -> fail("Root Linux preparation failed with exit code ${output.exitCode}")
        }
    }

    suspend fun reset() {
        withContext(ioDispatcher) {
            runCatching { statusFile.delete() }
            runCatching { scratchDir.deleteRecursively() }
        }
        publish(RootfsState.Idle)
    }

    private fun fail(reason: String): RootfsStatus {
        persistStatus(STATUS_FAILED_PREFIX + reason)
        publish(RootfsState.Failed(reason))
        return RootfsStatus(RootfsState.Failed(reason))
    }

    private fun publish(state: RootfsState) {
        _status.value = RootfsStatus(state)
    }

    private fun initialStatus(): RootfsStatus {
        val raw = runCatching { statusFile.readText().trim() }.getOrNull()
        return if (raw == STATUS_READY) RootfsStatus(RootfsState.Ready) else RootfsStatus(RootfsState.Idle)
    }

    private fun persistStatus(value: String) {
        runCatching {
            statusFile.parentFile?.mkdirs()
            statusFile.writeText(value)
        }
    }

    private val rootfsDir: File get() = File(linuxDir, ROOTFS_DIR_NAME)
    private val tempRootfsDir: File get() = File(linuxDir, TEMP_ROOTFS_DIR_NAME)

    private fun writeScratchFiles() {
        check(scratchDir.isDirectory || scratchDir.mkdirs()) { "Cannot create scratch directory: $scratchDir" }
        bootstrapScriptFile.writeText(
            buildBootstrapScript(
                appRootfs = appRootfsDir,
                linuxDir = linuxDir,
                runScript = runScriptFile,
                profileFile = profileFile,
            ),
        )
        runScriptFile.writeText(buildRunScript(linuxDir))
        profileFile.writeText(buildProfileScript())
    }

    internal val bootstrapScriptFile: File get() = File(scratchDir, "rootfs-bootstrap.sh")
    internal val runScriptFile: File get() = File(scratchDir, "run.sh")
    internal val profileFile: File get() = File(scratchDir, "deepdeep-profile.sh")

    companion object {
        const val DEFAULT_LINUX_DIR = "/data/adb/deepdeep/linux"
        private const val ROOTFS_DIR_NAME = "rootfs"
        private const val TEMP_ROOTFS_DIR_NAME = "rootfs.tmp"
        private const val PREPARE_TIMEOUT_MILLIS = 10 * 60 * 1000L
        private const val STATUS_READY = "ready"
        private const val STATUS_FAILED_PREFIX = "failed:"
        private const val PREPARE_READY = "DEEPDEEP_READY"
        private const val PREPARE_ALREADY = "DEEPDEEP_ALREADY"
        private const val PREPARE_NO_BUSYBOX = "DEEPDEEP_NO_BUSYBOX"
        private const val PREPARE_COPY_FAILED = "DEEPDEEP_COPY_FAILED"
        private const val PREPARE_PROBE_FAILED = "DEEPDEEP_PROBE_FAILED"

        internal fun buildBootstrapScript(
            appRootfs: File,
            linuxDir: File,
            runScript: File,
            profileFile: File,
        ): String = buildString {
            appendLine("set -e")
            appendLine("APP_ROOT=${ChrootExecutor.shellQuote(ChrootExecutor.suPath(appRootfs.absolutePath))}")
            appendLine("LINUX=${ChrootExecutor.shellQuote(linuxDir.absolutePath)}")
            appendLine("ROOT=\"\$LINUX/$ROOTFS_DIR_NAME\"")
            appendLine("TMP=\"\$LINUX/rootfs.tmp\"")
            appendLine("RUN_SCRIPT=${ChrootExecutor.shellQuote(ChrootExecutor.suPath(runScript.absolutePath))}")
            appendLine("PROFILE=${ChrootExecutor.shellQuote(ChrootExecutor.suPath(profileFile.absolutePath))}")
            appendLine("mkdir -p \"\$LINUX\"")
            appendLine("chmod 700 \"\$LINUX\" 2>/dev/null || true")
            appendLine("if [ -f \"\$ROOT/.deepdeep-ready\" ] && [ -x \"\$ROOT/bin/bash\" ]; then")
            appendLine("  cp \"\$RUN_SCRIPT\" \"\$LINUX/run.sh\" || { echo $PREPARE_COPY_FAILED; exit 12; }")
            appendLine("  chmod 700 \"\$LINUX/run.sh\"")
            appendLine("  cp \"\$PROFILE\" \"\$ROOT/etc/profile.d/deepdeep.sh\" 2>/dev/null || true")
            appendLine("  if [ -L \"\$ROOT/sdcard\" ]; then rm -f \"\$ROOT/sdcard\"; fi")
            appendLine("  [ -e \"\$ROOT/sdcard\" ] || ln -s /storage/emulated/0 \"\$ROOT/sdcard\"")
            appendLine("  echo $PREPARE_ALREADY")
            appendLine("  exit 0")
            appendLine("fi")
            appendLine("BB=/data/adb/ap/bin/busybox")
            appendLine("[ -x \"\$BB\" ] || BB=/data/adb/magisk/busybox")
            appendLine("[ -x \"\$BB\" ] || BB=\$(command -v busybox 2>/dev/null || true)")
            appendLine("if [ -z \"\$BB\" ] || [ ! -x \"\$BB\" ]; then echo $PREPARE_NO_BUSYBOX; exit 10; fi")
            appendLine("rm -rf \"\$TMP\"")
            appendLine("mkdir -p \"\$TMP\"")
            appendLine("\"\$BB\" cp -a \"\$APP_ROOT/.\" \"\$TMP/\" || { echo $PREPARE_COPY_FAILED; exit 12; }")
            appendLine("mkdir -p \"\$TMP/data\" \"\$TMP/system\" \"\$TMP/vendor\" \"\$TMP/product\" \"\$TMP/system_ext\" \"\$TMP/odm\" \"\$TMP/.deepdeep-empty\" \"\$TMP/storage/emulated/0\" \"\$TMP/etc/profile.d\"")
            appendLine("rm -rf \"\$TMP/sdcard\"")
            appendLine("ln -s /storage/emulated/0 \"\$TMP/sdcard\"")
            appendLine("cp \"\$PROFILE\" \"\$TMP/etc/profile.d/deepdeep.sh\"")
            appendLine("chmod 644 \"\$TMP/etc/profile.d/deepdeep.sh\"")
            appendLine("cp \"\$RUN_SCRIPT\" \"\$LINUX/run.sh\"")
            appendLine("chmod 700 \"\$LINUX/run.sh\"")
            appendLine("rm -rf \"\$ROOT\"")
            appendLine("mv \"\$TMP\" \"\$ROOT\"")
            appendLine("touch \"\$ROOT/.deepdeep-ready\"")
            appendLine("/system/bin/chroot \"\$ROOT\" /bin/bash -c 'echo DEEPDEEP_PROBE_OK' >/dev/null 2>&1 || { echo $PREPARE_PROBE_FAILED; exit 11; }")
            appendLine("echo $PREPARE_READY")
        }

        internal fun buildRunScript(linuxDir: File): String {
            val root = File(linuxDir, ROOTFS_DIR_NAME).absolutePath
            require(!root.contains('\'')) { "rootfs path must not contain single quotes" }
            return buildString {
                appendLine("#!/system/bin/sh")
                appendLine("set -e")
                appendLine("WS=\"\$1\"")
                appendLine("RESOLV=\"\$3\"")
                appendLine("NSENTER=\"\"")
                appendLine("if /system/bin/nsenter -t 1 -m -- /system/bin/true >/dev/null 2>&1; then NSENTER=\"/system/bin/nsenter -t 1 -m --\"; fi")
                append("exec \$NSENTER /system/bin/unshare -m /system/bin/sh -c '")
                appendLine()
                appendLine("set -e")
                appendLine("ROOT=$root")
                appendLine("BB=/data/adb/ap/bin/busybox")
                appendLine("[ -x \"\$BB\" ] || BB=/data/adb/magisk/busybox")
                appendLine("[ -x \"\$BB\" ] || BB=\$(command -v busybox 2>/dev/null || true)")
                appendLine("[ -n \"\$BB\" ] || { echo \"deepdeep root bash: busybox not found\" >&2; exit 90; }")
                appendLine("\"\$BB\" mount --make-rprivate /")
                appendLine("mount -t proc proc \"\$ROOT/proc\"")
                appendLine("mount -t sysfs sysfs \"\$ROOT/sys\"")
                appendLine("mount --rbind /dev \"\$ROOT/dev\"")
                appendLine("if [ -d /dev/binderfs ]; then mount --rbind /dev/binderfs \"\$ROOT/dev/binderfs\" 2>/dev/null || true; fi")
                appendLine("mkdir -p \"\$ROOT/.deepdeep-empty\" \"\$ROOT/data\" \"\$ROOT/system\" \"\$ROOT/vendor\" \"\$ROOT/product\" \"\$ROOT/system_ext\" \"\$ROOT/odm\" \"\$ROOT/storage/emulated/0\"")
                appendLine("mount --rbind /data \"\$ROOT/data\"")
                appendLine("mount --bind \"\$ROOT/.deepdeep-empty\" \"\$ROOT/data/adb/deepdeep\"")
                appendLine("for d in system vendor product system_ext odm; do if [ -d \"/\$d\" ]; then mkdir -p \"\$ROOT/\$d\"; mount --rbind \"/\$d\" \"\$ROOT/\$d\"; mount -o remount,bind,ro \"\$ROOT/\$d\" 2>/dev/null || true; fi; done")
                appendLine("mkdir -p \"\$ROOT/apex/com.android.runtime\" \"\$ROOT/apex/com.android.tzdata\" \"\$ROOT/linkerconfig\"")
                appendLine("if [ -d /apex/com.android.runtime ]; then mount --rbind /apex/com.android.runtime \"\$ROOT/apex/com.android.runtime\"; mount -o remount,bind,ro \"\$ROOT/apex/com.android.runtime\" 2>/dev/null || true; fi")
                appendLine("if [ -d /apex/com.android.tzdata ]; then mount --rbind /apex/com.android.tzdata \"\$ROOT/apex/com.android.tzdata\"; mount -o remount,bind,ro \"\$ROOT/apex/com.android.tzdata\" 2>/dev/null || true; fi")
                appendLine("if [ -d /linkerconfig ]; then mount --rbind /linkerconfig \"\$ROOT/linkerconfig\"; mount -o remount,bind,ro \"\$ROOT/linkerconfig\" 2>/dev/null || true; fi")
                appendLine("mount --bind /storage/emulated/0 \"\$ROOT/storage/emulated/0\"")
                appendLine("rm -rf \"\$ROOT/sdcard\"")
                appendLine("ln -s /storage/emulated/0 \"\$ROOT/sdcard\"")
                appendLine("case \"\$1\" in")
                appendLine("  /data/*|/storage/*|/) ;;")
                appendLine("  \"\")")
                appendLine("    ;;")
                appendLine("  *)")
                appendLine("    if [ -e \"\$ROOT\$1\" ]; then")
                appendLine("      :")
                appendLine("    elif [ -d \"\$1\" ]; then")
                appendLine("      mkdir -p \"\$ROOT\$1\"")
                appendLine("      mount --rbind \"\$1\" \"\$ROOT\$1\"")
                appendLine("    else")
                appendLine("      mkdir -p \"\$ROOT\$1\"")
                appendLine("    fi")
                appendLine("    ;;")
                appendLine("esac")
                appendLine("if [ -n \"\$3\" ] && [ -f \"\$3\" ]; then mount --bind \"\$3\" \"\$ROOT/etc/resolv.conf\"; fi")
                appendLine("TZ_NAME=\$(/system/bin/getprop persist.sys.timezone 2>/dev/null || true)")
                appendLine("[ -n \"\$TZ_NAME\" ] || TZ_NAME=UTC")
                appendLine("if [ -d \"\$ROOT/system/usr/share/zoneinfo\" ]; then")
                appendLine("  mkdir -p \"\$ROOT/usr/share\" \"\$ROOT/etc\"")
                appendLine("  ln -sfn /system/usr/share/zoneinfo \"\$ROOT/usr/share/zoneinfo\"")
                appendLine("  if [ -e \"\$ROOT/system/usr/share/zoneinfo/\$TZ_NAME\" ]; then ln -sfn \"/usr/share/zoneinfo/\$TZ_NAME\" \"\$ROOT/etc/localtime\"; fi")
                appendLine("fi")
                appendLine("exec /system/bin/env -i HOME=/root TERM=dumb LANG=C.UTF-8 TZ=\"\$TZ_NAME\" DEEPDEEP_CWD=\"\$1\" chroot \"\$ROOT\" /bin/bash -ls 2>&1")
                appendLine("' deepdeep \"\$WS\" \"\" \"\$RESOLV\"")
            }
        }

        internal fun buildProfileScript(): String = buildString {
            appendLine("export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin")
            appendLine("export HOME=/root")
            appendLine("export TERM=dumb")
            appendLine("export LANG=C.UTF-8")
            appendLine(": \"\${TZ:=UTC}\"")
            appendLine("export TZ")
            appendLine("if [ ! -e \"/usr/share/zoneinfo/\$TZ\" ] && [ ! -e /etc/localtime ]; then")
            appendLine("  case \"\$TZ\" in")
            appendLine("    Asia/Shanghai|Asia/Chongqing|Asia/Harbin) export TZ=CST-8 ;;")
            appendLine("  esac")
            appendLine("fi")
            appendLine("cd -- \"\${DEEPDEEP_CWD:-/}\" 2>/dev/null || true")
        }
    }
}
