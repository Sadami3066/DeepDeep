package io.github.whywood.deepdeep.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

public class PluginServiceUnavailable(
    public val service: String,
    public val reason: String? = null,
) : IllegalStateException(
    if (reason == null) "plugin service is not available: $service" else "plugin service is not available: $service ($reason)",
)

public interface LinuxService {
    public val available: Boolean

    public suspend fun exec(command: String, cwd: String? = null, timeoutMs: Long = DEFAULT_TIMEOUT_MS): LinuxResult

    public fun stream(command: String, cwd: String? = null): Flow<LinuxChunk>

    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 15_000
    }
}

public interface ScreenService {
    public val status: ScreenStatus

    public suspend fun startVirtual(): ScreenStatus

    public suspend fun stopVirtual()

    public suspend fun observe(target: ScreenTarget = ScreenTarget.VIRTUAL, packageName: String? = null): ScreenObservation

    public suspend fun screenshot(target: ScreenTarget = ScreenTarget.VIRTUAL): ScreenScreenshot

    public suspend fun apps(): List<ScreenApp>

    public suspend fun launch(packageName: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    public suspend fun tap(x: Int, y: Int, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    public suspend fun tapNode(ref: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    public suspend fun swipe(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Int = 300,
        target: ScreenTarget = ScreenTarget.VIRTUAL,
    ): Boolean

    public suspend fun scroll(deltaY: Int, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    public suspend fun type(text: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    public suspend fun key(keyCode: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    public suspend fun waitFor(
        text: String,
        timeoutMs: Long = DEFAULT_WAIT_TIMEOUT_MS,
        target: ScreenTarget = ScreenTarget.VIRTUAL,
    ): Boolean

    public companion object {
        public const val DEFAULT_WAIT_TIMEOUT_MS: Long = 5_000
    }
}

public interface NotificationService {
    public val enabled: Boolean

    public fun notify(notification: PluginNotification)

    public fun notifyChecked(notification: PluginNotification): PluginNotificationHandle?

    public fun updateProgress(id: String, progress: Int?, title: String? = null)

    public fun cancel(id: String)

    public val listenerGranted: Boolean

    public suspend fun recent(limit: Int = 50): List<PluginNotificationRecord>

    public fun received(): Flow<PluginNotificationRecord>

    public fun removed(): Flow<String>

    public suspend fun dismiss(key: String): Boolean
}

public interface SecretService {
    public suspend fun get(key: String): String?

    public suspend fun put(key: String, value: String)

    public suspend fun remove(key: String)

    public suspend fun clear()
}

public interface NetworkService {
    public suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): NetworkResponse

    public suspend fun download(
        url: String,
        targetPath: String,
        expectedSha256: String? = null,
        onProgress: ((Int?) -> Unit)? = null,
    ): DownloadResult

    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 30_000
    }
}

public suspend fun NetworkService.get(
    url: String,
    headers: Map<String, String> = emptyMap(),
): NetworkResponse = request("GET", url, headers)

public suspend fun NetworkService.post(
    url: String,
    body: String,
    headers: Map<String, String> = emptyMap(),
): NetworkResponse = request("POST", url, headers, body)

public interface FileService {
    public val rootDir: String

    public val workspaceDir: String?

    public suspend fun list(path: String = ""): List<FileEntry>

    public suspend fun readText(path: String): String

    public suspend fun writeText(path: String, content: String, append: Boolean = false)

    public suspend fun readBytes(path: String): ByteArray

    public suspend fun writeBytes(path: String, bytes: ByteArray)

    public suspend fun delete(path: String): Boolean

    public suspend fun exists(path: String): Boolean

    public suspend fun resolve(path: String): String
}

public data class PluginMessage(
    public val id: String,
    public val role: MessageRole,
    public val text: String,
    public val timestampMs: Long,
    public val toolName: String? = null,
    public val images: List<String> = emptyList(),
)

public interface SessionService {
    public val active: SessionInfo?

    public fun observe(): Flow<SessionInfo?>

    public suspend fun list(): List<SessionInfo>

    public suspend fun prompt(text: String, sessionId: String? = null): Boolean

    public suspend fun history(sessionId: String, limit: Int = 50): List<PluginMessage>

    public suspend fun stop(sessionId: String): Boolean

    public suspend fun create(title: String? = null): SessionInfo?

    public suspend fun rename(sessionId: String, title: String): Boolean

    public suspend fun delete(sessionId: String): Boolean

    public suspend fun open(sessionId: String): Boolean

    public suspend fun archive(sessionId: String, archived: Boolean): Boolean
}

public interface JobService {
    public fun observe(): Flow<List<JobInfo>>

    public suspend fun start(command: String, label: String, cwd: String? = null, sessionId: String? = null): String?

    public suspend fun read(jobId: String): JobRead

    public suspend fun kill(jobId: String): Boolean
}

public interface TimerService {
    public suspend fun schedule(request: TimerRequest): TimerInfo?

    public suspend fun list(): List<TimerInfo>

    public suspend fun cancel(id: String): Boolean
}

public interface GoalService {
    public fun observe(): Flow<List<GoalInfo>>

    public suspend fun get(sessionId: String): GoalInfo?

    public suspend fun create(sessionId: String, objective: String, maxRounds: Int? = null): GoalInfo?

    public suspend fun edit(sessionId: String, objective: String): GoalInfo?

    public suspend fun pause(sessionId: String): GoalInfo?

    public suspend fun resume(sessionId: String): GoalInfo?

    public suspend fun complete(sessionId: String): GoalInfo?

    public suspend fun clear(sessionId: String)
}

public interface MiniappService {
    public suspend fun scan(): MiniappScan

    public suspend fun load(id: String): MiniappEntry

    public suspend fun create(request: MiniappCreateRequest): MiniappApp?

    public suspend fun edit(id: String, instruction: String): MiniappRun

    public suspend fun delete(id: String): Boolean

    public suspend fun run(id: String, instruction: String): MiniappRun
}

public interface McpService {
    public suspend fun servers(): List<McpServer>

    public suspend fun probe(name: String): McpProbe

    public suspend fun tools(server: String): List<McpTool>

    public suspend fun call(server: String, tool: String, arguments: JsonObject = JsonObject(emptyMap())): McpCallResult
}

public interface UiService {
    public fun openUrl(url: String)

    public fun share(request: ShareRequest)

    public fun shareFiles(paths: List<String>, mimeType: String? = null)

    public fun toast(message: String)

    public fun openPage(pageId: String)

    public fun openPageWithArguments(pageId: String, arguments: JsonObject, newTask: Boolean = false)

    public fun openPageSheet(pageId: String, arguments: JsonObject = JsonObject(emptyMap())) {
        openPageWithArguments(pageId, arguments)
    }

    public fun closePage(result: JsonObject? = null)

    public fun pageContext(): PluginPageContext?

    public val isDark: Boolean

    public suspend fun confirm(request: UiConfirmRequest): Boolean

    public suspend fun prompt(request: UiPromptRequest): String?

    public suspend fun choose(request: UiChooseRequest): String?

    public suspend fun pickDate(request: UiDatePickRequest = uiDatePickRequest()): Long?

    public suspend fun pickTime(request: UiTimePickRequest = uiTimePickRequest()): Int?

    public suspend fun pickFiles(request: UiFilePickRequest = uiFilePickRequest()): List<PluginPickedFile>

    public suspend fun pickImage(request: UiImagePickRequest = uiImagePickRequest()): PluginPickedFile?

    public suspend fun scanQr(): String?

    public suspend fun pickDirectory(): String?

    public fun snackbar(message: String, actionLabel: String? = null, action: (() -> Unit)? = null)
}

public interface AppInfoService {
    public val info: AppInfo
}

public interface LogService {
    public fun log(level: PluginLogLevel, message: String, error: Throwable? = null)

    public fun debug(message: String): Unit = log(PluginLogLevel.DEBUG, message)

    public fun info(message: String): Unit = log(PluginLogLevel.INFO, message)

    public fun warn(message: String, error: Throwable? = null): Unit = log(PluginLogLevel.WARN, message, error)

    public fun error(message: String, error: Throwable? = null): Unit = log(PluginLogLevel.ERROR, message, error)
}

public interface PluginBus {
    public fun publish(event: PluginBusEvent)

    public fun subscribe(topic: String? = null): Flow<PluginBusEvent>
}

public enum class ChatDelivery {
    STEER,
    FOLLOW_UP,
}

public interface ChatDisplay {
    public val customType: String
    public val payload: JsonObject
    public val text: String
}

public fun chatDisplay(
    customType: String,
    payload: JsonObject = JsonObject(emptyMap()),
    text: String = "",
): ChatDisplay = ChatDisplayImpl(customType, payload, text)

internal data class ChatDisplayImpl(
    override val customType: String,
    override val payload: JsonObject,
    override val text: String,
) : ChatDisplay

public interface ChatMessageOptions {
    public val triggerTurn: Boolean
    public val deliverAs: ChatDelivery
    public val display: ChatDisplay?
}

public fun chatMessageOptions(
    triggerTurn: Boolean = true,
    deliverAs: ChatDelivery = ChatDelivery.STEER,
    display: ChatDisplay? = null,
): ChatMessageOptions = ChatMessageOptionsImpl(triggerTurn, deliverAs, display)

internal data class ChatMessageOptionsImpl(
    override val triggerTurn: Boolean,
    override val deliverAs: ChatDelivery,
    override val display: ChatDisplay?,
) : ChatMessageOptions

@JvmInline
public value class ChatSendKind private constructor(public val wire: String) {
    public companion object {
        public val STARTED: ChatSendKind = ChatSendKind("started")
        public val QUEUED: ChatSendKind = ChatSendKind("queued")
        public val DISPLAYED: ChatSendKind = ChatSendKind("displayed")
        public val ALL: List<ChatSendKind> = listOf(STARTED, QUEUED, DISPLAYED)

        public fun fromWire(value: String): ChatSendKind? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface ChatSendResult {
    public val kind: ChatSendKind
    public val sessionId: String
    public val runId: String?
    public val delivery: ChatDelivery?
}

public interface ChatAttachment {
    public val path: String
    public val mimeType: String?
    public val name: String?
}

public fun chatAttachment(
    path: String,
    mimeType: String? = null,
    name: String? = null,
): ChatAttachment = ChatAttachmentImpl(path, mimeType, name)

internal data class ChatAttachmentImpl(
    override val path: String,
    override val mimeType: String?,
    override val name: String?,
) : ChatAttachment

public interface ChatService {
    public suspend fun postCustomMessage(
        sessionId: String,
        customType: String,
        payload: JsonObject,
        text: String = "",
        display: Boolean = true,
    ): Boolean

    public suspend fun sendMessage(
        sessionId: String,
        text: String,
        options: ChatMessageOptions = chatMessageOptions(),
    ): ChatSendResult

    public suspend fun sendMessageWithAttachments(
        sessionId: String,
        text: String,
        attachments: List<ChatAttachment>,
        options: ChatMessageOptions = chatMessageOptions(),
    ): ChatSendResult
}
