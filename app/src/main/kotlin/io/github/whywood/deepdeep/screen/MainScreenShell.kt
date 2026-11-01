package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.shell.RootShell

interface MainScreenShell {
    val available: Boolean
    suspend fun execute(command: String, timeoutMs: Long = 10000): String
}

class PrivilegedMainScreenShell(
    private val privilege: PrivilegeController,
    private val root: RootShell,
    private val adb: AdbBootstrap,
) : MainScreenShell {
    override val available: Boolean
        get() = privilege.state.value.let { it.selected != PrivilegeLevel.NORMAL && it.effective in it.availableBackends }

    override suspend fun execute(command: String, timeoutMs: Long): String {
        check(available) { "主屏系统输入需要已授权且可用的 ADB 或 Root" }
        val state = privilege.state.value
        val (exitCode, output, truncated, timedOut) = when (state.effective) {
            PrivilegeLevel.ROOT -> root.execute(command, timeoutMs).let { Result(it.exitCode, it.stdout + it.stderr, it.truncated, it.timedOut) }
            PrivilegeLevel.ADB -> adb.exec(command, timeoutMs).let { Result(it.exitCode, it.stdout + it.stderr, it.truncated, it.timedOut) }
            else -> error("主屏系统输入未启用")
        }
        check(!timedOut && exitCode == 0 && !truncated) {
            "主屏命令未确认成功（exit=$exitCode, timeout=$timedOut, truncated=$truncated）；请重新观察当前屏幕，不要自动重跑。${output.takeLast(500)}"
        }
        return output
    }

    private data class Result(val exitCode: Int, val output: String, val truncated: Boolean, val timedOut: Boolean)
}

internal fun mainTaskDisplays(output: String, packageName: String): Set<Int> {
    check(output.contains("ACTIVITY MANAGER ACTIVITIES")) { "无法确认应用所在屏幕，未启动应用" }
    var display: Int? = null
    val result = mutableSetOf<Int>()
    val displayHeader = Regex("^Display #(\\d+)\\b")
    val record = Regex("ActivityRecord\\{[^}]*\\s${Regex.escape(packageName)}/")
    output.lineSequence().forEach { line ->
        displayHeader.find(line.trim())?.let { display = it.groupValues[1].toInt() }
        if (record.containsMatchIn(line)) result += checkNotNull(display) { "应用任务缺少屏幕信息，未启动应用" }
    }
    check(result.isNotEmpty() || !output.contains("$packageName/")) { "无法解析应用所在屏幕，未启动应用" }
    return result
}

internal fun mainShellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
