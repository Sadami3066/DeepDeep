package io.github.whywood.deepdeep.runtime.memory

import io.github.whywood.deepdeep.runtime.logWarn

import dev.pi.coding.session.SessionEntry
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class MemoryWriteMode {
    APPEND,
    REPLACE_SECTION,
    OVERWRITE,
    ;

    companion object {
        fun fromWire(value: String?): MemoryWriteMode? = when (value?.trim()?.lowercase()) {
            null, "", "append" -> APPEND
            "replace_section", "replace-section" -> REPLACE_SECTION
            "overwrite" -> OVERWRITE
            else -> null
        }
    }
}

internal data class MemoryDoc(
    val label: String,
    val fileName: String,
    val section: String?,
    val text: String,
)

internal data class MemoryHit(
    val label: String,
    val fileName: String,
    val section: String?,
    val score: Double,
    val snippet: String,
)

internal data class MemorySearchOutcome(
    val hits: List<MemoryHit>,
    val chunks: Int,
    val elapsedMs: Long,
)

internal data class CapturedTranscript(
    val sessionId: String,
    val sessionFile: String?,
    val entries: List<SessionEntry>,
)

data class MemoryStatus(
    val rootDir: String,
    val profileChars: Int,
    val contextChars: Int,
    val playbookChars: Int,
    val activeProjects: Int,
    val dailyDays: Int,
    val totalBytes: Long,
    val indexedChunks: Int,
    val indexBuildMs: Long,
    val snapshotChars: Int,
    val snapshotAt: String?,
    val snapshotReason: String,
    val dirty: Boolean,
)

class MemoryStore(private val rootDir: File) {
    private val mutex = Mutex()
    private val captured = ConcurrentHashMap<String, CapturedTranscript>()

    private val profileFile = File(rootDir, "profile.md")
    private val contextFile = File(rootDir, "context.md")
    private val playbookFile = File(rootDir, "playbook.md")
    private val dailyDir = File(rootDir, "daily")
    private val summaryLogFile = File(rootDir, "summaries.log")

    private var cacheValid = false
    private var cachedSection: String? = null
    private var cachedChars = 0
    private var cachedAt: String? = null
    private var cachedReason = "init"
    private var cachedDay = ""
    @Volatile
    private var dirty = true
    private var corpusCache: List<MemoryDoc>? = null

    suspend fun ensure() = withContext(Dispatchers.IO) {
        rootDir.mkdirs()
        dailyDir.mkdirs()
        listOf(profileFile, contextFile, playbookFile).forEach { file ->
            if (!file.exists()) file.createNewFile()
        }
    }

    suspend fun section(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val today = MemoryText.todayKey()
            if (cacheValid && !dirty && cachedDay == today) return@withLock cachedSection
            buildSectionLocked(if (cacheValid) "refresh" else "session_start")
        }
    }

    suspend fun refresh(reason: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock { buildSectionLocked(reason) }
    }

    fun markDirty() {
        dirty = true
    }

    internal fun capture(sessionId: String, sessionFile: String?, entries: List<SessionEntry>?) {
        if (entries.isNullOrEmpty()) return
        captured[sessionId] = CapturedTranscript(sessionId, sessionFile, entries)
    }

    internal fun takeCaptured(sessionId: String): CapturedTranscript? = captured.remove(sessionId)

    suspend fun onCompaction(sessionId: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val day = MemoryText.todayKey()
            val context = contextFile.readTextOrEmpty()
            val focus = MemorySections.body(context, "近期焦点").orEmpty().trim()
            val tail = readDailyLocked(day).lines().takeLast(15).joinToString("\n").trim()
            if (focus.isBlank() && tail.isBlank()) return@withLock
            val block = buildString {
                append(MemoryText.marker(sessionId))
                append("\n## Session Handoff\n")
                if (focus.isNotBlank()) append("\n**近期焦点**\n").append(focus).append('\n')
                if (tail.isNotBlank()) append("\n**今天日志末尾**\n").append(tail).append('\n')
            }
            appendDailyLocked(day, block.trimEnd())
            buildSectionLocked("compaction")
        }
    }

    internal suspend fun write(
        target: String,
        content: String,
        mode: MemoryWriteMode,
        section: String?,
        sessionId: String?,
        date: String?,
    ): String = withContext(Dispatchers.IO) {
        val body = MemoryText.normalize(content)
        mutex.withLock {
            when (target.trim().lowercase()) {
                "daily" -> {
                    if (mode != MemoryWriteMode.APPEND) {
                        return@withLock "daily 只支持 append 模式。"
                    }
                    if (body.isEmpty()) return@withLock "内容为空，未写入。"
                    val day = normalizedDay(date)
                    appendDailyLocked(day, MemoryText.marker(sessionId) + "\n" + body)
                    invalidateLocked()
                    "已追加到 daily/$day.md：\n\n" + MemoryText.preview(body)
                }

                "profile", "context", "playbook" -> {
                    val file = fileFor(target)
                    val current = file.readTextOrEmpty()
                    val updated = when (mode) {
                        MemoryWriteMode.APPEND -> {
                            val block = MemoryText.marker(sessionId) + "\n" + body
                            if (section.isNullOrBlank()) {
                                (current.trimEnd() + "\n\n" + block).trimEnd() + "\n"
                            } else {
                                MemorySections.append(current, section, block)
                            }
                        }

                        MemoryWriteMode.REPLACE_SECTION -> {
                            val title = section?.takeIf { it.isNotBlank() }
                                ?: return@withLock "replace_section 模式需要提供 section。"
                            MemorySections.replace(current, title, body)
                        }

                        MemoryWriteMode.OVERWRITE -> body + "\n"
                    }
                    writeAtomically(file, updated)
                    dirty = true
                    invalidateLocked()
                    val action = when (mode) {
                        MemoryWriteMode.APPEND -> if (section.isNullOrBlank()) "append" else "append 到「$section」"
                        MemoryWriteMode.REPLACE_SECTION -> "replace_section「$section」"
                        MemoryWriteMode.OVERWRITE -> "overwrite"
                    }
                    "已写入 ${file.name}（$action）：\n\n" + MemoryText.preview(body)
                }

                else -> "未知 target：$target（可用 profile / context / playbook / daily）"
            }
        }
    }

    suspend fun read(target: String, section: String?, date: String?): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            when (target.trim().lowercase()) {
                "daily" -> {
                    val day = normalizedDay(date)
                    readDailyLocked(day).trim().ifEmpty { "daily/$day.md 还没有记录。" }
                }

                "profile", "context", "playbook" -> {
                    val file = fileFor(target)
                    val whole = file.readTextOrEmpty().trim()
                    if (whole.isEmpty()) return@withLock "${file.name} 目前为空。"
                    if (section.isNullOrBlank()) return@withLock whole
                    MemorySections.body(whole, section)?.takeIf { it.isNotBlank() }
                        ?: "${file.name} 里找不到 section「$section」。"
                }

                else -> "未知 target：$target（可用 profile / context / playbook / daily）"
            }
        }
    }

    internal suspend fun search(query: String, limit: Int): MemorySearchOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            MemorySearch.search(corpusLocked(), query, limit)
        }
    }

    suspend fun status(): MemoryStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            val docs = corpusLocked()
            val measured = MemorySearch.measure(docs)
            val context = contextFile.readTextOrEmpty()
            val projects = MemorySections.body(context, "进行中的项目").orEmpty()
                .lines().count { it.trimStart().startsWith("### ") }
            MemoryStatus(
                rootDir = rootDir.absolutePath,
                profileChars = profileFile.readTextOrEmpty().length,
                contextChars = context.length,
                playbookChars = playbookFile.readTextOrEmpty().length,
                activeProjects = projects,
                dailyDays = dailyDatesLocked().size,
                totalBytes = rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                indexedChunks = measured.chunks,
                indexBuildMs = measured.elapsedMs,
                snapshotChars = if (cacheValid) cachedChars else 0,
                snapshotAt = cachedAt,
                snapshotReason = cachedReason,
                dirty = dirty,
            )
        }
    }

    suspend fun rebuildIndex(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            corpusCache = null
            MemorySearch.measure(corpusLocked()).chunks
        }
    }

    internal suspend fun appendDailyBlock(day: String, block: String) = withContext(Dispatchers.IO) {
        mutex.withLock { appendDailyLocked(normalizedDay(day), block) }
    }

    internal fun isSummaryDuplicate(sessionId: String, hash: String): Boolean =
        runCatching { summaryLogFile.isFile && summaryLogFile.readLines().any { it.trim() == "$sessionId|$hash" } }
            .getOrDefault(false)

    internal fun markSummary(sessionId: String, hash: String) {
        runCatching {
            if (!summaryLogFile.exists()) summaryLogFile.createNewFile()
            summaryLogFile.appendText("$sessionId|$hash\n")
        }
    }

    fun fileForTarget(target: String): File = fileFor(target)

    private fun fileFor(target: String): File = when (target.trim().lowercase()) {
        "profile" -> profileFile
        "context" -> contextFile
        else -> playbookFile
    }

    private fun normalizedDay(date: String?): String =
        date?.trim()?.takeIf { MemoryText.isValidDay(it) } ?: MemoryText.todayKey()

    private fun buildSectionLocked(reason: String): String? {
        val profile = profileFile.readTextOrEmpty()
        val context = contextFile.readTextOrEmpty()
        val playbook = playbookFile.readTextOrEmpty()
        val today = MemoryText.todayKey()
        val yesterday = MemoryText.yesterdayKey()
        val takenAt = MemoryText.nowStamp()
        val section = runCatching {
            renderMemorySection(profile, context, playbook, readDailyLocked(today), readDailyLocked(yesterday), takenAt)
        }.onFailure { logWarn("DeepDeepMemory", "render memory section failed", it) }.getOrNull()
        cachedSection = section
        cachedChars = section?.length ?: 0
        cachedAt = takenAt
        cachedReason = reason
        cachedDay = today
        cacheValid = true
        dirty = false
        return section
    }

    private fun invalidateLocked() {
        corpusCache = null
    }

    private fun corpusLocked(): List<MemoryDoc> {
        corpusCache?.let { return it }
        val docs = ArrayList<MemoryDoc>()
        docs += MemorySearch.chunk("profile.md", profileFile.readTextOrEmpty())
        docs += MemorySearch.chunk("context.md", contextFile.readTextOrEmpty())
        docs += MemorySearch.chunk("playbook.md", playbookFile.readTextOrEmpty())
        for (day in dailyDatesLocked()) {
            docs += MemorySearch.dailyChunk(day, readDailyLocked(day))
        }
        corpusCache = docs
        return docs
    }

    private fun dailyDatesLocked(): List<String> =
        dailyDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".md") }
            .map { it.name.removeSuffix(".md") }
            .filter { MemoryText.isValidDay(it) }
            .sorted()

    private fun readDailyLocked(day: String): String = dailyFile(day).readTextOrEmpty()

    private fun appendDailyLocked(day: String, block: String) {
        if (block.isBlank()) return
        dailyDir.mkdirs()
        val file = dailyFile(day)
        val current = file.readTextOrEmpty().trimEnd()
        val updated = if (current.isEmpty()) block.trimEnd() + "\n" else current + "\n\n" + block.trimEnd() + "\n"
        writeAtomically(file, updated)
    }

    private fun dailyFile(day: String): File = File(dailyDir, "$day.md")

    private fun File.readTextOrEmpty(): String = runCatching { if (isFile) readText() else "" }.getOrDefault("")

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                file.writeText(text)
                temp.delete()
            }
        }
    }
}
