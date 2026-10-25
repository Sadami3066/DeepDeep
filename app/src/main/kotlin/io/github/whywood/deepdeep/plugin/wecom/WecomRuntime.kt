package io.github.whywood.deepdeep.plugin.wecom

import android.content.Context
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.shell.LinuxResult
import java.io.File

internal interface WecomRuntime {
    val isSupported: Boolean

    suspend fun isInstalled(): Boolean

    suspend fun isEnabled(): Boolean

    suspend fun execute(command: String, timeoutMillis: Long): LinuxResult

    suspend fun executeStreaming(
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit,
    ): LinuxResult
}

internal class LinuxWecomRuntime(
    private val context: Context,
    private val linux: LinuxEnvironment,
    private val settings: LinuxSettingsStore,
) : WecomRuntime {
    override val isSupported: Boolean
        get() = linux.isSupported

    override suspend fun isInstalled(): Boolean = linux.state.value is LinuxInstallState.Installed

    override suspend fun isEnabled(): Boolean = settings.isEnabled()

    override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult =
        linux.execute(command, timeoutMillis)

    override suspend fun executeStreaming(
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit,
    ): LinuxResult = linux.executeStreaming(command, timeoutMillis, onOutputLine)

    fun staging(workspaceDir: File): WecomRuntime = LinuxWecomRuntime(
        context,
        LinuxEnvironment(context, workspaceDir = workspaceDir),
        settings,
    )
}
