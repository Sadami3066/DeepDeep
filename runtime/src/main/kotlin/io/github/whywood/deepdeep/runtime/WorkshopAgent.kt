package io.github.whywood.deepdeep.runtime

data class WorkshopAgentModel(
    val providerId: String,
    val modelId: String,
    val label: String,
    val thinkingLevels: List<String>,
)

data class WorkshopAgentTool(
    val name: String,
    val description: String,
)

data class WorkshopAgentSkill(
    val name: String,
    val description: String,
)

data class WorkshopAgentCapabilities(
    val tools: List<WorkshopAgentTool>,
    val skills: List<WorkshopAgentSkill>,
)

data class WorkshopAgentRequest(
    val prompt: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val thinkingLevel: String? = null,
    val systemPrompt: String? = null,
    val maxTurns: Int? = null,
    val tools: List<String>? = null,
    val skills: List<String>? = null,
)

data class WorkshopAgentEvent(
    val type: String,
    val text: String? = null,
    val name: String? = null,
    val isError: Boolean? = null,
    val durationMs: Long? = null,
    val status: String? = null,
    val truncated: Boolean = false,
    val code: String? = null,
    val message: String? = null,
)

object WorkshopAgentEventType {
    const val RUN_START = "run_start"
    const val TEXT_DELTA = "text_delta"
    const val THINKING_DELTA = "thinking_delta"
    const val TOOL_START = "tool_start"
    const val TOOL_END = "tool_end"
    const val TURN_END = "turn_end"
    const val RUN_END = "run_end"
    const val RUN_ERROR = "run_error"
}

object WorkshopAgentRunStatus {
    const val COMPLETED = "completed"
    const val TRUNCATED = "truncated"
}

object WorkshopAgentErrorCode {
    const val AGENT_FAILED = "agent_failed"
    const val TIMEOUT = "timeout"
}

interface WorkshopAgentGateway {
    suspend fun models(): List<WorkshopAgentModel>

    suspend fun capabilities(): WorkshopAgentCapabilities

    suspend fun run(
        request: WorkshopAgentRequest,
        onEvent: suspend (WorkshopAgentEvent) -> Unit = {},
    ): String
}
