package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.FileEntry
import io.github.whywood.deepdeep.plugin.api.FileService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer

internal class FilePluginService(
    private val root: File,
    private val workspace: File?,
    private val sharedStorage: File?,
    private val files: () -> FileSystem = { FileSystem.SYSTEM },
) : FileService {
    private val rootPath: Path = root.absolutePath.toPath()

    override val rootDir: String = root.absolutePath

    override val workspaceDir: String? = workspace?.absolutePath

    val sharedStorageDir: String? = sharedStorage?.absolutePath

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val fileSystem = files()
        fileSystem.listOrNull(resolvePath(fileSystem, path)).orEmpty().sortedBy { it.name }.map { child ->
            val metadata = fileSystem.metadataOrNull(child)
            val directory = metadata?.isDirectory == true
            FileEntry(
                path = relativePath(child),
                name = child.name,
                directory = directory,
                size = if (directory) 0L else metadata?.size ?: 0L,
                modifiedAt = metadata?.lastModifiedAtMillis ?: 0L,
            )
        }
    }

    override suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        val fileSystem = files()
        fileSystem.source(resolvePath(fileSystem, path)).buffer().use { it.readUtf8() }
    }

    override suspend fun writeText(path: String, content: String, append: Boolean) = withContext(Dispatchers.IO) {
        val fileSystem = files()
        val file = resolvePath(fileSystem, path)
        prepareParent(fileSystem, file)
        val sink = if (append) fileSystem.appendingSink(file) else fileSystem.sink(file)
        sink.buffer().use { it.writeUtf8(content) }
        Unit
    }

    override suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val fileSystem = files()
        fileSystem.source(resolvePath(fileSystem, path)).buffer().use { it.readByteArray() }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val fileSystem = files()
        val file = resolvePath(fileSystem, path)
        prepareParent(fileSystem, file)
        fileSystem.sink(file).buffer().use { it.write(bytes) }
        Unit
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val fileSystem = files()
        val file = resolvePath(fileSystem, path)
        if (!fileSystem.exists(file)) {
            false
        } else {
            if (fileSystem.metadataOrNull(file)?.isDirectory == true) {
                fileSystem.deleteRecursively(file, mustExist = false)
            } else {
                fileSystem.delete(file, mustExist = false)
            }
            true
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        val fileSystem = files()
        fileSystem.exists(resolvePath(fileSystem, path))
    }

    override suspend fun resolve(path: String): String = withContext(Dispatchers.IO) {
        val fileSystem = files()
        resolvePath(fileSystem, path).toString()
    }

    private fun resolvePath(fileSystem: FileSystem, path: String): Path {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return rootPath
        val candidate = trimmed.toPath()
        return if (candidate.isAbsolute) candidate else rootPath.resolve(candidate)
    }

    private fun prepareParent(fileSystem: FileSystem, file: Path) {
        val parent = file.parent ?: return
        if (!fileSystem.exists(parent)) fileSystem.createDirectories(parent)
    }

    private fun relativePath(file: Path): String =
        runCatching { file.relativeTo(rootPath).toString() }.getOrDefault(file.toString())
}
