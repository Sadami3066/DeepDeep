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

interface WorkshopAgentGateway {
    suspend fun models(): List<WorkshopAgentModel>

    suspend fun capabilities(): WorkshopAgentCapabilities

    suspend fun run(request: WorkshopAgentRequest): String
}
