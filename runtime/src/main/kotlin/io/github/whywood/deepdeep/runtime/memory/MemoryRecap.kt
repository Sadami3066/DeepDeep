package io.github.whywood.deepdeep.runtime.memory

import dev.pi.ai.BlockContent
import dev.pi.ai.PlainTextContent
import dev.pi.ai.TextContent
import dev.pi.ai.UserMessage
import dev.pi.coding.session.SessionEntry
import dev.pi.coding.session.SessionMessageEntry
import io.github.whywood.deepdeep.runtime.AssistantMessage as UiAssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.logInfo
import io.github.whywood.deepdeep.runtime.logWarn
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "MemoryRecap"
private const val MIN_MESSAGES = 4
private const val MIN_INCREMENTAL_MESSAGES = 2
private const val MAX_TRANSCRIPT_CHARS = 80_000
private const val REASON_AUTO = "自动沉淀"

private val SKIP_PATTERN = Regex("^skip[\\s.!。]*$", RegexOption.IGNORE_CASE)
private val EMPTY_MARKERS = setOf(
    "无", "无。", "（无）", "(无)", "None", "None.", "- 无", "- None.", "无内容", "没有",
)

private sealed interface RecapOutcome {
    data object Skip : RecapOutcome

    data class Content(val text: String) : RecapOutcome
}

class MemoryRecap(
    private val store: MemoryStore,
    private val providers: ProviderCatalog,
    private val scope: CoroutineScope,
) {
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()

    fun launchSessionClose(sessionId: String) {
        val captured = store.takeCaptured(sessionId) ?: return
        scope.launch {
            try {
                summarizeCaptured(captured)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logWarn(TAG, "session recap failed", error)
            }
        }
    }

    fun discard(sessionId: String) {
        store.takeCaptured(sessionId)
    }

    suspend fun summarizeMessages(sessionId: String, messages: List<UiAssistantMessage>, reason: String) {
        val transcript = buildTranscript(messages) ?: return
        lockFor(sessionId).withLock {
            withContext(Dispatchers.IO) { summarize(sessionId, null, transcript, null, reason) }
        }
    }

    private suspend fun summarizeCaptured(captured: CapturedTranscript) {
        lockFor(captured.sessionId).withLock {
            val after = withContext(Dispatchers.IO) { store.lastSummarizedEntryId(captured.sessionId) }
            val delta = deltaEntries(captured.entries, after)
            val minMessages = if (after == null) MIN_MESSAGES else MIN_INCREMENTAL_MESSAGES
            val transcript = buildTranscript(delta, minMessages) ?: return@withLock
            val lastMessageId = delta.lastOrNull { it is SessionMessageEntry }?.id
            withContext(Dispatchers.IO) {
                summarize(captured.sessionId, captured.sessionFile, transcript, lastMessageId, REASON_AUTO)
            }
        }
    }

    private fun lockFor(sessionId: String): Mutex = sessionLocks.getOrPut(sessionId) { Mutex() }

    private suspend fun summarize(
        sessionId: String,
        sessionFile: String?,
        transcript: String,
        lastEntryId: String?,
        reason: String,
    ) {
        if (sessionFile != null && !File(sessionFile).isFile) {
            logInfo(TAG, "skip recap for deleted session $sessionId")
            return
        }
        val hash = sha256(transcript)
        if (store.isSummaryDuplicate(sessionId, hash)) return
        val candidates = candidateModels()
        if (candidates.isEmpty()) {
            logInfo(TAG, "no configured model for memory recap")
            return
        }
        val prompt = buildPrompt(transcript)
        var summary: String? = null
        for (candidate in candidates) {
            val raw = runCatching { providers.complete(candidate.providerId, candidate.id, prompt) }.getOrNull() ?: continue
            when (val outcome = parseOutcome(raw)) {
                null -> continue
                RecapOutcome.Skip -> {
                    store.commitSummary(sessionId, hash, lastEntryId)
                    logInfo(TAG, "recap skipped session=$sessionId")
                    return
                }

                is RecapOutcome.Content -> {
                    summary = outcome.text
                    break
                }
            }
        }
        if (summary == null) {
            logInfo(TAG, "memory recap produced no summary")
            return
        }
        val block = buildString {
            appendLine(MemoryText.marker(sessionId))
            appendLine("## 会话纪要（$reason）")
            appendLine()
            append(summary)
        }
        store.appendDailyBlock(MemoryText.todayKey(), block)
        store.commitSummary(sessionId, hash, lastEntryId)
    }

    private fun candidateModels(): List<ModelOption> =
        providers.modelsForConfiguredProviders()
            .map { it to score(it.id) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<ModelOption, Int>> { it.second }.thenBy { it.first.providerId })
            .map { it.first }

    private fun score(modelId: String): Int {
        val id = modelId.lowercase()
        var score = 0
        if (id.contains("deepseek") && id.contains("flash")) score += 100
        if (id.contains("flash")) score += 90
        if (id.contains("haiku")) score += 80
        if (id.contains("terra")) score += 70
        return score
    }

    private fun buildPrompt(transcript: String): String = buildString {
        appendLine("你负责把 agent 的一段会话整理成长期记忆日志。下面给出会话记录，可能只是整段对话的最新部分。")
        appendLine()
        appendLine("只输出值得跨会话记住的内容，用 markdown 无序列表，最多 6 条，每条一句话。")
        appendLine("- 值得记：用户的目标、偏好、约束、决定与理由；项目或事情的进展、阻碍、下一步；答应过的事；可复用的经验教训；用户状态的重要变化（情绪、生活、环境）。")
        appendLine("- 不要记：寒暄与确认、过程性叙述、工具调用细节、一次性信息、密钥或验证码等敏感内容。")
        appendLine()
        appendLine("不要标题，不要开场白。只写对话里明确出现或可直接推断的内容，推断出的条目标注「（推断）」。")
        appendLine("如果这段对话没有值得长期保留的内容，只输出 SKIP。")
        appendLine()
        appendLine("<conversation>")
        append(transcript)
        appendLine()
        appendLine("</conversation>")
    }

    private fun parseOutcome(raw: String?): RecapOutcome? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val unfenced = trimmed
            .removePrefix("```markdown")
            .removePrefix("```md")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (unfenced.isEmpty()) return null
        val firstLine = unfenced.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        if (SKIP_PATTERN.matches(firstLine)) return RecapOutcome.Skip
        val contentLines = unfenced.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "---" && !it.startsWith("#") }
        if (contentLines.isEmpty()) return null
        if (contentLines.all { it.trimStart('-', '*', ' ').trim() in EMPTY_MARKERS }) return null
        return RecapOutcome.Content(unfenced)
    }

    private fun deltaEntries(entries: List<SessionEntry>, afterEntryId: String?): List<SessionEntry> {
        if (afterEntryId == null) return entries
        val index = entries.indexOfFirst { it.id == afterEntryId }
        if (index < 0) return emptyList()
        return entries.subList(index + 1, entries.size)
    }

    private fun buildTranscript(entries: List<SessionEntry>, minMessages: Int): String? {
        val lines = ArrayList<String>()
        for (entry in entries) {
            if (entry !is SessionMessageEntry) continue
            when (val message = entry.message) {
                is UserMessage -> {
                    val text = when (val content = message.content) {
                        is PlainTextContent -> content.text
                        is BlockContent -> content.blocks.filterIsInstance<TextContent>().joinToString("\n") { it.text }
                    }.trim()
                    if (text.isEmpty()) continue
                    lines += "User: $text"
                }

                is dev.pi.ai.AssistantMessage -> {
                    val text = message.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }.trim()
                    if (text.isEmpty()) continue
                    lines += "Assistant: $text"
                }

                else -> Unit
            }
        }
        if (lines.size < minMessages) return null
        return MemoryText.truncate(lines.joinToString("\n\n"), MAX_TRANSCRIPT_CHARS, MemoryTruncation.END)
    }

    private fun buildTranscript(messages: List<UiAssistantMessage>): String? {
        val lines = messages.mapNotNull { message ->
            val text = message.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            when (message.role) {
                AssistantRole.USER -> "User: $text"
                AssistantRole.ASSISTANT -> "Assistant: $text"
            }
        }
        if (lines.size < MIN_MESSAGES) return null
        return MemoryText.truncate(lines.joinToString("\n\n"), MAX_TRANSCRIPT_CHARS, MemoryTruncation.END)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
