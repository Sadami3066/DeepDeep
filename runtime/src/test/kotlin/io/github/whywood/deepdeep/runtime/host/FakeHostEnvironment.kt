package io.github.whywood.deepdeep.runtime.host

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import okio.FileSystem

internal object FakeShellExecutor : ShellExecutor {
    override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult =
        ShellResult(output = "", exitCode = 0)
}

internal class FakeHostEnvironment(
    override val access: HostAccess = HostAccess.LOCAL,
    override val capabilities: HostCapabilities = HostCapabilities(),
    override val sharedStorageEnabled: Boolean = false,
    private val bashExecutor: ShellExecutor = FakeShellExecutor,
    override val files: FileSystem = FileSystem.SYSTEM,
    override val system: ShellExecutor? = null,
    override val promptLines: List<String> = emptyList(),
) : HostEnvironment {
    override fun bash(cwd: String): ShellExecutor = bashExecutor
}
