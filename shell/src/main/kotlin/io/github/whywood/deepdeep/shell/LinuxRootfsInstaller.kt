package io.github.whywood.deepdeep.shell

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LinuxRootfsInstaller(
    private val rootfsDir: File,
    private val assetOpener: () -> InputStream,
    private val expectedSha256: String? = null,
    private val extractor: RootfsExtractor = RootfsExtractor(),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<LinuxInstallState> = _state.asStateFlow()

    suspend fun install(): LinuxInstallState = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = readState()
            if (current is LinuxInstallState.Installed) {
                _state.value = current
                return@withContext current
            }

            _state.value = LinuxInstallState.Installing
            val parent = rootfsDir.parentFile
                ?: throw IllegalStateException("rootfs has no parent: $rootfsDir")
            parent.mkdirs()
            removeStaleTemps(parent)

            val temp = File(parent, "${rootfsDir.name}.tmp-${UUID.randomUUID()}")
            try {
                require(temp.mkdirs()) { "cannot create install directory: $temp" }
                val digest = MessageDigest.getInstance("SHA-256")
                assetOpener().use { raw ->
                    val buffered = BufferedInputStream(java.security.DigestInputStream(raw, digest))
                    buffered.mark(2)
                    val first = buffered.read()
                    val second = buffered.read()
                    buffered.reset()
                    val tar = if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
                    val extracted = extractor.extract(tar, temp.toPath())
                    require(extracted > 0) { "rootfs archive is empty" }
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                if (expectedSha256 != null) {
                    require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        "rootfs SHA256 mismatch: expected $expectedSha256, got $actualSha256"
                    }
                }

                File(temp, WORKSPACE_DIR).mkdirs()
                File(temp, MARKER_FILE).writeText(
                    "version=$VERSION\nsha256=$actualSha256\n",
                )

                if (rootfsDir.exists()) rootfsDir.deleteRecursively()
                require(temp.renameTo(rootfsDir)) { "cannot activate rootfs: $rootfsDir" }
                val installed = readState()
                _state.value = installed
                installed
            } catch (error: Exception) {
                temp.deleteRecursively()
                _state.value = LinuxInstallState.Failed(error.message ?: error.javaClass.simpleName)
                throw error
            }
        }
    }

    suspend fun reset(): LinuxInstallState = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (rootfsDir.exists() && !rootfsDir.deleteRecursively()) {
                val failed = LinuxInstallState.Failed("cannot delete rootfs: $rootfsDir")
                _state.value = failed
                throw IllegalStateException(failed.message)
            }
            _state.value = LinuxInstallState.NotInstalled
            LinuxInstallState.NotInstalled
        }
    }

    suspend fun refreshState(): LinuxInstallState = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState().also { _state.value = it }
        }
    }

    fun currentState(): LinuxInstallState = readState()

    private fun readState(): LinuxInstallState {
        if (!rootfsDir.isDirectory) return LinuxInstallState.NotInstalled
        val marker = File(rootfsDir, MARKER_FILE)
        if (!marker.isFile) return LinuxInstallState.NotInstalled
        val markerText = runCatching { marker.readText() }.getOrNull() ?: return LinuxInstallState.NotInstalled
        val version = markerText.lineSequence()
            .firstOrNull { it.startsWith("version=") }
            ?.removePrefix("version=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: VERSION
        return LinuxInstallState.Installed(version, directorySize(rootfsDir.toPath()))
    }

    private fun directorySize(root: java.nio.file.Path): Long = runCatching {
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .mapToLong { path -> runCatching { Files.size(path) }.getOrDefault(0L) }
                .sum()
        }
    }.getOrDefault(0L)

    private fun removeStaleTemps(parent: File) {
        parent.listFiles { file -> file.name.startsWith("${rootfsDir.name}.tmp-") }
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        const val VERSION = "3.24.2"
        const val ROOTFS_ASSET = "alpine-minirootfs-3.24.2-aarch64.rootfs"
        const val EXPECTED_SHA256 = "9bf70a7f18ea44094cbb5f70c58f9af129c8214745743db0e68e5502cc2ce773"
        const val MARKER_FILE = ".deepdeep-rootfs"
        const val WORKSPACE_DIR = "workspace"
    }
}
