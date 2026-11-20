package io.github.whywood.superpi.runtime.memory

import io.github.whywood.superpi.sdk.ai.BlockContent
import io.github.whywood.superpi.sdk.ai.PlainTextContent
import io.github.whywood.superpi.sdk.ai.TextContent
import io.github.whywood.superpi.sdk.ai.UserMessage
import io.github.whywood.superpi.sdk.coding.session.SessionEntry
import io.github.whywood.superpi.sdk.coding.session.SessionMessageEntry
import io.github.whywood.superpi.runtime.AssistantMessage as UiAssistantMessage
import io.github.whywood.superpi.runtime.AssistantRole
import io.github.whywood.superpi.runtime.ModelOption
import io.github.whywood.superpi.runtime.ProviderCatalog
import io.github.whywood.superpi.runtime.logInfo
import io.github.whywood.superpi.runtime.logWarn

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "MemoryRecap"
internal const val RECAP_MIN_MESSAGES = 4
internal const val RECAP_MIN_INCREMENTAL_MESSAGES = 2
internal const val RECAP_MAX_TRANSCRIPT_CHARS = 80_000
internal const val RECAP_REASON_AUTO = "auto"

private val SKIP_PATTERN = Regex("^skip[\\s.!。]*$", RegexOption.IGNORE_CASE)
private val EMPTY_MARKERS = setOf(
    "none", "nothing", "n/a", "no content", "nothing to record",
    "(none)", "(nothing)", "(n/a)", "(no content)",
)

internal enum class RecapOutcome {
    WRITTEN,
    SKIPPED,
    DUPLICATE,
    FAILED,
}

private sealed interface ParsedRecap {
    data object Skip : ParsedRecap

    data class Content(val text: String) : ParsedRecap
}

class MemoryRecap(
    private val store: MemoryStore,
    private val providers: ProviderCatalog,
    private val scope: CoroutineScope,
) {
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()

    fun launchSessionClose(sessionId: String) {
        val captured = store.takeCaptured(sessionId) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                summarizeCaptured(captured)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logWarn(TAG, "session recap failed", error)
            }
        }
    }

    fun discard(sessionId: String) {
        store.takeCaptured(sessionId)
    }

    fun launchRetryPending() {
        scope.launch(Dispatchers.IO) {
            try {
                retryPending()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logWarn(TAG, "pending recap retry failed", error)
            }
        }
    }

    suspend fun summarizeMessages(sessionId: String, messages: List<UiAssistantMessage>, reason: String) {
        val transcript = buildTranscript(messages) ?: return
        lockFor(sessionId).withLock {
            summarizeLocked(sessionId, null, transcript, null, reason)
        }
    }

    private suspend fun retryPending() {
        for (pending in store.pendingRecaps()) {
            val outcome = lockFor(pending.sessionId).withLock {
                summarizeLocked(
                    pending.sessionId,
                    pending.sessionFile,
                    pending.transcript,
                    pending.lastEntryId,
                    pending.reason,
                )
            }
            if (outcome == RecapOutcome.FAILED) return
            store.deletePendingRecap(pending.sessionId)
        }
    }

    private suspend fun summarizeCaptured(captured: CapturedTranscript) {
        lockFor(captured.sessionId).withLock {
            val after = store.lastSummarizedEntryId(captured.sessionId)
            val delta = deltaEntries(captured.entries, after)
            if (delta.isEmpty()) return@withLock
            val minMessages = if (after == null) RECAP_MIN_MESSAGES else RECAP_MIN_INCREMENTAL_MESSAGES
            val transcript = buildTranscript(delta, minMessages) ?: return@withLock
            val lastMessageId = delta.lastOrNull { it is SessionMessageEntry }?.id
            val outcome = summarizeLocked(captured.sessionId, captured.sessionFile, transcript, lastMessageId, RECAP_REASON_AUTO)
            if (outcome == RecapOutcome.FAILED) {
                store.savePendingRecap(
                    sessionId = captured.sessionId,
                    sessionFile = captured.sessionFile,
                    transcript = transcript,
                    lastEntryId = lastMessageId,
                    reason = RECAP_REASON_AUTO,
                )
            }
        }
    }

    private fun lockFor(sessionId: String): Mutex = sessionLocks.getOrPut(sessionId) { Mutex() }

    private suspend fun summarizeLocked(
        sessionId: String,
        sessionFile: String?,
        transcript: String,
        lastEntryId: String?,
        reason: String,
    ): RecapOutcome = withContext(Dispatchers.IO) {
        if (sessionFile != null && !File(sessionFile).isFile) {
            logInfo(TAG, "skip recap for deleted session $sessionId")
            return@withContext RecapOutcome.SKIPPED
        }
        val hash = sha256(transcript)
        if (store.isSummaryDuplicate(sessionId, hash)) return@withContext RecapOutcome.DUPLICATE
        val raw = completeWithCheapModel(providers, buildPrompt(transcript))
            ?: return@withContext RecapOutcome.FAILED
        when (val parsed = parseOutcome(raw)) {
            null -> {
                store.commitSummary(sessionId, hash, lastEntryId)
                RecapOutcome.SKIPPED
            }

            ParsedRecap.Skip -> {
                store.commitSummary(sessionId, hash, lastEntryId)
                RecapOutcome.SKIPPED
            }

            is ParsedRecap.Content -> {
                val block = buildString {
                    appendLine(MemoryText.marker(sessionId))
                    appendLine("## Session recap ($reason)")
                    appendLine()
                    append(parsed.text)
                }
                store.appendDayBlock(MemoryText.todayKey(), block)
                store.commitSummary(sessionId, hash, lastEntryId)
                RecapOutcome.WRITTEN
            }
        }
    }

    private fun buildPrompt(transcript: String): String = buildString {
        appendLine("You turn one stretch of an agent conversation into a long-term memory log. The conversation below may be only the latest part of a longer session.")
        appendLine()
        appendLine("Write only what is worth remembering across sessions, as a markdown unordered list with at most 6 items, one sentence each.")
        appendLine("- Keep: the user's goals, preferences, constraints, decisions and the reasons behind them; progress, blockers and next steps in plans and life arrangements; promises made; reusable lessons; important changes in the user's state (mood, life, environment).")
        appendLine("- Leave out: greetings and acknowledgements, narrating the process, tool-call details, one-off information, secrets such as keys or verification codes.")
        appendLine()
        appendLine("No heading, no preamble. Write only what appears explicitly in the conversation or follows directly from it; mark inferred items, for example with (inferred) or the equivalent in the user's language.")
        appendLine("Write the memory in the language the user used in the conversation. Keep proper nouns such as names, project names, product names and technical terms exactly as the user wrote them; do not translate the memory into English just because these instructions are in English.")
        appendLine("If this conversation has nothing worth keeping long term, output only SKIP.")
        appendLine()
        appendLine("<conversation>")
        append(transcript)
        appendLine()
        appendLine("</conversation>")
    }

    private fun parseOutcome(raw: String?): ParsedRecap? {
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
        if (SKIP_PATTERN.matches(firstLine)) return ParsedRecap.Skip
        val contentLines = unfenced.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "---" && !it.startsWith("#") }
        if (contentLines.isEmpty()) return null
        if (contentLines.all { isEmptyMarker(it) }) return null
        return ParsedRecap.Content(unfenced)
    }

    private fun isEmptyMarker(line: String): Boolean =
        line.trimStart('-', '*', ' ').trim().trimEnd('.', '!', '。').lowercase() in EMPTY_MARKERS

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

                is io.github.whywood.superpi.sdk.ai.AssistantMessage -> {
                    val text = message.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }.trim()
                    if (text.isEmpty()) continue
                    lines += "Assistant: $text"
                }

                else -> Unit
            }
        }
        if (lines.size < minMessages) return null
        return MemoryText.truncate(lines.joinToString("\n\n"), RECAP_MAX_TRANSCRIPT_CHARS, MemoryTruncation.END)
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
        if (lines.size < RECAP_MIN_MESSAGES) return null
        return MemoryText.truncate(lines.joinToString("\n\n"), RECAP_MAX_TRANSCRIPT_CHARS, MemoryTruncation.END)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}

suspend fun completeWithCheapModel(providers: ProviderCatalog, prompt: String): String? {
    val candidates = providers.modelsForConfiguredProviders()
        .map { model -> model to modelPreference(model.id) }
        .sortedWith(
            compareByDescending<Pair<ModelOption, Int>> { it.second }
                .thenBy { it.first.providerId }
                .thenBy { it.first.id },
        )
        .map { it.first }
    for (candidate in candidates) {
        val raw = runCatching { providers.complete(candidate.providerId, candidate.id, prompt) }.getOrNull()
        if (!raw.isNullOrBlank()) return raw
    }
    return null
}

internal fun modelPreference(modelId: String): Int {
    val id = modelId.lowercase()
    var score = 0
    if (id.contains("deepseek") && id.contains("flash")) score += 100
    if (id.contains("flash")) score += 90
    if (id.contains("haiku")) score += 80
    if (id.contains("terra")) score += 70
    return score
}
