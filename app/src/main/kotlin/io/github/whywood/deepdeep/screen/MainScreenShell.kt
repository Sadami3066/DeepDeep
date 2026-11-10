package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor

interface MainScreenShell {
    val available: Boolean
    suspend fun execute(command: String, timeoutMs: Long = 10000): String
}

class PrivilegedMainScreenShell(
    private val hostEnvironment: HostEnvironmentProvider,
) : MainScreenShell {
    private fun systemShell(): ShellExecutor? = runCatching { hostEnvironment.current().system }.getOrNull()

    override val available: Boolean
        get() = systemShell() != null

    override suspend fun execute(command: String, timeoutMs: Long): String {
        val shell = systemShell()
            ?: error("Main screen system input needs an active ADB or Root access tier")
        val seconds = (timeoutMs / 1000).coerceAtLeast(1)
        val result = shell.run(command, seconds, null)
        val output = result.output
        check(result.exitCode == 0 && !result.truncated) {
            "Main screen command did not confirm success (exit=${result.exitCode}, truncated=${result.truncated}); observe the current screen again and do not rerun automatically. ${output.takeLast(500)}"
        }
        return output
    }
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
