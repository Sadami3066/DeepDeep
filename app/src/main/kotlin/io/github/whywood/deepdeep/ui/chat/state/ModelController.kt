package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelControl
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ModelController(
    private val runtime: ModelControl,
    private val queries: ConversationQueries,
    private val providers: ProviderDirectory,
    private val settings: ModelSettings,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val state: () -> ChatUiState,
    private val update: ((ChatUiState) -> ChatUiState) -> Unit,
    private val cacheCurrentSession: () -> Unit,
    private val userFacingError: (String) -> String,
    private val switchModelFailedText: () -> String,
    private val switchThinkingFailedText: () -> String,
) {
    fun setModel(modelId: String) {
        val current = state()
        if (current.sending || current.switchingConversation) return
        val option = current.models.firstOrNull { it.id == modelId } ?: return
        val providerId = current.providerId.ifBlank { option.providerId }
        val thinking = current.thinkingLevel.takeIf { it in option.thinkingLevels } ?: ThinkingLevel.OFF
        changeSessionModel(SessionModelSnapshot(providerId, option.id, thinking), thinkingOnly = false)
    }

    fun setProvider(providerId: String) {
        val current = state()
        if (current.sending || current.switchingConversation) return
        if (providerId == current.providerId) return
        val options = providers.models(providerId)
        val model = options.firstOrNull { it.id == current.modelId } ?: options.firstOrNull() ?: return
        val thinking = current.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
        changeSessionModel(SessionModelSnapshot(providerId, model.id, thinking), thinkingOnly = false)
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        val current = state()
        if (current.sending || current.switchingConversation) return
        val model = current.sessionModel() ?: return
        if (model.thinkingLevel == level) return
        changeSessionModel(model.copy(thinkingLevel = level), thinkingOnly = true)
    }

    fun applySessionModel(model: SessionModelSnapshot?) {
        if (model == null) return
        val options = providers.models(model.providerId)
        val option = options.firstOrNull { it.id == model.modelId }
        update {
            it.copy(
                providerId = model.providerId,
                modelId = model.modelId,
                modelLabel = option?.displayName ?: model.modelId,
                thinkingLevel = model.thinkingLevel,
                models = options,
            )
        }
    }

    fun applyConfig(config: ModelConfig) = applySessionModel(config.toSnapshot())

    fun adoptConfiguredModel(config: ModelConfig) {
        val current = state()
        if (current.sending || current.switchingConversation) return
        val sessionId = current.conversationId
        if (sessionId != null && current.messages.isNotEmpty()) return
        val model = config.toSnapshot()
        if (sessionId == null) {
            applySessionModel(model)
            scope.launch { settings.save(config) }
        } else {
            changeSessionModel(model, thinkingOnly = false)
        }
    }

    private fun changeSessionModel(model: SessionModelSnapshot, thinkingOnly: Boolean) {
        val sessionId = state().conversationId ?: return
        applySessionModel(model)
        cacheCurrentSession()
        scope.launch {
            try {
                if (thinkingOnly) {
                    runtime.setSessionThinkingLevel(sessionId, model.thinkingLevel)
                } else {
                    runtime.setSessionModel(sessionId, model.providerId, model.modelId, model.thinkingLevel)
                }
                settings.save(ModelConfig(model.providerId, model.modelId, model.thinkingLevel))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val fallback = if (thinkingOnly) switchThinkingFailedText() else switchModelFailedText()
                update { it.copy(error = userFacingError(error.message ?: fallback)) }
            }
            val truth = withContext(ioDispatcher) { queries.sessionModel(sessionId) }
            if (truth != null && state().conversationId == sessionId) {
                applySessionModel(truth)
                cacheCurrentSession()
            }
        }
    }
}

private fun ModelConfig.toSnapshot(): SessionModelSnapshot =
    SessionModelSnapshot(providerId, modelId, thinkingLevel)
