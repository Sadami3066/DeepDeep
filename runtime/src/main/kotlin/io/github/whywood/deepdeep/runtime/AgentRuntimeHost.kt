package io.github.whywood.deepdeep.runtime

import android.util.Log
import dev.pi.agent.AgentEvent
import dev.pi.ai.TextContent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.SessionEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

class AgentRuntimeHost(
    private val engine: PiSessionEngine,
    private val modelConfigStore: ModelConfigStore,
    private val shellEnabled: suspend () -> Boolean = { true },
) {
    private val eventsBuffer = MutableSharedFlow<RuntimeEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val events: SharedFlow<RuntimeEvent> = eventsBuffer.asSharedFlow()

    private val mutex = Mutex()
    private var session: AgentSession? = null
    private var unsubscribe: (() -> Unit)? = null
    private var started = false
    private var stopRequested = false

    @Volatile
    var isBusy: Boolean = false
        private set

    @Volatile
    var currentSessionFile: String? = null
        private set

    suspend fun start() = mutex.withLock {
        if (started) return@withLock
        started = true
        val recent = engine.listSessions().firstOrNull()
        if (recent != null) {
            openLocked(recent.filePath.toPath())
        } else {
            newLocked()
        }
    }

    suspend fun ensureStarted() {
        if (!started) start()
    }

    suspend fun send(text: String) = mutex.withLock {
        val active = session ?: error("Agent session is not ready")
        stopRequested = false
        isBusy = true
        Log.i(TAG, "send start chars=${text.length}")
        try {
            active.prompt(text)
            updateTitle(active, text)
            val last = active.getLastAssistantText().orEmpty()
            Log.i(TAG, "send end stop=$stopRequested chars=${last.length}")
            if (!stopRequested) {
                eventsBuffer.emit(RuntimeEvent.RunFinished(last))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (stopRequested) {
                eventsBuffer.emit(RuntimeEvent.RunStopped)
            } else {
                eventsBuffer.emit(RuntimeEvent.RunFailed(error.message ?: "请求失败，请稍后重试。"))
            }
        } finally {
            isBusy = false
        }
    }

    fun stop() {
        if (stopRequested) return
        stopRequested = true
        Log.i(TAG, "stop requested")
        eventsBuffer.tryEmit(RuntimeEvent.RunStopped)
        session?.abort()
    }

    suspend fun newChat() = mutex.withLock {
        detachLocked()
        newLocked()
    }

    suspend fun open(filePath: String) = mutex.withLock {
        detachLocked()
        openLocked(filePath.toPath())
    }

    suspend fun history(): List<AssistantMessage> = mutex.withLock {
        session?.let { engine.history(it) }.orEmpty()
    }

    suspend fun currentConfig(): ModelConfig = modelConfigStore.read()

    suspend fun setModel(modelId: String, thinkingLevel: ThinkingLevel) = mutex.withLock {
        val normalized = DeepSeekModels.model(modelId)?.id ?: DeepSeekModels.defaultModelId()
        val config = ModelConfig(normalized, thinkingLevel)
        modelConfigStore.save(config)
        val active = session ?: return@withLock
        val model = DeepSeekModels.model(normalized) ?: return@withLock
        active.setModel(model, thinkingLevel.runtimeValue())
    }

    suspend fun setThinkingLevel(thinkingLevel: ThinkingLevel) = mutex.withLock {
        val config = modelConfigStore.read()
        modelConfigStore.save(config.copy(thinkingLevel = thinkingLevel))
        session?.setThinkingLevel(thinkingLevel.runtimeValue())
    }

    suspend fun sessions(): List<ConversationSummary> = engine.listSessions()

    suspend fun reconfigure() = mutex.withLock {
        val file = currentSessionFile
        detachLocked()
        if (file != null) {
            openLocked(file.toPath())
        } else {
            newLocked()
        }
    }

    suspend fun close() = mutex.withLock {
        detachLocked()
        started = false
    }

    private suspend fun newLocked() {
        val config = modelConfigStore.read()
        val created = engine.createSession(null, config, shellEnabled())
        attach(created)
    }

    private suspend fun openLocked(file: okio.Path) {
        val config = modelConfigStore.read()
        val created = engine.createSession(file, config, shellEnabled())
        attach(created)
    }

    private suspend fun attach(created: AgentSession) {
        session = created
        currentSessionFile = created.sessionFile?.toString()
        unsubscribe = created.subscribe { event -> handle(event) }
    }

    private suspend fun detachLocked() {
        unsubscribe?.invoke()
        unsubscribe = null
        session?.close()
        session = null
        currentSessionFile = null
        isBusy = false
    }

    private suspend fun updateTitle(active: AgentSession, text: String) {
        if (active.sessionManager.getSessionName() != null) return
        active.sessionManager.appendSessionInfo(engine.titleFor(text))
    }

    private suspend fun handle(event: SessionEvent) {
        when (event) {
            is SessionEvent.AgentEventReceived -> Log.d(TAG, "agent event ${event.event.type}")
            is SessionEvent.AgentEventReceived -> handleAgentEvent(event.event)
            is SessionEvent.RetryScheduled -> eventsBuffer.emit(
                RuntimeEvent.Status("重试中（第 ${event.attempt} 次）"),
            )

            is SessionEvent.RetryExhausted -> eventsBuffer.emit(
                RuntimeEvent.Status("重试失败：${event.errorMessage ?: "未知错误"}"),
            )

            is SessionEvent.CompactionStarted -> eventsBuffer.emit(RuntimeEvent.Status("正在压缩上下文…"))
            is SessionEvent.CompactionFinished -> eventsBuffer.emit(RuntimeEvent.Status("上下文已压缩"))
            is SessionEvent.CompactionFailed -> eventsBuffer.emit(
                RuntimeEvent.Status("上下文压缩失败：${event.errorMessage ?: "未知错误"}"),
            )

            is SessionEvent.SessionError -> eventsBuffer.emit(RuntimeEvent.Status(event.message))
            else -> Unit
        }
    }

    private suspend fun handleAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
                is TextDeltaEvent -> eventsBuffer.emit(RuntimeEvent.TextDelta(update.delta))
                is ThinkingDeltaEvent -> eventsBuffer.emit(RuntimeEvent.ReasoningDelta(update.delta))
                else -> Unit
            }

            is AgentEvent.ToolExecutionStart -> eventsBuffer.emit(
                RuntimeEvent.ToolStarted(
                    toolName = event.toolName,
                    arguments = event.args.entries.associate { (key, value) ->
                        key to value.toString().trim('"')
                    },
                    toolCallId = event.toolCallId,
                ),
            )

            is AgentEvent.ToolExecutionEnd -> eventsBuffer.emit(
                RuntimeEvent.ToolFinished(
                    toolName = event.toolName,
                    result = event.result.content.filterIsInstance<TextContent>().joinToString("") { it.text },
                    isError = event.isError,
                    toolCallId = event.toolCallId,
                ),
            )

            else -> Unit
        }
    }
}

private const val TAG = "DeepDeepRuntime"
