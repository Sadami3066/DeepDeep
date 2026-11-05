package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.FileEntry
import io.github.whywood.deepdeep.plugin.api.FileService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FilePluginService(
    private val root: File,
    private val workspace: File?,
    private val sharedStorage: File?,
) : FileService {
    override val rootDir: String = root.absolutePath

    override val workspaceDir: String? = workspace?.absolutePath

    val sharedStorageDir: String? = sharedStorage?.absolutePath

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val directory = resolveFile(path)
        if (!directory.isDirectory) return@withContext emptyList()
        directory.listFiles().orEmpty().sortedBy { it.name }.map { file ->
            FileEntry(
                path = relativePath(file),
                name = file.name,
                directory = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                modifiedAt = file.lastModified(),
            )
        }
    }

    override suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        resolveFile(path).readText(Charsets.UTF_8)
    }

    override suspend fun writeText(path: String, content: String, append: Boolean) = withContext(Dispatchers.IO) {
        val file = resolveFile(path)
        file.parentFile?.mkdirs()
        if (append) file.appendText(content, Charsets.UTF_8) else file.writeText(content, Charsets.UTF_8)
        Unit
    }

    override suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        resolveFile(path).readBytes()
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = resolveFile(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        Unit
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveFile(path)
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolveFile(path).exists()
    }

    override suspend fun resolve(path: String): String = withContext(Dispatchers.IO) {
        resolveFile(path).absolutePath
    }

    private fun resolveFile(path: String): File {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return root
        val file = File(trimmed)
        return if (file.isAbsolute) file else File(root, trimmed)
    }

    private fun relativePath(file: File): String =
        runCatching { file.relativeTo(root).path }.getOrDefault(file.absolutePath)
}
