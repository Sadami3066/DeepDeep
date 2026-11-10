package io.github.whywood.deepdeep.runtime.host

import io.github.whywood.deepdeep.runtime.CommandResult
import io.github.whywood.deepdeep.runtime.DetailedShellExecutor
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutionException
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source

class DynamicShellExecutor(
    private val current: () -> ShellExecutor?,
) : DetailedShellExecutor {
    override suspend fun runDetailed(command: String, timeoutSeconds: Long?, signal: RunSignal?): CommandResult {
        val executor = current()
            ?: throw ShellExecutionException("The Linux shell is not available in the current host environment.")
        if (executor is DetailedShellExecutor) return executor.runDetailed(command, timeoutSeconds, signal)
        val result = executor.run(command, timeoutSeconds, signal)
        return CommandResult(result.output, result.exitCode, truncated = result.truncated)
    }
}

class DynamicFileSystem(
    private val current: () -> FileSystem,
) : FileSystem() {
    override fun canonicalize(path: Path): Path = current().canonicalize(path)

    override fun metadataOrNull(path: Path): FileMetadata? = current().metadataOrNull(path)

    override fun list(dir: Path): List<Path> = current().list(dir)

    override fun listOrNull(dir: Path): List<Path>? = current().listOrNull(dir)

    override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> =
        current().listRecursively(dir, followSymlinks)

    override fun openReadOnly(file: Path): FileHandle = current().openReadOnly(file)

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
        current().openReadWrite(file, mustCreate, mustExist)

    override fun source(file: Path): Source = current().source(file)

    override fun sink(file: Path, mustCreate: Boolean): Sink = current().sink(file, mustCreate)

    override fun appendingSink(file: Path, mustExist: Boolean): Sink = current().appendingSink(file, mustExist)

    override fun createDirectory(dir: Path, mustCreate: Boolean) = current().createDirectory(dir, mustCreate)

    override fun atomicMove(source: Path, target: Path) = current().atomicMove(source, target)

    override fun copy(source: Path, target: Path) = current().copy(source, target)

    override fun delete(path: Path, mustExist: Boolean) = current().delete(path, mustExist)

    override fun deleteRecursively(file: Path, mustExist: Boolean) = current().deleteRecursively(file, mustExist)

    override fun createSymlink(source: Path, target: Path) = current().createSymlink(source, target)

    override fun close() = Unit
}
