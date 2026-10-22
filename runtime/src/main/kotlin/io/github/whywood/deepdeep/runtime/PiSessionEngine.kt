package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentMessage
import dev.pi.agent.asLlmMessage
import dev.pi.ai.AssistantMessage as PiAssistantMessage
import dev.pi.ai.BlockContent
import dev.pi.ai.PlainTextContent
import dev.pi.ai.TextContent
import dev.pi.ai.ThinkingContent
import dev.pi.ai.ToolCall
import dev.pi.ai.ToolResultMessage
import dev.pi.ai.UserMessage
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.okHttpTransport
import dev.pi.coding.AgentSessionOptions
import dev.pi.coding.createAgentSession
import dev.pi.coding.paths.AgentPaths
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.CodingHooks
import dev.pi.coding.session.SessionInfo
import dev.pi.coding.session.SessionManager
import dev.pi.coding.tools.FileMutationQueue
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.defaultCodingTools
import java.io.File
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class PiSessionEngine(
    workspace: File,
    agentDir: File,
    private val credentials: CredentialStore,
    private val shellExecutor: ShellExecutor?,
    private val providers: ProviderCatalog,
) {
    val cwd: Path = workspace.absolutePath.toPath()
    val agentDirectory: Path = agentDir.absolutePath.toPath()
    val paths = AgentPaths(cwd, agentDirectory)

    private val fs = FileSystem.SYSTEM
    private val transport = okHttpTransport()

    suspend fun createSession(file: Path?, config: ModelConfig, shellEnabled: Boolean = true): AgentSession {
        val resolved = providers.resolveConfig(config)
        val modelId = resolved.modelId
        val activeShell = if (shellEnabled) shellExecutor else null
        val tools = defaultCodingTools(
            cwd = cwd,
            fs = fs,
            mutationQueue = FileMutationQueue(),
            shellExecutor = activeShell,
        )
        return createAgentSession(
            AgentSessionOptions(
                cwd = cwd,
                agentDir = agentDirectory,
                transport = transport,
                fs = fs,
                model = providers.modelReference(resolved.providerId, modelId),
                thinkingLevel = resolved.thinkingLevel.runtimeValue(),
                sessionFile = file,
                tools = tools,
                shellExecutor = activeShell,
                hooks = CodingHooks(
                    extraSystemPromptSections = {
                        mapOf("deepdeep_environment" to environmentPrompt(resolved.providerId, modelId))
                    },
                ),
                credentials = credentials,
                projectTrusted = true,
            ),
        )
    }

    suspend fun listSessions(): List<ConversationSummary> = SessionManager.list(paths, fs).map { info ->
        info.toSummary()
    }

    fun history(session: AgentSession): List<AssistantMessage> = mapHistory(session.messages)

    suspend fun deleteSessionFile(filePath: String) {
        runCatching { fs.delete(filePath.toPath()) }
    }

    fun titleFor(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return "新对话"
        return if (normalized.length <= 28) normalized else normalized.take(28) + "…"
    }

    private fun SessionInfo.toSummary(): ConversationSummary = ConversationSummary(
        id = sessionId,
        title = name?.takeIf { it.isNotBlank() } ?: "新对话",
        updatedAt = parseTimestamp(timestamp),
        filePath = sessionFile.toString(),
    )

    private fun parseTimestamp(value: String): Long = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun environmentPrompt(providerId: String, modelId: String): String = buildString {
        appendLine("<deepdeep_environment>")
        appendLine("你是 deepdeep，一个运行在 Android 手机上的个人 agent。")
        appendLine("当前时间：${OffsetDateTime.now()}。")
        appendLine("模型：$providerId/$modelId。")
        appendLine("工作区：$cwd。")
        appendLine("bash 工具运行在 Alpine Linux（proot）里，工作目录是 /workspace，它和宿主工作区是同一个目录。")
        appendLine("pi 的文件工具（read/write/edit/ls/grep/find）直接操作宿主工作区。")
        appendLine("不需要请求审批；直接执行命令。不要编造工具结果。")
        appendLine("</deepdeep_environment>")
    }

    private fun mapHistory(messages: List<AgentMessage>): List<AssistantMessage> {
        val result = mutableListOf<AssistantMessage>()
        val traces = mutableMapOf<String, ToolTrace>()
        var messageId = 0L
        var stepId = 0L

        for (agentMessage in messages) {
            when (val message = agentMessage.asLlmMessage()) {
                is UserMessage -> {
                    val text = message.content.asText()
                    if (text.isNotBlank()) {
                        result += AssistantMessage(messageId++, AssistantRole.USER, text)
                    }
                }

                is PiAssistantMessage -> {
                    val text = message.content.filterIsInstance<TextContent>().joinToString("") { it.text }
                    val steps = mutableListOf<AssistantStep>()
                    for (content in message.content) {
                        when (content) {
                            is ThinkingContent -> {
                                if (content.thinking.isNotBlank()) {
                                    steps += AssistantStep.Reasoning(stepId++, content.thinking)
                                }
                            }

                            is ToolCall -> {
                                val trace = ToolTrace(
                                    id = stepId++,
                                    name = content.name,
                                    arguments = content.arguments.toStringMap(),
                                    toolCallId = content.id,
                                )
                                traces[content.id] = trace
                                steps += AssistantStep.Tool(trace)
                            }

                            else -> Unit
                        }
                    }
                    if (text.isNotBlank() || steps.isNotEmpty()) {
                        result += AssistantMessage(
                            id = messageId++,
                            role = AssistantRole.ASSISTANT,
                            text = text,
                            steps = steps,
                            status = MessageStatus.DONE,
                            startedAt = message.timestamp,
                            finishedAt = message.timestamp,
                        )
                    }
                }

                is ToolResultMessage -> {
                    val output = message.content.filterIsInstance<TextContent>().joinToString("") { it.text }
                    val existing = traces[message.toolCallId]
                    if (existing == null) {
                        val trace = ToolTrace(
                            id = stepId++,
                            name = message.toolName,
                            arguments = emptyMap(),
                            status = if (message.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                            output = output.takeIf { it.isNotBlank() && !message.isError },
                            error = output.takeIf { it.isNotBlank() && message.isError },
                            toolCallId = message.toolCallId,
                        )
                        traces[message.toolCallId] = trace
                        result += AssistantMessage(
                            id = messageId++,
                            role = AssistantRole.ASSISTANT,
                            text = "",
                            steps = listOf(AssistantStep.Tool(trace)),
                            status = MessageStatus.DONE,
                            startedAt = message.timestamp,
                            finishedAt = message.timestamp,
                        )
                    } else {
                        val updated = existing.copy(
                            status = if (message.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                            output = output.takeIf { it.isNotBlank() && !message.isError },
                            error = output.takeIf { it.isNotBlank() && message.isError },
                        )
                        traces[message.toolCallId] = updated
                        val index = result.indexOfLast { item ->
                            item.steps.any { step ->
                                step is AssistantStep.Tool && step.trace.toolCallId == message.toolCallId
                            }
                        }
                        if (index >= 0) {
                            val target = result[index]
                            result[index] = target.copy(
                                steps = target.steps.map { step ->
                                    if (step is AssistantStep.Tool && step.trace.toolCallId == message.toolCallId) {
                                        AssistantStep.Tool(updated)
                                    } else {
                                        step
                                    }
                                },
                            )
                        }
                    }
                }

                else -> Unit
            }
        }
        return result
    }

    private fun dev.pi.ai.MessageContent.asText(): String = when (this) {
        is PlainTextContent -> text
        is BlockContent -> blocks.filterIsInstance<TextContent>().joinToString("") { it.text }
    }

    private fun JsonObject.toStringMap(): Map<String, String> = entries.associate { (key, value) ->
        key to value.toString().trim('"')
    }
}

fun ThinkingLevel.runtimeValue(): String = when (this) {
    ThinkingLevel.OFF -> "off"
    ThinkingLevel.LOW -> "low"
    ThinkingLevel.MEDIUM -> "medium"
    ThinkingLevel.HIGH -> "high"
}

fun ThinkingLevel.displayName(): String = when (this) {
    ThinkingLevel.OFF -> "关闭"
    ThinkingLevel.LOW -> "低"
    ThinkingLevel.MEDIUM -> "中"
    ThinkingLevel.HIGH -> "高"
}
