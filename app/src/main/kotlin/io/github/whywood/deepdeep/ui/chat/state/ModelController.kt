package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelControl
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.resolveThinkingLevel
import io.github.whywood.deepdeep.ui.common.chat.ChatUiState
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
    private val onModelChanged: (String, SessionModelSnapshot) -> Unit,
    private val userFacingError: (String) -> String,
    private val switchModelFailedText: () -> String,
    private val switchThinkingFailedText: () -> String,
) {
    fun setModel(modelId: String) {
        val current = state()
        if (current.switchingConversation) return
        val option = current.models.firstOrNull { it.id == modelId } ?: return
        val providerId = current.providerId.ifBlank { option.providerId }
        val thinking = option.thinkingLevels.resolveThinkingLevel(current.thinkingLevel)
        changeSessionModel(SessionModelSnapshot(providerId, option.id, thinking), thinkingOnly = false)
    }

    fun setProvider(providerId: String) {
        val current = state()
        if (current.switchingConversation) return
        if (providerId == current.providerId) return
        val options = providers.models(providerId)
        val model = options.firstOrNull { it.id == current.modelId } ?: options.firstOrNull() ?: return
        val thinking = model.thinkingLevels.resolveThinkingLevel(current.thinkingLevel)
        changeSessionModel(SessionModelSnapshot(providerId, model.id, thinking), thinkingOnly = false)
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        val current = state()
        if (current.switchingConversation) return
        val model = current.sessionModel() ?: return
        if (model.thinkingLevel == level) return
        changeSessionModel(model.copy(thinkingLevel = level), thinkingOnly = true)
    }

    fun applySessionModel(model: SessionModelSnapshot?) {
        if (model == null) return
        val options = providers.models(model.providerId)
        val option = options.firstOrNull { it.id == model.modelId }
        val thinking = option?.thinkingLevels?.resolveThinkingLevel(model.thinkingLevel) ?: model.thinkingLevel
        update {
            it.copy(
                providerId = model.providerId,
                modelId = model.modelId,
                modelLabel = option?.displayName ?: model.modelId,
                thinkingLevel = thinking,
                models = options,
            )
        }
    }

    fun applyConfig(config: ModelConfig) = applySessionModel(config.toSnapshot())

    fun refreshModels() {
        val current = state()
        if (current.providerId.isBlank()) return
        val options = providers.models(current.providerId)
        if (options.isEmpty()) return
        val option = options.firstOrNull { it.id == current.modelId }
        update {
            it.copy(
                modelLabel = option?.displayName ?: it.modelLabel,
                thinkingLevel = option?.thinkingLevels?.resolveThinkingLevel(it.thinkingLevel) ?: it.thinkingLevel,
                models = options,
            )
        }
    }

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
        onModelChanged(sessionId, model)
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
                onModelChanged(sessionId, truth)
            }
        }
    }
}

private fun ModelConfig.toSnapshot(): SessionModelSnapshot =
    SessionModelSnapshot(providerId, modelId, thinkingLevel)
