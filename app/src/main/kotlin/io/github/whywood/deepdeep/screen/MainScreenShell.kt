package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostAccessController
import io.github.whywood.deepdeep.shell.RootShell

interface MainScreenShell {
    val available: Boolean
    suspend fun execute(command: String, timeoutMs: Long = 10000): String
}

class PrivilegedMainScreenShell(
    private val access: HostAccessController,
    private val root: RootShell,
    private val adb: AdbBootstrap,
) : MainScreenShell {
    override val available: Boolean
        get() = access.state.value.effective.let { it == HostAccess.ADB || it == HostAccess.ROOT }

    override suspend fun execute(command: String, timeoutMs: Long): String {
        check(available) { "Main screen system input needs authorized and available ADB or Root" }
        val (exitCode, output, truncated, timedOut) = when (access.state.value.effective) {
            HostAccess.ROOT -> root.execute(command, timeoutMs).let { Result(it.exitCode, it.stdout + it.stderr, it.truncated, it.timedOut) }
            HostAccess.ADB -> adb.exec(command, timeoutMs).let { Result(it.exitCode, it.stdout + it.stderr, it.truncated, it.timedOut) }
            HostAccess.LOCAL -> error("Main screen system input is not enabled")
        }
        check(!timedOut && exitCode == 0 && !truncated) {
            "Main screen command did not confirm success (exit=$exitCode, timeout=$timedOut, truncated=$truncated); observe the current screen again and do not rerun automatically. ${output.takeLast(500)}"
        }
        return output
    }

    private data class Result(val exitCode: Int, val output: String, val truncated: Boolean, val timedOut: Boolean)
}

internal fun mainTaskDisplays(output: String, packageName: String): Set<Int> {
    check(output.contains("ACTIVITY MANAGER ACTIVITIES")) { "Cannot confirm the screen the app is on; the app was not launched" }
    var display: Int? = null
    val result = mutableSetOf<Int>()
    val displayHeader = Regex("^Display #(\\d+)\\b")
    val record = Regex("ActivityRecord\\{[^}]*\\s${Regex.escape(packageName)}/")
    output.lineSequence().forEach { line ->
        displayHeader.find(line.trim())?.let { display = it.groupValues[1].toInt() }
        if (record.containsMatchIn(line)) result += checkNotNull(display) { "App task has no display information; the app was not launched" }
    }
    check(result.isNotEmpty() || !output.contains("$packageName/")) { "Cannot parse the screen the app is on; the app was not launched" }
    return result
}

internal fun mainShellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
