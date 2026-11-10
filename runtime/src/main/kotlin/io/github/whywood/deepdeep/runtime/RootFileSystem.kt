package io.github.whywood.deepdeep.runtime

import java.io.IOException
import java.util.concurrent.TimeUnit
import okio.Buffer
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
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
) : ForwardingFileSystem(FileSystem.SYSTEM) {

    override fun metadataOrNull(path: Path): FileMetadata? {
        val result = run("stat -Lc '%F|%s' -- ${quote(path)}")
        if (result.exitCode != 0) return null
        val line = result.stdout.lineSequence().firstOrNull() ?: return null
        val type = line.substringBefore('|')
        val size = line.substringAfter('|', "").trim().toLongOrNull()
        return if (type == "directory") {
            FileMetadata(
                isRegularFile = false,
                isDirectory = true,
                symlinkTarget = null,
                size = null,
                createdAtMillis = null,
                lastModifiedAtMillis = null,
                lastAccessedAtMillis = null,
                extras = emptyMap(),
            )
        } else {
            FileMetadata(
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
        val process = runner.start("cat > ${quote(path)}")
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
        val result = run("rm -f -- ${quote(path)}")
        if (mustExist && result.exitCode != 0) throw IOException("root delete failed: ${result.stderr.trim()}")
    }

    private data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    private fun run(command: String): Result {
        val process = runner.start(command)
        val stdout = process.inputStream.readBytes()
        val stderr = process.errorStream.readBytes()
        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw IOException("root command timed out")
        }
        return Result(process.exitValue(), String(stdout, Charsets.UTF_8), String(stderr, Charsets.UTF_8))
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
