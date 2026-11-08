package io.github.whywood.deepdeep.runtime.memory

import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
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

private val writeTargets = listOf("profile", "context", "cookbook", "today")
private val writeModes = listOf("append", "replace_section", "overwrite")
private val readTargets = listOf("profile", "context", "cookbook", "today", "month", "year")

private val writeParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("target", stringProperty(
            "Where to write. profile: who the user is, who you are, and how you work together. " +
                "context: what is current or planned — ongoing threads, commitments, reminders, near-term state. " +
                "cookbook: reusable methods and lessons learned. today: raw record of what happened today.",
            writeTargets,
        ))
        put("content", stringProperty(
            "Markdown to save. Keep entries short. Understanding you infer must cite its evidence and any expiry condition.",
        ))
        put("mode", stringProperty(
            "How to write. append (default) adds new content. replace_section replaces one section body and requires section. " +
                "overwrite rewrites the whole curated file and should be used only after memory_read.",
            writeModes,
        ))
        put("section", stringProperty(
            "Heading to replace or to append under, such as Workout plan or Recent threads; leading # is optional.",
        ))
        put("date", stringProperty("Day for target=today as yyyy-MM-dd; defaults to today."))
    })
    put("required", buildJsonArray { add(JsonPrimitive("target")); add(JsonPrimitive("content")) })
}

private val readParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("target", stringProperty(
            "Which memory to read: profile, context, cookbook, today, month or year.",
            readTargets,
        ))
        put("section", stringProperty("Optional heading to show only that section (curated zones)."))
        put("date", stringProperty("Day for today, month for month (yyyy-MM or any day in it), year for year."))
    })
    put("required", buildJsonArray { add(JsonPrimitive("target")) })
}

private val searchParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("query", stringProperty("Keywords to search for. Chinese and English both work; short distinctive words are best."))
        put("limit", integerProperty("Maximum number of results to return."))
    })
    put("required", buildJsonArray { add(JsonPrimitive("query")) })
}

private val statusParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {})
}

fun memoryTools(store: MemoryStore, sessionId: () -> String?): List<CodingTool> = listOf(
    CodingTool(
        name = "memory_write",
        label = "Save memory",
        description = "Save something into long-term memory. The app owns the level-1 headings of the memory files; " +
            "write any structure you like underneath them.",
        parameters = writeParameters,
        promptSnippet = "Save durable memory",
        promptGuidelines = listOf(
            "Use memory_write for durable understanding: preferences, current threads and commitments, and reusable lessons. " +
                "Keep the raw log of events in today.",
            "Inferred understanding must cite its evidence and any expiry condition in the entry itself.",
            "Before replace_section or overwrite, call memory_read first and keep unrelated content.",
            "When the user says to remember something, write it immediately.",
        ),
    ) { _, args, signal, _ ->
        signal?.throwIfAborted()
        withContext(Dispatchers.IO) {
            try {
                val target = args.stringArg("target").ifBlank { "context" }
                val mode = MemoryWriteMode.fromWire(args.stringArg("mode")) ?: MemoryWriteMode.APPEND
                textResult(
                    store.write(
                        target = target,
                        content = args.stringArg("content"),
                        mode = mode,
                        section = args.stringArg("section").takeIf { it.isNotBlank() },
                        sessionId = sessionId(),
                        date = args.stringArg("date").takeIf { it.isNotBlank() },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult("memory_write failed: ${error.message}", isError = true)
            }
        }
    },
    CodingTool(
        name = "memory_read",
        label = "Read memory",
        description = "Read a memory zone or a time slice of the memory. Read before changing memory so nothing unrelated is lost.",
        parameters = readParameters,
        promptSnippet = "Read memory before rewriting it",
        promptGuidelines = listOf(
            "Read before replace_section or overwrite.",
            "Use month or year to look back at older summaries; today is the raw log.",
        ),
    ) { _, args, signal, _ ->
        signal?.throwIfAborted()
        withContext(Dispatchers.IO) {
            try {
                textResult(
                    store.read(
                        target = args.stringArg("target").ifBlank { "profile" },
                        section = args.stringArg("section").takeIf { it.isNotBlank() },
                        date = args.stringArg("date").takeIf { it.isNotBlank() },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult("memory_read failed: ${error.message}", isError = true)
            }
        }
    },
    CodingTool(
        name = "memory_search",
        label = "Search memory",
        description = "Search long-term memory across profile, context, cookbook and the time files. " +
            "Local keyword search, works offline.",
        parameters = searchParameters,
        promptSnippet = "Search long-term memory",
        promptGuidelines = listOf(
            "Search memory before assuming past context; if nothing matches, say so instead of inventing a memory.",
            "Prefer short, distinctive keywords over long sentences.",
        ),
    ) { _, args, signal, _ ->
        signal?.throwIfAborted()
        withContext(Dispatchers.IO) {
            try {
                val query = args.stringArg("query")
                val limit = args.intArg("limit")?.coerceIn(1, MAX_SEARCH_LIMIT) ?: DEFAULT_SEARCH_LIMIT
                textResult(formatSearchResult(store.search(query, limit)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult("memory_search failed: ${error.message}", isError = true)
            }
        }
    },
    CodingTool(
        name = "memory_status",
        label = "Memory status",
        description = "Show long-term memory statistics: file sizes, day count, search index and snapshot state.",
        parameters = statusParameters,
        promptSnippet = "Inspect memory status",
        promptGuidelines = listOf(
            "Use memory_status when the user asks what you remember or how much memory exists.",
        ),
    ) { _, _, signal, _ ->
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

private fun formatSearchResult(outcome: MemorySearchOutcome): String {
    if (outcome.hits.isEmpty()) {
        return "No matches (${outcome.chunks} chunks, ${outcome.elapsedMs} ms)."
    }
    val text = buildString {
        outcome.hits.forEachIndexed { index, hit ->
            appendLine("### Result ${index + 1}")
            val section = hit.section?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            appendLine("**File:** ${hit.fileName}$section")
            appendLine("**Score:** ${String.format(Locale.US, "%.3f", hit.score)}")
            appendLine()
            appendLine(hit.snippet.trim())
            appendLine()
        }
        append("(${outcome.hits.size} result(s), ${outcome.chunks} chunks, ${outcome.elapsedMs} ms)")
    }
    return text.trim()
}

private fun formatStatus(status: MemoryStatus): String = buildString {
    appendLine("Data directory: ${status.rootDir}")
    appendLine()
    appendLine("profile.md: ${status.profileChars} chars")
    appendLine("context.md: ${status.contextChars} chars")
    appendLine("cookbook.md: ${status.cookbookChars} chars")
    appendLine("Today: ${status.todayChars} chars")
    appendLine("This month: ${status.monthChars} chars")
    appendLine("This year: ${status.yearChars} chars")
    appendLine("Diary: ${status.dayCount} days")
    appendLine()
    appendLine("Search index: ${status.indexedChunks} chunks · ${status.indexBuildMs} ms")
    val snapshot = if (status.snapshotAt == null) {
        "Snapshot: ${status.snapshotChars} chars · not built yet"
    } else {
        "Snapshot: ${status.snapshotChars} chars · ${status.snapshotAt} · ${status.snapshotReason}"
    }
    appendLine(snapshot)
    if (status.dirty) appendLine("(Snapshot refresh pending)")
    appendLine("Size: ${MemoryText.formatBytes(status.totalBytes)}")
}

private fun JsonObject.stringArg(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.intArg(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
