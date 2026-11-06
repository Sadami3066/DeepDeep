package io.github.whywood.deepdeep.runtime

data class MiniappAgentModel(
    val providerId: String,
    val modelId: String,
    val label: String,
    val thinkingLevels: List<String>,
)

data class MiniappAgentTool(
    val name: String,
    val description: String,
)

data class MiniappAgentSkill(
    val name: String,
    val description: String,
)

data class MiniappAgentCapabilities(
    val tools: List<MiniappAgentTool>,
    val skills: List<MiniappAgentSkill>,
)

data class MiniappAgentRequest(
    val prompt: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val thinkingLevel: String? = null,
    val systemPrompt: String? = null,
    val maxTurns: Int? = null,
    val tools: List<String>? = null,
    val skills: List<String>? = null,
)

data class MiniappAgentEvent(
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

object MiniappAgentEventType {
    const val RUN_START = "run_start"
    const val TEXT_DELTA = "text_delta"
    const val THINKING_DELTA = "thinking_delta"
    const val TOOL_START = "tool_start"
    const val TOOL_END = "tool_end"
    const val TURN_END = "turn_end"
    const val RUN_END = "run_end"
    const val RUN_ERROR = "run_error"
}

object MiniappAgentRunStatus {
    const val COMPLETED = "completed"
    const val TRUNCATED = "truncated"
}

object MiniappAgentErrorCode {
    const val AGENT_FAILED = "agent_failed"
    const val TIMEOUT = "timeout"
}

interface MiniappAgentGateway {
    suspend fun models(): List<MiniappAgentModel>

    suspend fun capabilities(): MiniappAgentCapabilities

    suspend fun run(
        request: MiniappAgentRequest,
        onEvent: suspend (MiniappAgentEvent) -> Unit = {},
    ): String
}
