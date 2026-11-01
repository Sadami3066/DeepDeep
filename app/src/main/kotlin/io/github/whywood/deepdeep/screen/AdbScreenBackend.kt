package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot

class AdbScreenBackend(
    private val adbProvider: () -> AdbBootstrap?,
) : ScreenAdbBackend {
    @Volatile
    private var cachedAt = 0L

    @Volatile
    private var cachedAvailable = false

    override suspend fun available(): Boolean {
        val now = System.currentTimeMillis()
        if (now - cachedAt < AVAILABILITY_CACHE_MS) return cachedAvailable
        val bootstrap = runCatching { adbProvider() }.getOrNull()
        val alive = bootstrap != null && (
            bootstrap.state.value.daemon ||
                runCatching { bootstrap.isDaemonAlive() }.getOrDefault(false)
            )
        cachedAt = now
        cachedAvailable = alive
        return alive
    }

    override suspend fun observeMain(maxDepth: Int, maxNodes: Int): GuiSnapshot {
        val parsed = AdbUiAutomatorParser.parse(dumpUiHierarchy())
            ?: throw IllegalStateException("无法读取主屏界面结构：快照内容无法解析，请确认屏幕已解锁并停留在目标界面后重试")
        return GuiNodeTree.buildSnapshot(
            root = parsed.root,
            packageName = parsed.packageName,
            windowTitle = null,
            maxDepth = maxDepth,
            maxNodes = maxNodes,
        )
    }

    override suspend fun tap(x: Int, y: Int) {
        exec("input tap $x $y", INPUT_TIMEOUT_MS)
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        exec("input swipe $x1 $y1 $x2 $y2 ${durationMs.coerceIn(50L, 10_000L)}", INPUT_TIMEOUT_MS)
    }

    override suspend fun keyEvent(keyCode: Int) {
        exec("input keyevent $keyCode", INPUT_TIMEOUT_MS)
    }

    override suspend fun typeText(text: String) {
        exec(adbInputTextCommand(text), INPUT_TIMEOUT_MS)
    }

    override suspend fun shell(command: String): String = exec(command, SHELL_TIMEOUT_MS)

    private suspend fun dumpUiHierarchy(): String {
        val result = requireAdb().exec(adbUiDumpCommand(), UI_DUMP_TIMEOUT_MS)
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            throw IllegalStateException("无法读取主屏界面结构：请确认屏幕已解锁并停留在目标界面后重试")
        }
        if (result.truncated) {
            throw IllegalStateException("主屏界面结构过大，无法完整读取：请开启无障碍服务后重试")
        }
        return result.stdout
    }

    private suspend fun exec(command: String, timeoutMs: Long): String {
        val result = requireAdb().exec(command, timeoutMs)
        if (result.exitCode != 0) {
            val output = result.stdout.trim().takeLast(MAX_OUTPUT_CHARS)
            throw IllegalStateException(
                "ADB 命令失败（exit=${result.exitCode}）：${output.ifEmpty { command }}",
            )
        }
        return if (result.truncated) {
            result.stdout.take(MAX_OUTPUT_CHARS)
        } else {
            result.stdout
        }
    }

    private fun requireAdb(): AdbBootstrap = runCatching { adbProvider() }.getOrNull()
        ?: throw IllegalStateException("无线调试（ADB）未就绪：请到权限设置完成配对与连接")

    private companion object {
        const val AVAILABILITY_CACHE_MS = 2_000L
        const val UI_DUMP_TIMEOUT_MS = 20_000L
        const val INPUT_TIMEOUT_MS = 10_000L
        const val SHELL_TIMEOUT_MS = 10_000L
        const val MAX_OUTPUT_CHARS = 400
    }
}

internal const val ADB_UI_DUMP_PATH = "/data/local/tmp/deepdeep-ui.xml"

internal fun adbUiDumpCommand(): String =
    "rm -f $ADB_UI_DUMP_PATH; uiautomator dump $ADB_UI_DUMP_PATH >/dev/null 2>&1 && cat $ADB_UI_DUMP_PATH"

internal fun adbInputTextCommand(text: String): String = "input text " + shellQuote(text.replace(" ", "%s"))

internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
