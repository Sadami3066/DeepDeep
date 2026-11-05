package io.github.whywood.deepdeep.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

class PluginServiceUnavailable(val service: String) : IllegalStateException("plugin service is not available: $service")

interface LinuxService {
    val available: Boolean

    suspend fun exec(command: String, cwd: String? = null, timeoutMs: Long = DEFAULT_TIMEOUT_MS): LinuxResult

    fun stream(command: String, cwd: String? = null): Flow<LinuxChunk>

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 15_000
    }
}

interface PrivilegeService {
    val state: PrivilegeState

    fun observe(): Flow<PrivilegeState>

    suspend fun shell(command: String, timeoutMs: Long = LinuxService.DEFAULT_TIMEOUT_MS): LinuxResult
}

interface ScreenService {
    val status: ScreenStatus

    suspend fun startVirtual(): ScreenStatus

    suspend fun stopVirtual()

    suspend fun observe(target: ScreenTarget = ScreenTarget.VIRTUAL, packageName: String? = null): ScreenObservation

    suspend fun screenshot(target: ScreenTarget = ScreenTarget.VIRTUAL): ScreenScreenshot

    suspend fun apps(): List<ScreenApp>

    suspend fun launch(packageName: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    suspend fun tap(x: Int, y: Int, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    suspend fun tapNode(ref: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    suspend fun swipe(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Int = 300,
        target: ScreenTarget = ScreenTarget.VIRTUAL,
    ): Boolean

    suspend fun scroll(deltaY: Int, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    suspend fun type(text: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    suspend fun key(keyCode: String, target: ScreenTarget = ScreenTarget.VIRTUAL): Boolean

    suspend fun waitFor(
        text: String,
        timeoutMs: Long = DEFAULT_WAIT_TIMEOUT_MS,
        target: ScreenTarget = ScreenTarget.VIRTUAL,
    ): Boolean

    companion object {
        const val DEFAULT_WAIT_TIMEOUT_MS: Long = 5_000
    }
}

interface NotificationService {
    val enabled: Boolean

    fun notify(notification: PluginNotification)

    fun updateProgress(id: String, progress: Int?, title: String? = null)

    fun cancel(id: String)
}

interface SecretService {
    suspend fun get(key: String): String?

    suspend fun put(key: String, value: String)

    suspend fun remove(key: String)

    suspend fun clear()
}

interface NetworkService {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): NetworkResponse

    suspend fun download(
        url: String,
        targetPath: String,
        expectedSha256: String? = null,
        onProgress: ((Int?) -> Unit)? = null,
    ): DownloadResult

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000
    }
}

suspend fun NetworkService.get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse =
    request("GET", url, headers)

suspend fun NetworkService.post(
    url: String,
    body: String,
    headers: Map<String, String> = emptyMap(),
): NetworkResponse = request("POST", url, headers, body)

interface FileService {
    val rootDir: String

    val workspaceDir: String?

    suspend fun list(path: String = ""): List<FileEntry>

    suspend fun readText(path: String): String

    suspend fun writeText(path: String, content: String, append: Boolean = false)

    suspend fun readBytes(path: String): ByteArray

    suspend fun writeBytes(path: String, bytes: ByteArray)

    suspend fun delete(path: String): Boolean

    suspend fun exists(path: String): Boolean

    suspend fun resolve(path: String): String
}

interface SessionService {
    val active: SessionInfo?

    fun observe(): Flow<SessionInfo?>

    suspend fun list(): List<SessionInfo>

    suspend fun prompt(text: String, sessionId: String? = null): Boolean
}

interface JobService {
    fun observe(): Flow<List<JobInfo>>

    suspend fun start(command: String, label: String, cwd: String? = null, sessionId: String? = null): String?

    suspend fun read(jobId: String): JobRead

    suspend fun kill(jobId: String): Boolean
}

interface TimerService {
    suspend fun schedule(request: TimerRequest): TimerInfo?

    suspend fun list(): List<TimerInfo>

    suspend fun cancel(id: String): Boolean
}

interface GoalService {
    fun observe(): Flow<List<GoalInfo>>

    suspend fun get(sessionId: String): GoalInfo?

    suspend fun create(sessionId: String, objective: String, maxRounds: Int? = null): GoalInfo?

    suspend fun edit(sessionId: String, objective: String): GoalInfo?

    suspend fun pause(sessionId: String): GoalInfo?

    suspend fun resume(sessionId: String): GoalInfo?

    suspend fun complete(sessionId: String): GoalInfo?

    suspend fun clear(sessionId: String)
}

interface WorkshopService {
    suspend fun scan(): WorkshopScan

    suspend fun load(id: String): WorkshopEntry

    suspend fun create(request: WorkshopCreateRequest): WorkshopApp?

    suspend fun edit(id: String, instruction: String): WorkshopRun

    suspend fun delete(id: String): Boolean

    suspend fun run(id: String, instruction: String): WorkshopRun
}

interface McpService {
    suspend fun servers(): List<McpServer>

    suspend fun probe(name: String): McpProbe

    suspend fun tools(server: String): List<McpTool>

    suspend fun call(server: String, tool: String, arguments: JsonObject = JsonObject(emptyMap())): McpCallResult
}

interface UiService {
    fun openUrl(url: String)

    fun share(request: ShareRequest)

    fun toast(message: String)

    fun openPage(pageId: String)
}

interface AppInfoService {
    val info: AppInfo
}

interface LogService {
    fun log(level: PluginLogLevel, message: String, error: Throwable? = null)

    fun debug(message: String) = log(PluginLogLevel.DEBUG, message)

    fun info(message: String) = log(PluginLogLevel.INFO, message)

    fun warn(message: String, error: Throwable? = null) = log(PluginLogLevel.WARN, message, error)

    fun error(message: String, error: Throwable? = null) = log(PluginLogLevel.ERROR, message, error)
}

interface PluginBus {
    fun publish(event: PluginBusEvent)

    fun subscribe(topic: String? = null): Flow<PluginBusEvent>
}

interface ChatService {
    suspend fun postCustomMessage(
        sessionId: String,
        customType: String,
        payload: JsonObject,
        text: String = "",
        display: Boolean = true,
    ): Boolean
}
