package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentEvent
import dev.pi.ai.TextDeltaEvent
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.SessionEvent
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import okio.FileSystem

private const val MAX_PROMPT_CONTEXT_CHARS = 32 * 1024

internal fun buildPrompt(
    mode: WorkshopRunMode,
    creation: WorkshopCreation,
    instruction: String,
    context: JsonElement?,
): String = buildString {
    append("You are editing the DeepDeep workshop app ")
    append(creation.manifest.name)
    append(" (")
    append(creation.manifest.type.wire)
    append(") located at ")
    append(creation.directory.absolutePath)
    append(".\nFollow the AGENTS.md in this folder.\n\n")
    append(instruction)
    if (context != null) {
        val encoded = context.toString()
        append("\n\nContext from the page:\n")
        append(if (encoded.length > MAX_PROMPT_CONTEXT_CHARS) encoded.take(MAX_PROMPT_CONTEXT_CHARS) else encoded)
    }
}

class WorkshopAgentService(
    private val engineFactory: suspend (File) -> PiSessionEngine,
    private val modelConfigStore: ModelConfigStore,
    private val sandboxTimeoutMs: Long = 300_000L,
    private val fullTimeoutMs: Long = 900_000L,
) {
    private val stateMutex = Mutex()
    private var activeCreationId: String? = null
    private var activeSession: AgentSession? = null
    private var cancelRequested = false

    fun runnerFor(creation: WorkshopCreation): WorkshopAgentRunner = object : WorkshopAgentRunner {
        override suspend fun run(
            instruction: String,
            context: JsonElement?,
            onEvent: (WorkshopAgentEvent) -> Unit,
        ): String = withContext(Dispatchers.IO) {
            execute(WorkshopRunMode.SANDBOX, creation, instruction, context, onEvent)
        }

        override suspend fun cancel() {
            this@WorkshopAgentService.cancel(creation.manifest.id)
        }
    }

    suspend fun runFull(
        creation: WorkshopCreation,
        instruction: String,
        onEvent: (WorkshopAgentEvent) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        execute(WorkshopRunMode.FULL, creation, instruction, null, onEvent)
    }

    suspend fun cancel(creationId: String) {
        val session = stateMutex.withLock {
            if (activeCreationId != creationId) return
            cancelRequested = true
            activeSession
        }
        session?.abort()
    }

    private suspend fun execute(
        mode: WorkshopRunMode,
        creation: WorkshopCreation,
        instruction: String,
        context: JsonElement?,
        onEvent: (WorkshopAgentEvent) -> Unit,
    ): String {
        val creationId = creation.manifest.id
        stateMutex.withLock {
            if (activeCreationId != null) {
                throw WorkshopAgentException(
                    WorkshopBridgeErrors.BUSY,
                    "Another workshop agent run is in progress",
                )
            }
            activeCreationId = creationId
            cancelRequested = false
        }
        var session: AgentSession? = null
        try {
            val engine = engineFactory(creation.directory)
            val config = modelConfigStore.read()
            val created = engine.createSession(
                file = null,
                config = config,
                shellEnabled = mode == WorkshopRunMode.FULL,
                mcpEnabled = false,
                toolsOverride = if (mode == WorkshopRunMode.SANDBOX) {
                    sandboxedFileTools(engine.cwd, FileSystem.SYSTEM)
                } else {
                    null
                },
            )
            session = created
            stateMutex.withLock { activeSession = created }
            if (stateMutex.withLock { cancelRequested }) {
                throw WorkshopAgentException(WorkshopBridgeErrors.AGENT_CANCELLED, "Agent run cancelled")
            }
            onEvent(WorkshopAgentEvent.Started)
            val failure = AtomicReference<String?>(null)
            val prompt = buildPrompt(mode, creation, instruction, context)
            coroutineScope {
                val forwarding = launch(start = CoroutineStart.UNDISPATCHED) {
                    created.events.collect { event ->
                        when (event) {
                            is SessionEvent.AgentEventReceived -> when (val inner = event.event) {
                                is AgentEvent.MessageUpdate -> {
                                    val update = inner.assistantMessageEvent
                                    if (update is TextDeltaEvent) {
                                        onEvent(WorkshopAgentEvent.Text(update.delta))
                                    }
                                }

                                is AgentEvent.ToolExecutionStart -> onEvent(WorkshopAgentEvent.Tool(inner.toolName))

                                else -> Unit
                            }

                            is SessionEvent.SessionError -> failure.set(event.message)

                            else -> Unit
                        }
                    }
                }
                try {
                    withTimeout(timeoutFor(mode)) { created.prompt(prompt) }
                } finally {
                    forwarding.cancel()
                }
            }
            if (stateMutex.withLock { cancelRequested }) {
                throw WorkshopAgentException(WorkshopBridgeErrors.AGENT_CANCELLED, "Agent run cancelled")
            }
            failure.get()?.let { message ->
                throw WorkshopAgentException(WorkshopBridgeErrors.AGENT_FAILED, message)
            }
            val text = created.getLastAssistantText().orEmpty()
            onEvent(WorkshopAgentEvent.Finished)
            return text
        } catch (error: TimeoutCancellationException) {
            runCatching { session?.abort() }
            throw WorkshopAgentException(WorkshopBridgeErrors.AGENT_TIMEOUT, "Agent run timed out")
        } catch (error: CancellationException) {
            runCatching { session?.abort() }
            throw WorkshopAgentException(WorkshopBridgeErrors.AGENT_CANCELLED, "Agent run cancelled")
        } catch (error: WorkshopAgentException) {
            throw error
        } catch (error: Exception) {
            throw WorkshopAgentException(mapAgentFailure(error), error.message ?: "Agent run failed")
        } finally {
            session?.let { finished ->
                runCatching { finished.close() }
                finished.sessionFile?.let { file -> runCatching { FileSystem.SYSTEM.delete(file) } }
            }
            stateMutex.withLock {
                if (activeCreationId == creationId) {
                    activeCreationId = null
                    activeSession = null
                    cancelRequested = false
                }
            }
        }
    }

    private fun timeoutFor(mode: WorkshopRunMode): Long = when (mode) {
        WorkshopRunMode.SANDBOX -> sandboxTimeoutMs
        WorkshopRunMode.FULL -> fullTimeoutMs
    }

    private fun mapAgentFailure(error: Exception): String {
        val message = error.message.orEmpty()
        return if (message.contains("No model configured", ignoreCase = true)) {
            WorkshopBridgeErrors.MODEL_MISSING
        } else {
            WorkshopBridgeErrors.AGENT_FAILED
        }
    }
}
