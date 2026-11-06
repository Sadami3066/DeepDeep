package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

data class LinuxResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
    val fullOutputPath: String? = null,
)

sealed interface LinuxChunk {
    data class Stdout(val text: String) : LinuxChunk

    data class Stderr(val text: String) : LinuxChunk

    data class Exit(val exitCode: Int) : LinuxChunk
}

enum class PrivilegeLevel {
    NORMAL,
    ADB,
    ROOT,
}

data class PrivilegeState(
    val selected: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val effective: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val rootGranted: Boolean = false,
    val adbConnected: Boolean = false,
) {
    val availableBackends: Set<PrivilegeLevel>
        get() = buildSet {
            if (adbConnected) add(PrivilegeLevel.ADB)
            if (rootGranted) add(PrivilegeLevel.ROOT)
        }

    val virtualScreenAvailable: Boolean
        get() = selected != PrivilegeLevel.NORMAL && availableBackends.isNotEmpty()
}

enum class ScreenTarget {
    VIRTUAL,
    MAIN,
}

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left

    val height: Int get() = bottom - top
}

data class ScreenNode(
    val ref: String,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: ScreenBounds,
    val clickable: Boolean = false,
    val children: List<ScreenNode> = emptyList(),
)

data class ScreenObservation(
    val packageName: String? = null,
    val windowTitle: String? = null,
    val nodes: List<ScreenNode> = emptyList(),
    val capturedAt: Long = 0,
)

data class ScreenScreenshot(
    val path: String,
    val width: Int,
    val height: Int,
    val capturedAt: Long = 0,
)

data class ScreenApp(
    val packageName: String,
    val label: String,
)

data class ScreenCapability(
    val available: Boolean = false,
    val accessibility: Boolean = false,
    val screenshot: Boolean = false,
    val input: Boolean = false,
)

data class ScreenStatus(
    val virtual: ScreenCapability = ScreenCapability(),
    val main: ScreenCapability = ScreenCapability(),
)

enum class NotificationChannel {
    DEFAULT,
    PROGRESS,
    ALERT,
}

data class PluginNotification(
    val id: String,
    val title: String,
    val body: String,
    val channel: NotificationChannel = NotificationChannel.DEFAULT,
    val progress: Int? = null,
    val ongoing: Boolean = false,
)

data class NetworkResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val contentType: String? = null,
    val finalUrl: String? = null,
)

data class DownloadResult(
    val path: String,
    val size: Long,
    val sha256: String,
)

data class FileEntry(
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long = 0,
    val modifiedAt: Long = 0,
)

data class SessionInfo(
    val id: String,
    val title: String? = null,
    val cwd: String? = null,
    val busy: Boolean = false,
)

enum class JobStatus {
    RUNNING,
    DONE,
    FAILED,
    KILLED,
    INTERRUPTED,
}

data class JobInfo(
    val id: String,
    val label: String,
    val command: String,
    val status: JobStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val exitCode: Int? = null,
    val outputBytes: Long = 0,
)

data class JobRead(
    val output: String,
    val status: JobStatus,
    val exitCode: Int? = null,
    val dropped: Boolean = false,
)

sealed interface TimerTrigger {
    data class Once(val epochMillis: Long) : TimerTrigger

    data class Cron(val expression: String) : TimerTrigger
}

data class TimerRequest(
    val name: String,
    val prompt: String,
    val trigger: TimerTrigger,
    val sessionId: String? = null,
    val enabled: Boolean = true,
)

data class TimerInfo(
    val id: String,
    val name: String,
    val prompt: String,
    val trigger: TimerTrigger,
    val sessionId: String? = null,
    val enabled: Boolean = true,
    val nextRunAt: Long? = null,
    val lastError: String? = null,
)

enum class GoalPhase {
    ACTIVE,
    PAUSED,
    BLOCKED,
    COMPLETE,
}

data class GoalInfo(
    val id: String,
    val sessionId: String,
    val objective: String,
    val phase: GoalPhase,
    val roundsStarted: Int,
    val maxRounds: Int,
    val armed: Boolean = true,
    val blockedReason: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class MiniappApp(
    val id: String,
    val name: String,
    val description: String? = null,
    val updatedAt: Long = 0,
)

data class MiniappInvalid(
    val id: String,
    val reason: String,
)

data class MiniappScan(
    val apps: List<MiniappApp> = emptyList(),
    val invalid: List<MiniappInvalid> = emptyList(),
)

sealed interface MiniappEntry {
    data class Valid(
        val app: MiniappApp,
        val manifest: JsonObject = JsonObject(emptyMap()),
    ) : MiniappEntry

    data class Invalid(
        val id: String,
        val reason: String,
    ) : MiniappEntry

    data object Missing : MiniappEntry
}

data class MiniappCreateRequest(
    val name: String,
    val description: String,
    val prompt: String = "",
)

data class MiniappRun(
    val appId: String,
    val accepted: Boolean,
    val error: String? = null,
)

enum class McpServerStatus {
    OK,
    AUTH_REQUIRED,
    FAILED,
    UNKNOWN,
}

data class McpServer(
    val name: String,
    val url: String? = null,
    val enabled: Boolean = true,
    val status: McpServerStatus = McpServerStatus.UNKNOWN,
    val toolCount: Int = 0,
)

data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject = JsonObject(emptyMap()),
)

data class McpProbe(
    val status: McpServerStatus,
    val toolCount: Int = 0,
    val serverName: String? = null,
    val error: String? = null,
)

data class McpCallResult(
    val blocks: List<ToolBlock> = emptyList(),
    val isError: Boolean = false,
)

data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val pluginApiLevel: Int = PLUGIN_API_LEVEL,
    val uiApiLevel: Int = HOST_UI_API_LEVEL,
    val language: String = "zh",
    val androidSdkInt: Int = 0,
    val deviceModel: String = "",
    val installationId: String = "",
)

data class ShareRequest(
    val text: String? = null,
    val filePath: String? = null,
    val mimeType: String? = null,
)

enum class PluginLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class PluginBusEvent(
    val topic: String,
    val publisher: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val publishedAt: Long = 0,
)
