package io.github.whywood.deepdeep.runtime.memory

import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private const val DEFAULT_SEARCH_LIMIT = 5
private const val MAX_SEARCH_LIMIT = 25

private fun stringProperty(description: String, values: List<String>? = null): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    if (values != null) put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
}

private fun integerProperty(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private val writeParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("target", stringProperty(
            "Where to write. profile: durable facts about the user, you, how you collaborate and the environment. " +
                "context: goals, active projects and commitments. playbook: methods that work and lessons learned. " +
                "daily: raw record of what happened on a day.",
            listOf("profile", "context", "playbook", "daily"),
        ))
        put("content", stringProperty(
            "Markdown to save. Keep entries short. Understanding you infer must cite its evidence and any expiry condition.",
        ))
        put("mode", stringProperty(
            "How to write. append (default) adds new content. replace_section replaces one section body and requires section. " +
                "overwrite rewrites the whole curated file and should be used only after memory_read.",
            listOf("append", "replace_section", "overwrite"),
        ))
        put("section", stringProperty(
            "Section title for replace_section or for appending inside a section, such as 用户 or 项目名, with or without leading ##.",
        ))
        put("date", stringProperty("Daily log date as yyyy-MM-dd. Only for target=daily; defaults to today."))
    })
    put("required", buildJsonArray { add(JsonPrimitive("target")); add(JsonPrimitive("content")) })
}

private val readParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("target", stringProperty(
            "Which memory file to read: profile, context, playbook or daily.",
            listOf("profile", "context", "playbook", "daily"),
        ))
        put("section", stringProperty("When set, return only this section body."))
        put("date", stringProperty("Daily log date as yyyy-MM-dd; defaults to today."))
    })
    put("required", buildJsonArray { add(JsonPrimitive("target")) })
}

private val searchParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("query", stringProperty("Keyword query, Chinese or English."))
        put("limit", integerProperty("Maximum results from 1 to 25; default 5."))
    })
    put("required", buildJsonArray { add(JsonPrimitive("query")) })
}

private val statusParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {})
}

fun memoryTools(store: MemoryStore, sessionId: () -> String?): List<CodingTool> = listOf(
    memoryWriteTool(store, sessionId),
    memoryReadTool(store),
    memorySearchTool(store),
    memoryStatusTool(store),
)

private fun memoryWriteTool(store: MemoryStore, sessionId: () -> String?): CodingTool = CodingTool(
    name = "memory_write",
    description = "Save something to long-term memory. Curated files hold current understanding; daily holds raw events. " +
        "Use append by default, read before revising, and never drop unrelated content when replacing.",
    parameters = writeParameters,
    label = "Save memory",
    promptSnippet = "Save durable understanding or session events to long-term memory.",
    promptGuidelines = listOf(
        "Write decisions, preferences, active project state and methods that worked or failed to memory_write so they survive the session.",
        "Curated files (profile, context, playbook) hold current understanding; daily holds events. Do not copy daily events into curated files.",
        "Inferred understanding must cite its evidence and any expiry condition; keep each entry short.",
        "Before replace_section or overwrite, call memory_read first and keep unrelated content.",
        "When the user asks you to remember something, write it with memory_write before replying.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        withContext(Dispatchers.IO) {
            try {
                val target = args.stringArg("target") ?: return@withContext textResult("Missing target", isError = true)
                val content = args.stringArg("content")
                if (content.isNullOrBlank()) return@withContext textResult("Missing content", isError = true)
                val mode = MemoryWriteMode.fromWire(args.stringArg("mode") ?: "append")
                    ?: return@withContext textResult("Unknown mode", isError = true)
                textResult(store.write(target, content, mode, args.stringArg("section"), sessionId(), args.stringArg("date")))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult("memory_write failed: ${error.message}", isError = true)
            }
        }
    },
)

private fun memoryReadTool(store: MemoryStore): CodingTool = CodingTool(
    name = "memory_read",
    description = "Read a memory file, one section of it, or one day of the daily log. Use it before revising curated memory.",
    parameters = readParameters,
    label = "Read memory",
    promptSnippet = "Read long-term memory files or sections.",
    promptGuidelines = listOf(
        "Read a curated file before rewriting it so you do not drop existing content.",
        "Use memory_search for recall across everything; use memory_read to inspect the exact current text.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        withContext(Dispatchers.IO) {
            try {
                val target = args.stringArg("target") ?: return@withContext textResult("Missing target", isError = true)
                textResult(store.read(target, args.stringArg("section"), args.stringArg("date")))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult("memory_read failed: ${error.message}", isError = true)
            }
        }
    },
)

private fun memorySearchTool(store: MemoryStore): CodingTool = CodingTool(
    name = "memory_search",
    description = "Search long-term memory (profile, context, playbook and daily logs) by keyword. Use it before answering " +
        "questions about past sessions, prior decisions or user preferences, and cite what you find instead of guessing.",
    parameters = searchParameters,
    label = "Search memory",
    promptSnippet = "Search long-term memory by keyword.",
    promptGuidelines = listOf(
        "Search memory before assuming past context; if nothing matches, say so instead of inventing a memory.",
        "Prefer short, distinctive Chinese or English keywords over long sentences.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        withContext(Dispatchers.IO) {
            try {
                val query = args.stringArg("query")
                if (query.isNullOrBlank()) return@withContext textResult("Missing query", isError = true)
                val limit = args.intArg("limit")?.coerceIn(1, MAX_SEARCH_LIMIT) ?: DEFAULT_SEARCH_LIMIT
                textResult(formatSearchResult(query, store.search(query, limit)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult("memory_search failed: ${error.message}", isError = true)
            }
        }
    },
)

private fun memoryStatusTool(store: MemoryStore): CodingTool = CodingTool(
    name = "memory_status",
    description = "Show long-term memory statistics: file sizes, active projects, daily log count, search index state and snapshot state.",
    parameters = statusParameters,
    label = "Memory status",
    promptSnippet = "Show long-term memory statistics.",
    promptGuidelines = listOf("Use memory_status when the user asks what you remember or how much memory exists."),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        withContext(Dispatchers.IO) {
            try {
                textResult(formatStatus(store.status()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult("memory_status failed: ${error.message}", isError = true)
            }
        }
    },
)

private fun formatSearchResult(query: String, outcome: MemorySearchOutcome): String {
    if (outcome.hits.isEmpty()) {
        return "No matches for \"$query\" (${outcome.chunks} chunks, ${outcome.elapsedMs} ms)."
    }
    return buildString {
        outcome.hits.forEachIndexed { index, hit ->
            appendLine("### Result ${index + 1}")
            val heading = hit.section?.trim()?.trimStart('#', ' ')?.takeIf { it.isNotEmpty() }
            appendLine("**File:** ${hit.label}${if (heading != null) " · $heading" else ""}")
            appendLine("**Score:** ${String.format(Locale.US, "%.3f", hit.score)}")
            appendLine()
            appendLine(hit.snippet)
            appendLine()
        }
        append("(${outcome.hits.size} result(s), ${outcome.chunks} chunks, ${outcome.elapsedMs} ms)")
    }
}

private fun formatStatus(status: MemoryStatus): String = buildString {
    appendLine("路径：${status.rootDir}")
    appendLine("profile.md：${status.profileChars} 字符")
    appendLine("context.md：${status.contextChars} 字符 · 进行中项目 ${status.activeProjects}")
    appendLine("playbook.md：${status.playbookChars} 字符")
    appendLine("daily 日志：${status.dailyDays} 天")
    appendLine("索引：${status.indexedChunks} 个片段，构建 ${status.indexBuildMs} ms")
    val snapshot = if (status.snapshotAt == null) "尚未生成" else "${status.snapshotAt} · ${status.snapshotReason}"
    appendLine("快照：${status.snapshotChars} 字符 · $snapshot${if (status.dirty) "（待刷新）" else ""}")
    append("占用：${MemoryText.formatBytes(status.totalBytes)}")
}

private fun JsonObject.stringArg(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.intArg(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()
