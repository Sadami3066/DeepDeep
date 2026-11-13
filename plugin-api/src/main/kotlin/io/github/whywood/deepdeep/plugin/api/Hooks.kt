package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

public interface PluginEvent

public interface SessionStartedEvent : PluginEvent {
    public val sessionId: String
    public val title: String?
    public val workspace: String?
}

public interface SessionClosedEvent : PluginEvent {
    public val sessionId: String
}

public interface RunStartedEvent : PluginEvent {
    public val sessionId: String
    public val runId: String
}

public interface RunFinishedEvent : PluginEvent {
    public val sessionId: String
    public val runId: String
    public val error: String?

    public val succeeded: Boolean
        get() = error == null
}

public interface TurnStartedEvent : PluginEvent {
    public val sessionId: String
    public val turnId: String
}

public interface TurnFinishedEvent : PluginEvent {
    public val sessionId: String
    public val turnId: String
    public val failed: Boolean
}

@JvmInline
public value class MessageRole private constructor(public val wire: String) {
    public companion object {
        public val USER: MessageRole = MessageRole("user")
        public val ASSISTANT: MessageRole = MessageRole("assistant")
        public val TOOL: MessageRole = MessageRole("tool")
        public val SYSTEM: MessageRole = MessageRole("system")
        public val ALL: List<MessageRole> = listOf(USER, ASSISTANT, TOOL, SYSTEM)

        public fun fromWire(value: String): MessageRole? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface MessageStartedEvent : PluginEvent {
    public val sessionId: String
    public val messageId: String
    public val role: MessageRole
}

public interface MessageDeltaEvent : PluginEvent {
    public val sessionId: String
    public val messageId: String
    public val delta: String
}

public interface MessageFinishedEvent : PluginEvent {
    public val sessionId: String
    public val messageId: String
    public val text: String?
}

@JvmInline
public value class ToolPhase private constructor(public val wire: String) {
    public companion object {
        public val STARTED: ToolPhase = ToolPhase("started")
        public val PARTIAL: ToolPhase = ToolPhase("partial")
        public val FINISHED: ToolPhase = ToolPhase("finished")
        public val FAILED: ToolPhase = ToolPhase("failed")
        public val ALL: List<ToolPhase> = listOf(STARTED, PARTIAL, FINISHED, FAILED)

        public fun fromWire(value: String): ToolPhase? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface ToolExecutionUpdateEvent : PluginEvent {
    public val sessionId: String
    public val callId: String
    public val toolName: String
    public val phase: ToolPhase
    public val partial: PluginToolResult?
}

public interface CompactionStartedEvent : PluginEvent {
    public val sessionId: String
}

public interface CompactionFinishedEvent : PluginEvent {
    public val sessionId: String
    public val tokensBefore: Long
    public val tokensAfter: Long
}

public interface CompactionFailedEvent : PluginEvent {
    public val sessionId: String
    public val error: String
}

public interface ModelChangedEvent : PluginEvent {
    public val sessionId: String
    public val model: String
}

public interface ThinkingLevelChangedEvent : PluginEvent {
    public val sessionId: String
    public val level: String
}

public interface RetryScheduledEvent : PluginEvent {
    public val sessionId: String
    public val attempt: Int
    public val delayMs: Long
    public val reason: String?
}

public interface RetrySucceededEvent : PluginEvent {
    public val sessionId: String
    public val attempt: Int
}

public interface RetryExhaustedEvent : PluginEvent {
    public val sessionId: String
    public val attempts: Int
    public val error: String
}

public interface McpStatusChangedEvent : PluginEvent {
    public val server: String
    public val status: McpServerStatus
}

public interface McpToolsChangedEvent : PluginEvent {
    public val server: String
    public val tools: List<String>
}

public interface BusEventReceived : PluginEvent {
    public val event: PluginBusEvent
}


public interface NotificationReceivedEvent : PluginEvent {
    public val notification: PluginNotificationRecord
    public val removed: Boolean
}

public interface NotificationActionEvent : PluginEvent {
    public val notificationId: String
    public val actionId: String
    public val payload: JsonObject
}

public interface BackgroundTaskEvent : PluginEvent {
    public val taskId: String
    public val kind: PluginBackgroundKind
    public val firedAtMs: Long
}

public interface SharedStream {
    public val path: String
    public val mimeType: String?
    public val name: String?
}

public interface ShareReceivedEvent : PluginEvent {
    public val text: String?
    public val streams: List<SharedStream>
    public val title: String?
}

public interface DeepLinkReceivedEvent : PluginEvent {
    public val path: String
    public val arguments: JsonObject
}

public interface AppLifecycleEvent : PluginEvent {
    public val foreground: Boolean
}

public data class PromptRequest(
    public val sessionId: String,
    public val prompt: String,
)

public data class PluginContextRequest(
    public val sessionId: String,
    public val cwd: String,
    public val providerId: String? = null,
    public val modelId: String? = null,
)

public data class InputRequest(
    public val sessionId: String,
    public val text: String,
    public val imagePaths: List<String> = emptyList(),
)

public interface InputDecision {
    public val text: String?
    public val imagePaths: List<String>
    public val handled: Boolean
}

public fun inputContinue(text: String, imagePaths: List<String> = emptyList()): InputDecision =
    InputDecisionImpl(text = text, imagePaths = imagePaths, handled = false)

public fun inputHandled(): InputDecision =
    InputDecisionImpl(text = null, imagePaths = emptyList(), handled = true)

internal data class InputDecisionImpl(
    override val text: String?,
    override val imagePaths: List<String>,
    override val handled: Boolean,
) : InputDecision

public data class ToolCallRequest(
    public val sessionId: String,
    public val callId: String,
    public val toolName: String,
    public val args: JsonObject,
)

@JvmInline
public value class ToolCallAction private constructor(public val wire: String) {
    public companion object {
        public val CONTINUE: ToolCallAction = ToolCallAction("continue")
        public val BLOCK: ToolCallAction = ToolCallAction("block")
        public val REWRITE: ToolCallAction = ToolCallAction("rewrite")
        public val ALL: List<ToolCallAction> = listOf(CONTINUE, BLOCK, REWRITE)

        public fun fromWire(value: String): ToolCallAction? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface ToolCallDecision {
    public val action: ToolCallAction
    public val reason: String?
    public val args: JsonObject?
}

public fun toolContinue(): ToolCallDecision =
    ToolCallDecisionImpl(ToolCallAction.CONTINUE, reason = null, args = null)

public fun toolBlock(reason: String): ToolCallDecision =
    ToolCallDecisionImpl(ToolCallAction.BLOCK, reason = reason, args = null)

public fun toolRewrite(args: JsonObject): ToolCallDecision =
    ToolCallDecisionImpl(ToolCallAction.REWRITE, reason = null, args = args)

internal data class ToolCallDecisionImpl(
    override val action: ToolCallAction,
    override val reason: String?,
    override val args: JsonObject?,
) : ToolCallDecision

public data class ToolExecutionResult(
    public val sessionId: String,
    public val callId: String,
    public val toolName: String,
    public val result: PluginToolResult,
)

@JvmInline
public value class ToolResultAction private constructor(public val wire: String) {
    public companion object {
        public val KEEP: ToolResultAction = ToolResultAction("keep")
        public val REPLACE: ToolResultAction = ToolResultAction("replace")
        public val ALL: List<ToolResultAction> = listOf(KEEP, REPLACE)

        public fun fromWire(value: String): ToolResultAction? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface ToolResultDecision {
    public val action: ToolResultAction
    public val result: PluginToolResult?
}

public fun resultKeep(): ToolResultDecision =
    ToolResultDecisionImpl(ToolResultAction.KEEP, result = null)

public fun resultReplace(result: PluginToolResult): ToolResultDecision =
    ToolResultDecisionImpl(ToolResultAction.REPLACE, result = result)

internal data class ToolResultDecisionImpl(
    override val action: ToolResultAction,
    override val result: PluginToolResult?,
) : ToolResultDecision

public interface PluginHooks {
    public suspend fun sessionStarted(event: SessionStartedEvent): Unit = Unit

    public suspend fun sessionClosed(event: SessionClosedEvent): Unit = Unit

    public suspend fun runStarted(event: RunStartedEvent): Unit = Unit

    public suspend fun runFinished(event: RunFinishedEvent): Unit = Unit

    public suspend fun turnStarted(event: TurnStartedEvent): Unit = Unit

    public suspend fun turnFinished(event: TurnFinishedEvent): Unit = Unit

    public suspend fun messageStarted(event: MessageStartedEvent): Unit = Unit

    public suspend fun messageDelta(event: MessageDeltaEvent): Unit = Unit

    public suspend fun messageFinished(event: MessageFinishedEvent): Unit = Unit

    public suspend fun toolExecutionUpdate(event: ToolExecutionUpdateEvent): Unit = Unit

    public suspend fun compactionStarted(event: CompactionStartedEvent): Unit = Unit

    public suspend fun compactionFinished(event: CompactionFinishedEvent): Unit = Unit

    public suspend fun compactionFailed(event: CompactionFailedEvent): Unit = Unit

    public suspend fun modelChanged(event: ModelChangedEvent): Unit = Unit

    public suspend fun thinkingLevelChanged(event: ThinkingLevelChangedEvent): Unit = Unit

    public suspend fun retryScheduled(event: RetryScheduledEvent): Unit = Unit

    public suspend fun retrySucceeded(event: RetrySucceededEvent): Unit = Unit

    public suspend fun retryExhausted(event: RetryExhaustedEvent): Unit = Unit

    public suspend fun mcpStatusChanged(event: McpStatusChangedEvent): Unit = Unit

    public suspend fun mcpToolsChanged(event: McpToolsChangedEvent): Unit = Unit

    public suspend fun busEvent(event: BusEventReceived): Unit = Unit

    public suspend fun notificationReceived(event: NotificationReceivedEvent): Unit = Unit

    public suspend fun notificationAction(event: NotificationActionEvent): Unit = Unit

    public suspend fun backgroundTask(event: BackgroundTaskEvent): Unit = Unit

    public suspend fun shareReceived(event: ShareReceivedEvent): Unit = Unit

    public suspend fun deepLinkReceived(event: DeepLinkReceivedEvent): Unit = Unit

    public suspend fun appLifecycle(event: AppLifecycleEvent): Unit = Unit

    public suspend fun promptTransform(request: PromptRequest): String = request.prompt

    public suspend fun context(request: PluginContextRequest): String? = null

    public suspend fun inputTransform(request: InputRequest): InputDecision =
        inputContinue(request.text, request.imagePaths)

    public suspend fun beforeToolCall(request: ToolCallRequest): ToolCallDecision = toolContinue()

    public suspend fun afterToolExecution(request: ToolExecutionResult): ToolResultDecision = resultKeep()

    public companion object {
        public const val TRANSFORM_TIMEOUT_MS: Long = 2_000

        public const val OBSERVE_TIMEOUT_MS: Long = 5_000

        public val None: PluginHooks = object : PluginHooks {}
    }
}
