package io.github.whywood.deepdeep.runtime

import dev.pi.ai.Model
import dev.pi.ai.ModelCatalog

enum class ThinkingLevel {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val thinkingLevels: List<ThinkingLevel>,
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val filePath: String,
)

enum class AssistantRole {
    USER,
    ASSISTANT,
}

sealed interface AssistantStep {
    val id: Long
    val expanded: Boolean

    data class Reasoning(
        override val id: Long,
        val text: String,
        override val expanded: Boolean = false,
    ) : AssistantStep

    data class Tool(
        val trace: ToolTrace,
    ) : AssistantStep {
        override val id: Long get() = trace.id
        override val expanded: Boolean get() = trace.expanded
    }
}

enum class ToolActivityStatus {
    RUNNING,
    DONE,
    FAILED,
}

data class ToolTrace(
    val id: Long,
    val name: String,
    val arguments: Map<String, String>,
    val status: ToolActivityStatus = ToolActivityStatus.RUNNING,
    val output: String? = null,
    val error: String? = null,
    val expanded: Boolean = false,
    val toolCallId: String = "",
)

data class AssistantMessage(
    val id: Long,
    val role: AssistantRole,
    val text: String,
    val streaming: Boolean = false,
    val steps: List<AssistantStep> = emptyList(),
)

sealed interface RuntimeEvent {
    data class TextDelta(val text: String) : RuntimeEvent

    data class ReasoningDelta(val text: String) : RuntimeEvent

    data class ToolStarted(
        val toolName: String,
        val arguments: Map<String, String>,
        val toolCallId: String,
    ) : RuntimeEvent

    data class ToolFinished(
        val toolName: String,
        val result: String,
        val isError: Boolean,
        val toolCallId: String,
    ) : RuntimeEvent

    data class RunFinished(val text: String) : RuntimeEvent

    data object RunStopped : RuntimeEvent

    data class RunFailed(val message: String) : RuntimeEvent

    data class Status(val message: String) : RuntimeEvent
}

object DeepSeekModels {
    const val PROVIDER_ID = "deepseek"

    fun options(): List<ModelOption> = ModelCatalog.findByProvider(PROVIDER_ID)
        .filterIsInstance<Model>()
        .map { model ->
            ModelOption(
                id = model.id,
                displayName = model.name,
                thinkingLevels = thinkingLevels(model),
            )
        }

    fun defaultModelId(): String {
        val models = options()
        return models.firstOrNull { it.id == "deepseek-v4-flash" }?.id
            ?: models.firstOrNull()?.id
            ?: error("No DeepSeek model in catalog")
    }

    fun model(modelId: String): Model? = ModelCatalog.findChat(PROVIDER_ID, modelId)

    fun modelReference(modelId: String): String = "$PROVIDER_ID/${modelId.ifBlank { defaultModelId() }}"

    private fun thinkingLevels(model: Model): List<ThinkingLevel> {
        if (!model.reasoning) return listOf(ThinkingLevel.OFF)
        val levels = model.thinkingLevelMap.orEmpty().filterValues { it != null }.keys
        return buildList {
            add(ThinkingLevel.OFF)
            if ("low" in levels) add(ThinkingLevel.LOW)
            if ("medium" in levels) add(ThinkingLevel.MEDIUM)
            if ("high" in levels) add(ThinkingLevel.HIGH)
        }.distinct()
    }
}
