package io.github.whywood.deepdeep.runtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RuntimeModels(
    private val mutex: Mutex,
    private val handles: ConcurrentHashMap<String, RuntimeHandle>,
    private val modelSettings: ModelSettings,
    private val providers: ProviderDirectory,
    private val emitUsage: suspend (RuntimeHandle, String) -> Unit,
) {
    suspend fun history(sessionId: String): List<AssistantMessage> {
        val loaded = mutex.withLock {
            handles[sessionId]?.let { handle -> handle to handle.session.history() }
        } ?: return emptyList()
        emitUsage(loaded.first, loaded.first.state.runId.orEmpty())
        return loaded.second
    }

    suspend fun session(sessionId: String): SessionModelSnapshot? = mutex.withLock {
        val handle = handles[sessionId] ?: return@withLock null
        runCatching {
            SessionModelSnapshot(
                providerId = handle.session.model.provider,
                modelId = handle.session.model.id,
                thinkingLevel = thinkingLevelOf(handle.session.thinkingLevel),
            )
        }.getOrNull()
    }

    suspend fun setSession(
        sessionId: String,
        providerId: String,
        modelId: String,
        thinkingLevel: ThinkingLevel,
    ) {
        val handle = mutex.withLock { handles[sessionId] } ?: return
        val config = providers.resolveConfig(ModelConfig(providerId, modelId, thinkingLevel))
        val model = providers.sdkModel(config.providerId, config.modelId)
            ?: error("Model not found: ${config.providerId}/${config.modelId}")
        handle.session.setModel(model, config.thinkingLevel.runtimeValue())
        emitUsage(handle, handle.state.runId.orEmpty())
    }

    suspend fun setThinking(sessionId: String, thinkingLevel: ThinkingLevel) {
        val handle = mutex.withLock { handles[sessionId] } ?: return
        handle.session.setThinkingLevel(thinkingLevel.runtimeValue())
    }

    suspend fun currentConfig(): ModelConfig = modelSettings.read()

    suspend fun usage(sessionId: String): SessionUsage? {
        val handle = mutex.withLock { handles[sessionId] } ?: return null
        return handle.session.usage()
    }

    suspend fun compact(sessionId: String): CompactionOutcome {
        val handle = mutex.withLock { handles[sessionId] } ?: return CompactionOutcome.Unavailable
        if (handle.state.busy || handle.session.isStreaming) {
            emitUsage(handle, handle.state.runId.orEmpty())
            return CompactionOutcome.Busy
        }
        val outcome = try {
            val result = handle.session.compact()
            if (result == null) {
                CompactionOutcome.Empty
            } else {
                CompactionOutcome.Done(result.tokensBefore, result.estimatedTokensAfter)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CompactionOutcome.Failed(error.message)
        }
        emitUsage(handle, handle.state.runId.orEmpty())
        return outcome
    }

    suspend fun emitUsage(handle: RuntimeHandle, runId: String) = emitUsage.invoke(handle, runId)
}
