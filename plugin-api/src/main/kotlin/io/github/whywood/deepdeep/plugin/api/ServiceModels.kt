package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

public data class LinuxResult(
    public val exitCode: Int,
    public val stdout: String,
    public val stderr: String,
    public val timedOut: Boolean = false,
    public val truncated: Boolean = false,
    public val fullOutputPath: String? = null,
)

@JvmInline
public value class LinuxChunkKind private constructor(public val wire: String) {
    public companion object {
        public val STDOUT: LinuxChunkKind = LinuxChunkKind("stdout")
        public val STDERR: LinuxChunkKind = LinuxChunkKind("stderr")
        public val EXIT: LinuxChunkKind = LinuxChunkKind("exit")
        public val ALL: List<LinuxChunkKind> = listOf(STDOUT, STDERR, EXIT)

        public fun fromWire(value: String): LinuxChunkKind? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface LinuxChunk {
    public val kind: LinuxChunkKind
    public val text: String?
    public val exitCode: Int?
}

public enum class ScreenTarget {
    VIRTUAL,
    MAIN,
}

public data class ScreenBounds(
    public val left: Int,
    public val top: Int,
    public val right: Int,
    public val bottom: Int,
) {
    public val width: Int get() = right - left

    public val height: Int get() = bottom - top
}

public data class ScreenNode(
    public val ref: String,
    public val className: String? = null,
    public val text: String? = null,
    public val contentDescription: String? = null,
    public val bounds: ScreenBounds,
    public val clickable: Boolean = false,
    public val children: List<ScreenNode> = emptyList(),
)

public data class ScreenObservation(
    public val packageName: String? = null,
    public val windowTitle: String? = null,
    public val nodes: List<ScreenNode> = emptyList(),
    public val capturedAt: Long = 0,
)

public data class ScreenScreenshot(
    public val path: String,
    public val width: Int,
    public val height: Int,
    public val capturedAt: Long = 0,
)

public data class ScreenApp(
    public val packageName: String,
    public val label: String,
)

public data class ScreenCapability(
    public val available: Boolean = false,
    public val accessibility: Boolean = false,
    public val screenshot: Boolean = false,
    public val input: Boolean = false,
)

public data class ScreenStatus(
    public val virtual: ScreenCapability = ScreenCapability(),
    public val main: ScreenCapability = ScreenCapability(),
)

public enum class NotificationChannel {
    DEFAULT,
    PROGRESS,
    ALERT,
}

public interface PluginNotification {
    public val id: String
    public val title: String
    public val body: String
    public val channel: NotificationChannel
    public val progress: Int?
    public val ongoing: Boolean
}

public fun pluginNotification(
    id: String,
    title: String,
    body: String,
    channel: NotificationChannel = NotificationChannel.DEFAULT,
    progress: Int? = null,
    ongoing: Boolean = false,
): PluginNotification = PluginNotificationImpl(id, title, body, channel, progress, ongoing)

internal data class PluginNotificationImpl(
    override val id: String,
    override val title: String,
    override val body: String,
    override val channel: NotificationChannel,
    override val progress: Int?,
    override val ongoing: Boolean,
) : PluginNotification

public data class NetworkResponse(
    public val statusCode: Int,
    public val body: String,
    public val headers: Map<String, String> = emptyMap(),
    public val contentType: String? = null,
    public val finalUrl: String? = null,
)

public data class DownloadResult(
    public val path: String,
    public val size: Long,
    public val sha256: String,
)

public data class FileEntry(
    public val path: String,
    public val name: String,
    public val directory: Boolean,
    public val size: Long = 0,
    public val modifiedAt: Long = 0,
)

public data class SessionInfo(
    public val id: String,
    public val title: String? = null,
    public val cwd: String? = null,
    public val busy: Boolean = false,
)

@JvmInline
public value class JobStatus private constructor(public val wire: String) {
    public companion object {
        public val RUNNING: JobStatus = JobStatus("running")
        public val DONE: JobStatus = JobStatus("done")
        public val FAILED: JobStatus = JobStatus("failed")
        public val KILLED: JobStatus = JobStatus("killed")
        public val INTERRUPTED: JobStatus = JobStatus("interrupted")
        public val ALL: List<JobStatus> = listOf(RUNNING, DONE, FAILED, KILLED, INTERRUPTED)

        public fun fromWire(value: String): JobStatus? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public data class JobInfo(
    public val id: String,
    public val label: String,
    public val command: String,
    public val status: JobStatus,
    public val startedAt: Long,
    public val finishedAt: Long? = null,
    public val exitCode: Int? = null,
    public val outputBytes: Long = 0,
)

public data class JobRead(
    public val output: String,
    public val status: JobStatus,
    public val exitCode: Int? = null,
    public val dropped: Boolean = false,
)

@JvmInline
public value class TimerTriggerKind private constructor(public val wire: String) {
    public companion object {
        public val ONCE: TimerTriggerKind = TimerTriggerKind("once")
        public val CRON: TimerTriggerKind = TimerTriggerKind("cron")
        public val ALL: List<TimerTriggerKind> = listOf(ONCE, CRON)

        public fun fromWire(value: String): TimerTriggerKind? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface TimerTrigger {
    public val kind: TimerTriggerKind
    public val epochMillis: Long?
    public val expression: String?
}

public fun timerOnce(epochMillis: Long): TimerTrigger =
    TimerTriggerImpl(TimerTriggerKind.ONCE, epochMillis, expression = null)

public fun timerCron(expression: String): TimerTrigger =
    TimerTriggerImpl(TimerTriggerKind.CRON, epochMillis = null, expression = expression)

internal data class TimerTriggerImpl(
    override val kind: TimerTriggerKind,
    override val epochMillis: Long?,
    override val expression: String?,
) : TimerTrigger

public interface TimerRequest {
    public val name: String
    public val prompt: String
    public val trigger: TimerTrigger
    public val sessionId: String?
    public val enabled: Boolean
}

public fun timerRequest(
    name: String,
    prompt: String,
    trigger: TimerTrigger,
    sessionId: String? = null,
    enabled: Boolean = true,
): TimerRequest = TimerRequestImpl(name, prompt, trigger, sessionId, enabled)

internal data class TimerRequestImpl(
    override val name: String,
    override val prompt: String,
    override val trigger: TimerTrigger,
    override val sessionId: String?,
    override val enabled: Boolean,
) : TimerRequest

public data class TimerInfo(
    public val id: String,
    public val name: String,
    public val prompt: String,
    public val trigger: TimerTrigger,
    public val sessionId: String? = null,
    public val enabled: Boolean = true,
    public val nextRunAt: Long? = null,
    public val lastError: String? = null,
)

@JvmInline
public value class GoalPhase private constructor(public val wire: String) {
    public companion object {
        public val ACTIVE: GoalPhase = GoalPhase("active")
        public val PAUSED: GoalPhase = GoalPhase("paused")
        public val BLOCKED: GoalPhase = GoalPhase("blocked")
        public val COMPLETE: GoalPhase = GoalPhase("complete")
        public val ALL: List<GoalPhase> = listOf(ACTIVE, PAUSED, BLOCKED, COMPLETE)

        public fun fromWire(value: String): GoalPhase? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public data class GoalInfo(
    public val id: String,
    public val sessionId: String,
    public val objective: String,
    public val phase: GoalPhase,
    public val roundsStarted: Int,
    public val maxRounds: Int,
    public val armed: Boolean = true,
    public val blockedReason: String? = null,
    public val createdAt: Long = 0,
    public val updatedAt: Long = 0,
)

public data class MiniappApp(
    public val id: String,
    public val name: String,
    public val description: String? = null,
    public val updatedAt: Long = 0,
)

public data class MiniappInvalid(
    public val id: String,
    public val reason: String,
)

public data class MiniappScan(
    public val apps: List<MiniappApp> = emptyList(),
    public val invalid: List<MiniappInvalid> = emptyList(),
)

@JvmInline
public value class MiniappEntryKind private constructor(public val wire: String) {
    public companion object {
        public val VALID: MiniappEntryKind = MiniappEntryKind("valid")
        public val INVALID: MiniappEntryKind = MiniappEntryKind("invalid")
        public val MISSING: MiniappEntryKind = MiniappEntryKind("missing")
        public val ALL: List<MiniappEntryKind> = listOf(VALID, INVALID, MISSING)

        public fun fromWire(value: String): MiniappEntryKind? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface MiniappEntry {
    public val kind: MiniappEntryKind
    public val app: MiniappApp?
    public val manifest: JsonObject?
    public val id: String?
    public val reason: String?
}

public fun miniappValid(app: MiniappApp, manifest: JsonObject = JsonObject(emptyMap())): MiniappEntry =
    MiniappEntryImpl(MiniappEntryKind.VALID, app, manifest, id = null, reason = null)

public fun miniappInvalid(id: String, reason: String): MiniappEntry =
    MiniappEntryImpl(MiniappEntryKind.INVALID, app = null, manifest = null, id = id, reason = reason)

public fun miniappMissing(): MiniappEntry =
    MiniappEntryImpl(MiniappEntryKind.MISSING, app = null, manifest = null, id = null, reason = null)

internal data class MiniappEntryImpl(
    override val kind: MiniappEntryKind,
    override val app: MiniappApp?,
    override val manifest: JsonObject?,
    override val id: String?,
    override val reason: String?,
) : MiniappEntry

public interface MiniappCreateRequest {
    public val name: String
    public val description: String
    public val prompt: String
}

public fun miniappCreateRequest(
    name: String,
    description: String,
    prompt: String = "",
): MiniappCreateRequest = MiniappCreateRequestImpl(name, description, prompt)

internal data class MiniappCreateRequestImpl(
    override val name: String,
    override val description: String,
    override val prompt: String,
) : MiniappCreateRequest

public data class MiniappRun(
    public val appId: String,
    public val accepted: Boolean,
    public val error: String? = null,
)

@JvmInline
public value class McpServerStatus private constructor(public val wire: String) {
    public companion object {
        public val OK: McpServerStatus = McpServerStatus("ok")
        public val AUTH_REQUIRED: McpServerStatus = McpServerStatus("auth-required")
        public val FAILED: McpServerStatus = McpServerStatus("failed")
        public val UNKNOWN: McpServerStatus = McpServerStatus("unknown")
        public val ALL: List<McpServerStatus> = listOf(OK, AUTH_REQUIRED, FAILED, UNKNOWN)

        public fun fromWire(value: String): McpServerStatus? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public data class McpServer(
    public val name: String,
    public val url: String? = null,
    public val enabled: Boolean = true,
    public val status: McpServerStatus = McpServerStatus.UNKNOWN,
    public val toolCount: Int = 0,
)

public data class McpTool(
    public val name: String,
    public val description: String? = null,
    public val inputSchema: JsonObject = JsonObject(emptyMap()),
)

public data class McpProbe(
    public val status: McpServerStatus,
    public val toolCount: Int = 0,
    public val serverName: String? = null,
    public val error: String? = null,
)

public data class McpCallResult(
    public val blocks: List<ToolBlock> = emptyList(),
    public val isError: Boolean = false,
)

public data class AppInfo(
    public val versionName: String,
    public val versionCode: Long,
    public val pluginApiLevel: Int = PLUGIN_API_LEVEL,
    public val uiApiLevel: Int = HOST_UI_API_LEVEL,
    public val language: String = "zh",
    public val androidSdkInt: Int = 0,
    public val deviceModel: String = "",
    public val installationId: String = "",
)

public interface ShareRequest {
    public val text: String?
    public val filePath: String?
    public val mimeType: String?
}

public fun shareRequest(
    text: String? = null,
    filePath: String? = null,
    mimeType: String? = null,
): ShareRequest = ShareRequestImpl(text, filePath, mimeType)

internal data class ShareRequestImpl(
    override val text: String?,
    override val filePath: String?,
    override val mimeType: String?,
) : ShareRequest

public enum class PluginLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

public interface PluginBusEvent {
    public val topic: String
    public val publisher: String
    public val payload: JsonObject
    public val publishedAt: Long
}

public fun pluginBusEvent(
    topic: String,
    payload: JsonObject = JsonObject(emptyMap()),
    publishedAt: Long = 0,
): PluginBusEvent = PluginBusEventImpl(topic, publisher = "", payload, publishedAt)

internal data class PluginBusEventImpl(
    override val topic: String,
    override val publisher: String,
    override val payload: JsonObject,
    override val publishedAt: Long,
) : PluginBusEvent
