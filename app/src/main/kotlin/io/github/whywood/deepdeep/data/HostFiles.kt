package io.github.whywood.deepdeep.data

import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import io.github.whywood.deepdeep.runtime.host.HostEnvironment
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import okio.Buffer
import okio.FileMetadata
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

class HostFiles(
    private val hostEnvironment: () -> HostEnvironmentProvider,
    private val stagingRoot: File,
) {
    fun available(): Boolean = runCatching { fileSystem() }.isSuccess

    fun environment(): HostEnvironment? = runCatching { hostEnvironment().current() }.getOrNull()

    fun capabilities(): HostCapabilities = environment()?.capabilities ?: HostCapabilities()

    fun canonical(file: File): File? = runCatching {
        File(fileSystem().canonicalize(file.absolutePath.toPath()).toString())
    }.getOrNull()

    fun metadata(file: File): FileMetadata? =
        runCatching { fileSystem().metadataOrNull(file.absolutePath.toPath()) }.getOrNull()

    fun isFile(file: File): Boolean = metadata(file)?.isRegularFile == true

    fun isDirectory(file: File): Boolean = metadata(file)?.isDirectory == true

    fun length(file: File): Long = metadata(file)?.size ?: 0L

    fun list(directory: File): List<File>? = runCatching {
        fileSystem().list(directory.absolutePath.toPath()).map { File(it.toString()) }
    }.getOrNull()

    fun openStream(file: File): InputStream? = runCatching {
        val source = fileSystem().source(file.absolutePath.toPath()).buffer()
        object : InputStream() {
            override fun read(): Int {
                val byte = ByteArray(1)
                val count = source.read(byte, 0, 1)
                return if (count == -1) -1 else byte[0].toInt() and 0xFF
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                source.read(bytes, offset, length)

            override fun close() {
                source.close()
            }
        }
    }.getOrNull()

    fun readText(file: File, limitBytes: Int = 256 * 1024): TextFileContent? {
        val limit = limitBytes.coerceAtLeast(1)
        return openStream(file)?.use { input ->
            val buffer = ByteArray(limit + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            val truncated = total > limit
            val size = if (truncated) limit else total
            for (index in 0 until size) {
                if (buffer[index] == 0.toByte()) return null
            }
            TextFileContent(String(buffer, 0, size, Charsets.UTF_8), truncated)
        }
    }

    fun write(target: File, input: InputStream, limitBytes: Long): Boolean {
        val parent = target.parentFile ?: return false
        ensureDirectories(parent)
        val copied = runCatching {
            fileSystem().sink(target.absolutePath.toPath()).buffer().use { sink ->
                val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > limitBytes) throw IOException("attachment too large")
                    sink.write(chunk, 0, read)
                }
                true
            }
        }.getOrDefault(false)
        if (!copied) delete(target)
        return copied
    }

    fun stage(file: File, name: String): File? {
        if (!isFile(file)) return null
        val size = length(file)
        if (size <= 0L || size > STAGE_MAX_BYTES) return null
        stagingRoot.mkdirs()
        pruneStaging()
        val directory = File(stagingRoot, UUID.randomUUID().toString().replace("-", "").take(8))
        directory.mkdirs()
        val target = File(directory, sanitizeAttachmentName(name))
        val staged = runCatching {
            fileSystem().source(file.absolutePath.toPath()).buffer().use { source ->
                FileSystem.SYSTEM.sink(target.absolutePath.toPath()).buffer().use { sink ->
                    val buffer = Buffer()
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer, STAGE_CHUNK)
                        if (read == -1L) break
                        total += read
                        sink.write(buffer, read)
                        if (total > STAGE_MAX_BYTES) throw IOException("attachment too large")
                    }
                    true
                }
            }
        }.getOrDefault(false)
        if (!staged) delete(target)
        return target.takeIf { staged }
    }

    fun delete(file: File): Boolean = runCatching { fileSystem().delete(file.absolutePath.toPath()) }.isSuccess

    private fun fileSystem(): FileSystem = hostEnvironment().current().files

    private fun ensureDirectories(directory: File) {
        val missing = ArrayDeque<File>()
        var current: File? = directory
        while (current != null && !isDirectory(current)) {
            missing.addFirst(current)
            current = current.parentFile
        }
        missing.forEach { runCatching { fileSystem().createDirectory(it.absolutePath.toPath()) } }
    }

    private fun pruneStaging() {
        val cutoff = System.currentTimeMillis() - STAGE_MAX_AGE_MS
        stagingRoot.listFiles()?.forEach { child ->
            if (child.lastModified() < cutoff) runCatching { child.deleteRecursively() }
        }
    }

    private companion object {
        const val STAGE_CHUNK = 64L * 1024L
        const val STAGE_MAX_BYTES = 2L * 1024L * 1024L * 1024L
        const val STAGE_MAX_AGE_MS = 60L * 60L * 1000L
    }
}
