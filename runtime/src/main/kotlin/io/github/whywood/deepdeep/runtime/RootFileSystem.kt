package io.github.whywood.deepdeep.runtime

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import okio.Buffer
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import okio.sink
import okio.source

internal fun interface RootFileRunner {
    fun start(command: String): Process
}

internal class SuRootFileRunner : RootFileRunner {
    override fun start(command: String): Process {
        for (binary in ROOT_BINARIES) {
            try {
                return ProcessBuilder(binary, "-c", command).start()
            } catch (_: IOException) {
            }
        }
        throw IOException("su is unavailable")
    }

    private companion object {
        private val ROOT_BINARIES = listOf(
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
        )
    }
}

internal class RootFileSystem(
    private val runner: RootFileRunner = SuRootFileRunner(),
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val appUid: Int = -1,
    private val appGid: Int = -1,
) : ForwardingFileSystem(FileSystem.SYSTEM) {

    override fun metadataOrNull(path: Path): FileMetadata? {
        val quoted = quote(path)
        val result = run("stat -c '%F|%s' -- $quoted")
        if (result.exitCode != 0) return null
        val line = result.stdout.lineSequence().firstOrNull() ?: return null
        val type = line.substringBefore('|')
        val size = line.substringAfter('|', "").trim().toLongOrNull()
        return when {
            type == "directory" -> FileMetadata(
                isRegularFile = false,
                isDirectory = true,
                symlinkTarget = null,
                size = null,
                createdAtMillis = null,
                lastModifiedAtMillis = null,
                lastAccessedAtMillis = null,
                extras = emptyMap(),
            )
            type.contains("link") -> {
                val target = run("readlink -- $quoted")
                if (target.exitCode != 0) return null
                val value = target.stdout.trimEnd('\n')
                if (value.isEmpty()) return null
                FileMetadata(
                    isRegularFile = false,
                    isDirectory = false,
                    symlinkTarget = value.toPath(),
                    size = null,
                    createdAtMillis = null,
                    lastModifiedAtMillis = null,
                    lastAccessedAtMillis = null,
                    extras = emptyMap(),
                )
            }
            else -> FileMetadata(
                isRegularFile = true,
                isDirectory = false,
                symlinkTarget = null,
                size = size,
                createdAtMillis = null,
                lastModifiedAtMillis = null,
                lastAccessedAtMillis = null,
                extras = emptyMap(),
            )
        }
    }

    override fun source(path: Path): Source {
        val process = runner.start("cat -- ${quote(path)}")
        val input = process.inputStream.source().buffer()
        return object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = input.read(sink, byteCount)

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                runCatching { input.close() }
                waitForProcess(process)
            }
        }
    }

    override fun sink(path: Path, mustCreate: Boolean): Sink {
        val command = if (mustCreate) {
            "set -C; cat > ${quote(path)}"
        } else {
            "cat > ${quote(path)}"
        }
        val process = runner.start(command)
        val output = process.outputStream.sink().buffer()
        return object : Sink {
            override fun write(source: Buffer, byteCount: Long) = output.write(source, byteCount)

            override fun flush() = output.flush()

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                runCatching { output.close() }
                if (!waitForProcess(process)) {
                    throw IOException("root write failed: ${readError(process)}")
                }
                chown(path)
            }
        }
    }

    override fun list(dir: Path): List<Path> {
        val result = run("ls -1A -- ${quote(dir)}")
        if (result.exitCode != 0) throw IOException("root list failed: ${result.stderr.trim()}")
        return result.stdout.lineSequence()
            .filter { it.isNotBlank() }
            .map { dir / it }
            .toList()
    }

    override fun listOrNull(dir: Path): List<Path>? = runCatching { list(dir) }.getOrNull()

    override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> {
        if (followSymlinks) {
            val result = run("find -L ${quote(dir)} -print")
            if (result.exitCode != 0) return emptySequence()
            return result.stdout.lineSequence()
                .filter { it.isNotBlank() }
                .map { it.toPath() }
                .toList()
                .asSequence()
        }
        return sequence {
            val queue = ArrayDeque<Path>()
            queue.addLast(dir)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                yield(current)
                val metadata = metadataOrNull(current) ?: continue
                if (metadata.isDirectory) {
                    listOrNull(current)?.forEach { queue.addLast(it) }
                }
            }
        }
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        val command = if (mustCreate) "mkdir -- ${quote(dir)}" else "mkdir -p -- ${quote(dir)}"
        val result = run(command)
        if (result.exitCode != 0) throw IOException("root mkdir failed: ${result.stderr.trim()}")
    }

    override fun atomicMove(source: Path, target: Path) {
        val result = run("mv -f -- ${quote(source)} ${quote(target)}")
        if (result.exitCode != 0) throw IOException("root move failed: ${result.stderr.trim()}")
    }

    override fun delete(path: Path, mustExist: Boolean) {
        val quoted = quote(path)
        val command = if (mustExist) {
            "{ [ -e $quoted ] || [ -L $quoted ]; } || exit 3; rm -rf -- $quoted"
        } else {
            "rm -rf -- $quoted"
        }
        val result = run(command)
        if (result.exitCode != 0) throw IOException("root delete failed: ${result.stderr.trim()}")
    }

    private fun chown(path: Path) {
        val target = when {
            appUid >= 0 && appGid >= 0 -> "$appUid:$appGid"
            appUid >= 0 -> "$appUid"
            appGid >= 0 -> ":$appGid"
            else -> return
        }
        val result = run("chown $target -- ${quote(path)}")
        if (result.exitCode != 0) throw IOException("root chown failed: ${result.stderr.trim()}")
    }

    private data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    private fun run(command: String): Result {
        val process = runner.start(command)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutReader = Thread {
            runCatching { process.inputStream.copyTo(stdout) }
        }.apply {
            isDaemon = true
            start()
        }
        val stderrReader = Thread {
            runCatching { process.errorStream.copyTo(stderr) }
        }.apply {
            isDaemon = true
            start()
        }
        val finished = runCatching { process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!finished) {
            process.destroyForcibly()
            stdoutReader.join(1_000L)
            stderrReader.join(1_000L)
            throw IOException("root command timed out")
        }
        stdoutReader.join()
        stderrReader.join()
        return Result(
            process.exitValue(),
            String(stdout.toByteArray(), Charsets.UTF_8),
            String(stderr.toByteArray(), Charsets.UTF_8),
        )
    }

    private fun waitForProcess(process: Process): Boolean {
        val finished = runCatching { process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (finished) return process.exitValue() == 0
        process.destroyForcibly()
        return false
    }

    private fun readError(process: Process): String = runCatching {
        String(process.errorStream.readBytes(), Charsets.UTF_8).trim()
    }.getOrDefault("unknown error")

    private fun quote(path: Path): String = "'" + path.toString().replace("'", "'\\''") + "'"

    private companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}

fun createRootFileSystem(appUid: Int, appGid: Int): FileSystem =
    RootFileSystem(appUid = appUid, appGid = appGid)
