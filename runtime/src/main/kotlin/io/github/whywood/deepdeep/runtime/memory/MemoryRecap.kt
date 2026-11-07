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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MemoryRecap"
private const val MIN_MESSAGES = 4
private const val MAX_TRANSCRIPT_CHARS = 80_000

class MemoryRecap(
    private val store: MemoryStore,
    private val providers: ProviderCatalog,
    private val scope: CoroutineScope,
) {
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

    suspend fun summarizeMessages(sessionId: String, messages: List<UiAssistantMessage>, reason: String) {
        val transcript = buildTranscriptFromMessages(messages) ?: return
        withContext(Dispatchers.IO) { summarize(sessionId, null, transcript, reason) }
    }

    private suspend fun summarizeCaptured(captured: CapturedTranscript) {
        val transcript = buildTranscript(captured.entries) ?: return
        withContext(Dispatchers.IO) { summarize(captured.sessionId, captured.sessionFile, transcript, "会话关闭") }
    }

    private suspend fun summarize(sessionId: String, sessionFile: String?, transcript: String, reason: String) {
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
            val cleaned = sanitize(raw) ?: continue
            summary = cleaned
            break
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
        store.markSummary(sessionId, hash)
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
        appendLine("下面是一次会话记录。请提炼需要跨会话保留的信息，供写进长期记忆的当天日志。")
        appendLine()
        appendLine("只输出以下四段，不要任何额外说明：")
        appendLine("### 决定")
        appendLine("用户确定下来的方案、取舍和理由。")
        appendLine("### 结论与教训")
        appendLine("重要结论、失败原因、可复用的方法。")
        appendLine("### 未完成")
        appendLine("悬而未决的问题、下一步、答应过的事。")
        appendLine("### 用户情况")
        appendLine("对话中透露的稳定偏好、约束、人物或环境变化。")
        appendLine()
        appendLine("只写对话中明确出现或可直接推断的内容；推断出的内容标注「（推断）」。没有内容的段落写「无」。")
        appendLine("不要写寒暄、密钥、验证码或完整工具输出。")
        appendLine()
        appendLine("<conversation>")
        append(transcript)
        appendLine()
        appendLine("</conversation>")
    }

    private fun buildTranscript(entries: List<SessionEntry>): String? {
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
        if (lines.size < MIN_MESSAGES) return null
        return MemoryText.truncate(lines.joinToString("\n\n"), MAX_TRANSCRIPT_CHARS, MemoryTruncation.END)
    }

    private fun buildTranscriptFromMessages(messages: List<UiAssistantMessage>): String? {
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

    private fun sanitize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val unfenced = trimmed
            .removePrefix("```markdown")
            .removePrefix("```md")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (unfenced.isEmpty()) return null
        val contentLines = unfenced.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it != "---" }
        if (contentLines.isEmpty()) return null
        val emptyMarkers = setOf("无", "无。", "（无）", "(无)", "None", "None.", "- 无", "- None.")
        if (contentLines.all { it.trimStart('-', '*', ' ').trim() in emptyMarkers }) return null
        return unfenced
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
