package io.github.whywood.superpi.runtime.memory

import io.github.whywood.superpi.runtime.MemoryBackend
import io.github.whywood.superpi.runtime.MemoryWriteMode
import io.github.whywood.superpi.runtime.MemoryWriteOutcome
import io.github.whywood.superpi.runtime.logWarn
import io.github.whywood.superpi.sdk.coding.session.SessionEntry

import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

internal data class PendingRecap(
    val sessionId: String,
    val sessionFile: String?,
    val transcript: String,
    val lastEntryId: String?,
    val reason: String,
)

data class MemoryStatus(
    val rootDir: String,
    val profileChars: Int,
    val contextChars: Int,
    val cookbookChars: Int,
    val todayChars: Int,
    val monthChars: Int,
    val yearChars: Int,
    val dayCount: Int,
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

    @Volatile
    private var backend: MemoryBackend? = null

    fun attachBackend(value: MemoryBackend?) {
        backend = value
    }
    private val captured = ConcurrentHashMap<String, CapturedTranscript>()

    private val profileFile = File(rootDir, "profile.md")
    private val contextFile = File(rootDir, "context.md")
    private val cookbookFile = File(rootDir, "cookbook.md")
    private val dailyDir = File(rootDir, "daily")
    private val pendingDir = File(rootDir, "pending")
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
        mutex.withLock { ensureLocked() }
    }

    suspend fun section(): String? {
        refreshShared()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val today = MemoryText.todayKey()
                if (cacheValid && !dirty && cachedDay == today) return@withLock cachedSection
                buildSectionLocked(if (cacheValid) "refresh" else "session_start")
            }
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

    internal suspend fun write(
        target: String,
        content: String,
        mode: MemoryWriteMode,
        section: String?,
        sessionId: String?,
        date: String?,
    ): String = withContext(Dispatchers.IO) {
        val body = MemoryText.normalize(content)
        if (body.isBlank()) return@withContext "Nothing written: content is empty."
        val shared = backend?.let { it.writeShared(target, body, mode, section, date) }
        if (shared != null) {
            if (shared.writeLocal) {
                mutex.withLock { localWriteLocked(target, body, mode, section, sessionId, date) }
            }
            mutex.withLock { invalidateLocked() }
            return@withContext shared.message
        }
        mutex.withLock { localWriteLocked(target, body, mode, section, sessionId, date) }
    }

    private suspend fun MemoryBackend.writeShared(
        target: String,
        body: String,
        mode: MemoryWriteMode,
        section: String?,
        date: String?,
    ): SharedWrite? {
        val zone = target.trim().lowercase()
        if (zone == "today" || zone == "daily" || zone == "diary") {
            if (mode != MemoryWriteMode.APPEND) return null
            val day = normalizedDay(date)
            return when (val outcome = append(dayPath(day), null, body)) {
                MemoryWriteOutcome.Applied, MemoryWriteOutcome.Inactive ->
                    if (outcome == MemoryWriteOutcome.Inactive) null else SharedWrite(
                        "Appended to diary $day:\n\n" + MemoryText.preview(body),
                        writeLocal = false,
                    )

                MemoryWriteOutcome.Queued -> SharedWrite(
                    "Saved to diary $day on this phone; it will sync when the connection returns.",
                    writeLocal = true,
                )

                is MemoryWriteOutcome.Rejected -> throw MemoryWriteRejected(outcome.message)
            }
        }
        val file = zoneFile(target) ?: return null
        val title = zoneTitle(target) ?: return null
        val heading = section?.takeIf { it.isNotBlank() }?.trimStart('#', ' ')
        val message = when (mode) {
            MemoryWriteMode.APPEND -> submit(
                append(file.name, heading, body),
                "Wrote ${file.name} (${
                    if (heading == null) "append" else "append to \"$heading\""
                }):\n\n" + MemoryText.preview(body),
            )

            MemoryWriteMode.REPLACE_SECTION -> {
                val required = heading
                    ?: return null
                submit(
                    replaceSection(file.name, required, body),
                    "Wrote ${file.name} (replace_section \"$required\"):\n\n" + MemoryText.preview(body),
                )
            }

            MemoryWriteMode.OVERWRITE -> submit(
                overwrite(file.name, ensureZoneHeading(title, body)),
                "Wrote ${file.name} (overwrite):\n\n" + MemoryText.preview(body),
            )
        }
        if (message == null) return null
        return message
    }

    private fun submit(outcome: MemoryWriteOutcome, appliedMessage: String): SharedWrite? = when (outcome) {
        MemoryWriteOutcome.Applied -> SharedWrite(appliedMessage, writeLocal = false)
        MemoryWriteOutcome.Queued -> SharedWrite(
            "Saved on this phone; it will sync when the connection returns.",
            writeLocal = true,
        )

        MemoryWriteOutcome.Inactive -> null
        is MemoryWriteOutcome.Rejected -> throw MemoryWriteRejected(outcome.message)
    }

    private fun localWriteLocked(
        target: String,
        body: String,
        mode: MemoryWriteMode,
        section: String?,
        sessionId: String?,
        date: String?,
    ): String = when (target.trim().lowercase()) {
        "today", "daily", "diary" -> {
            if (mode != MemoryWriteMode.APPEND) return "Diary only supports append mode."
            val day = normalizedDay(date)
            appendDayLocked(day, MemoryText.marker(sessionId) + "\n" + body)
            invalidateLocked()
            "Appended to diary $day:\n\n" + MemoryText.preview(body)
        }

        "profile", "context", "cookbook" -> {
            val file = zoneFile(target) ?: return unknownTarget(target)
            val title = zoneTitle(target) ?: return unknownTarget(target)
            val current = file.readTextOrEmpty()
            val updated = when (mode) {
                MemoryWriteMode.APPEND -> appendZone(current, title, body, section)
                MemoryWriteMode.REPLACE_SECTION -> {
                    val heading = section?.takeIf { it.isNotBlank() }
                        ?: return "replace_section requires a section (the heading of the part to replace)."
                    if (current.isBlank()) {
                        "# $title\n\n## ${heading.trimStart('#', ' ')}\n\n$body\n"
                    } else {
                        MemorySections.replace(current, heading, body)
                    }
                }

                MemoryWriteMode.OVERWRITE -> ensureZoneHeading(title, body)
            }
            writeAtomically(file, updated)
            dirty = true
            invalidateLocked()
            val action = when (mode) {
                MemoryWriteMode.APPEND -> if (section.isNullOrBlank()) "append" else "append to \"$section\""
                MemoryWriteMode.REPLACE_SECTION -> "replace_section \"$section\""
                MemoryWriteMode.OVERWRITE -> "overwrite"
            }
            "Wrote ${file.name} ($action):\n\n" + MemoryText.preview(body)
        }

        else -> unknownTarget(target)
    }

    suspend fun read(target: String, section: String?, date: String?): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            when (target.trim().lowercase()) {
                "today", "daily", "diary" -> {
                    val day = normalizedDay(date)
                    readDayLocked(day).trim().ifEmpty { "No diary entries for $day." }
                }

                "month" -> {
                    val month = normalizedMonth(date)
                    readMonthLocked(month).trim().ifEmpty { "No summary for $month yet." }
                }

                "year" -> {
                    val year = normalizedYear(date)
                    readYearLocked(year).trim().ifEmpty { "No summary for $year yet." }
                }

                "profile", "context", "cookbook" -> {
                    val file = zoneFile(target) ?: return@withLock unknownTarget(target)
                    val whole = file.readTextOrEmpty().trim()
                    if (whole.isEmpty()) return@withLock "${file.name} is empty."
                    if (section.isNullOrBlank()) return@withLock whole
                    MemorySections.body(whole, section)?.takeIf { it.isNotBlank() }
                        ?: "No section \"$section\" in ${file.name}."
                }

                else -> unknownTarget(target)
            }
        }
    }

    suspend fun recentDayKeys(limit: Int = 7): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock { dayKeysLocked().sortedDescending().take(limit.coerceIn(1, 60)) }
    }

    suspend fun fileText(target: String, date: String? = null): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            when (target.trim().lowercase()) {
                "today", "daily", "diary" -> readDayLocked(normalizedDay(date)).trim().takeIf { it.isNotEmpty() }
                "month" -> readMonthLocked(normalizedMonth(date)).trim().takeIf { it.isNotEmpty() }
                "year" -> readYearLocked(normalizedYear(date)).trim().takeIf { it.isNotEmpty() }
                "profile", "context", "cookbook" -> zoneFile(target)?.readTextOrEmpty()?.trim()?.takeIf { it.isNotEmpty() }
                else -> null
            }
        }
    }

    internal suspend fun search(query: String, limit: Int): MemorySearchOutcome = withContext(Dispatchers.IO) {
        mutex.withLock { MemorySearch.search(corpusLocked(), query, limit) }
    }

    suspend fun status(): MemoryStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            val docs = corpusLocked()
            val measured = MemorySearch.measure(docs)
            val today = MemoryText.todayKey()
            val month = MemoryText.monthKey()
            val year = MemoryText.yearKey()
            MemoryStatus(
                rootDir = rootDir.absolutePath,
                profileChars = profileFile.readTextOrEmpty().length,
                contextChars = contextFile.readTextOrEmpty().length,
                cookbookChars = cookbookFile.readTextOrEmpty().length,
                todayChars = readDayLocked(today).length,
                monthChars = readMonthLocked(month).length,
                yearChars = readYearLocked(year).length,
                dayCount = dayKeysLocked().size,
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

    internal suspend fun appendDayBlock(day: String, block: String) = withContext(Dispatchers.IO) {
        val shared = backend
        if (shared != null) {
            val written = shared.appendBlock(dayPath(normalizedDay(day)), null, block)
            if (written) {
                mutex.withLock { invalidateLocked() }
                return@withContext
            }
        }
        mutex.withLock { appendDayLocked(normalizedDay(day), block) }
    }

    internal suspend fun readDay(day: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock { readDayLocked(day).takeIf { it.isNotBlank() } }
    }

    internal suspend fun readMonth(month: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock { readMonthLocked(month).takeIf { it.isNotBlank() } }
    }

    internal suspend fun readYear(year: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock { readYearLocked(year).takeIf { it.isNotBlank() } }
    }

    internal suspend fun dayKeys(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock { dayKeysLocked() }
    }

    internal suspend fun monthKeys(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            dailyDir.walkTopDown()
                .filter { it.isFile && it.name.matches(MONTH_FILE) }
                .map { it.name.removeSuffix(".md") }
                .sorted()
                .toList()
        }
    }

    internal fun monthHasDay(dayKey: String): Boolean = runCatching {
        val monthFile = monthFile(dayKey)
        monthFile.isFile && Regex("(?m)^##\\s+${Regex.escape(dayKey)}\\s*$").containsMatchIn(monthFile.readText())
    }.getOrDefault(false)

    internal fun yearHasMonth(monthKey: String): Boolean = runCatching {
        val yearFile = yearFile(monthKey)
        yearFile.isFile && Regex("(?m)^##\\s+${Regex.escape(monthKey)}\\s*$").containsMatchIn(yearFile.readText())
    }.getOrDefault(false)

    internal suspend fun appendMonthBlock(dayKey: String, block: String) = withContext(Dispatchers.IO) {
        val shared = backend
        if (shared != null) {
            val written = shared.appendBlock(monthPath(dayKey.take(7)), dayKey, block)
            if (written) {
                mutex.withLock { invalidateLocked() }
                return@withContext
            }
        }
        mutex.withLock {
            val month = dayKey.take(7)
            val file = monthFile(dayKey)
            val current = file.readTextOrEmpty().trimEnd()
            val heading = "# $month"
            val head = if (current.isBlank()) heading else current
            val updated = head + "\n\n## $dayKey\n\n" + block.trim() + "\n"
            writeAtomically(file, updated)
            invalidateLocked()
        }
    }

    internal suspend fun appendYearBlock(monthKey: String, block: String) = withContext(Dispatchers.IO) {
        val shared = backend
        if (shared != null) {
            val written = shared.appendBlock(yearPath(monthKey.take(4)), monthKey, block)
            if (written) {
                mutex.withLock { invalidateLocked() }
                return@withContext
            }
        }
        mutex.withLock {
            val year = monthKey.take(4)
            val file = yearFile(monthKey)
            val current = file.readTextOrEmpty().trimEnd()
            val heading = "# $year"
            val head = if (current.isBlank()) heading else current
            val updated = head + "\n\n## $monthKey\n\n" + block.trim() + "\n"
            writeAtomically(file, updated)
            invalidateLocked()
        }
    }

    internal suspend fun savePendingRecap(
        sessionId: String,
        sessionFile: String?,
        transcript: String,
        lastEntryId: String?,
        reason: String,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            pendingDir.mkdirs()
            val encoder = Base64.getEncoder()
            fun encode(value: String?): String =
                encoder.encodeToString((value.orEmpty()).toByteArray(Charsets.UTF_8))
            val header = listOf(encode(sessionFile), encode(lastEntryId), encode(reason)).joinToString("\n")
            pendingFile(sessionId).writeText(header + "\n\n" + transcript)
            prunePendingLocked()
        }.onFailure { logWarn(TAG, "save pending recap failed", it) }
        Unit
    }

    internal suspend fun pendingRecaps(): List<PendingRecap> = withContext(Dispatchers.IO) {
        runCatching {
            pendingDir.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".txt") }
                .sortedBy { it.lastModified() }
                .mapNotNull { file -> parsePending(file) }
        }.getOrDefault(emptyList())
    }

    internal suspend fun deletePendingRecap(sessionId: String) = withContext(Dispatchers.IO) {
        runCatching { pendingFile(sessionId).delete() }
        Unit
    }

    internal fun isSummaryDuplicate(sessionId: String, hash: String): Boolean =
        runCatching {
            summaryLogFile.isFile && summaryLogFile.readLines().any { line ->
                val parts = line.split('|')
                parts.size >= 2 && parts[0] == sessionId && parts[1] == hash
            }
        }.getOrDefault(false)

    internal fun lastSummarizedEntryId(sessionId: String): String? =
        runCatching {
            if (!summaryLogFile.isFile) return@runCatching null
            summaryLogFile.readLines()
                .map { it.split('|') }
                .filter { it.size >= 3 && it[0] == sessionId && it[2].isNotBlank() }
                .lastOrNull()
                ?.get(2)
        }.getOrNull()

    internal fun commitSummary(sessionId: String, hash: String, lastEntryId: String?) {
        runCatching {
            if (!summaryLogFile.exists()) summaryLogFile.createNewFile()
            summaryLogFile.appendText(buildString {
                append(sessionId).append('|').append(hash)
                if (!lastEntryId.isNullOrBlank()) append('|').append(lastEntryId)
                append('\n')
            })
        }
    }

    fun fileForTarget(target: String): File? = zoneFile(target)

    private fun ensureLocked() {
        rootDir.mkdirs()
        dailyDir.mkdirs()
        pendingDir.mkdirs()
        listOf(
            profileFile to ZONE_PROFILE,
            contextFile to ZONE_CONTEXT,
            cookbookFile to ZONE_COOKBOOK,
        ).forEach { (file, title) ->
            if (!file.isFile || file.readTextOrEmpty().isBlank()) {
                runCatching { file.writeText("# $title\n") }
            }
        }
    }

    private suspend fun refreshShared() {
        val shared = backend ?: return
        val changed = runCatching { shared.refreshIfStale() }.getOrDefault(false)
        if (changed) markDirty()
    }

    private suspend fun MemoryBackend.appendBlock(path: String, section: String?, block: String): Boolean =
        when (val outcome = append(path, section, block)) {
            MemoryWriteOutcome.Applied -> true
            MemoryWriteOutcome.Inactive -> false
            MemoryWriteOutcome.Queued -> false
            is MemoryWriteOutcome.Rejected -> {
                logWarn(TAG, "shared memory write rejected: ${outcome.message}")
                false
            }
        }

    private fun dayPath(day: String): String {
        val date = runCatching { LocalDate.parse(day) }.getOrElse { MemoryText.today() }
        val year = date.year.toString()
        val month = "%02d".format(date.monthValue)
        return "daily/$year/$month/$year-$month/${MemoryText.dayKey(date)}.md"
    }

    private fun monthPath(month: String): String =
        "daily/${month.take(4)}/${month.takeLast(2)}/$month.md"

    private fun yearPath(year: String): String = "daily/$year/$year.md"

    private data class SharedWrite(
        val message: String,
        val writeLocal: Boolean,
    )

    private fun zoneFile(target: String): File? = when (target.trim().lowercase()) {
        "profile" -> profileFile
        "context" -> contextFile
        "cookbook" -> cookbookFile
        else -> null
    }

    private fun appendZone(current: String, title: String, body: String, section: String?): String {
        if (current.isBlank()) {
            return if (section.isNullOrBlank()) {
                "# $title\n\n$body\n"
            } else {
                "# $title\n\n## ${section.trimStart('#', ' ')}\n\n$body\n"
            }
        }
        return if (section.isNullOrBlank()) {
            current.trimEnd() + "\n\n" + body + "\n"
        } else {
            MemorySections.append(current, section, body)
        }
    }

    private fun ensureZoneHeading(title: String, body: String): String {
        val text = body.trimStart()
        return if (text.startsWith("# ")) text.trimEnd() + "\n"
        else "# $title\n\n" + text.trimEnd() + "\n"
    }

    private fun normalizedDay(date: String?): String =
        date?.trim()?.takeIf { MemoryText.isValidDay(it) } ?: MemoryText.todayKey()

    private fun normalizedMonth(date: String?): String {
        val raw = date?.trim().orEmpty()
        if (raw.matches(Regex("\\d{4}-\\d{2}"))) return raw
        if (MemoryText.isValidDay(raw)) return raw.take(7)
        return MemoryText.monthKey()
    }

    private fun normalizedYear(date: String?): String {
        val raw = date?.trim().orEmpty()
        if (raw.matches(Regex("\\d{4}"))) return raw
        return MemoryText.yearKey()
    }

    private fun buildSectionLocked(reason: String): String? {
        val today = MemoryText.todayKey()
        val month = MemoryText.monthKey()
        val year = MemoryText.yearKey()
        val takenAt = MemoryText.nowStamp()
        runCatching { ensureLocked() }
        val section = runCatching {
            renderMemorySection(
                profile = profileFile.readTextOrEmpty(),
                context = contextFile.readTextOrEmpty(),
                cookbook = cookbookFile.readTextOrEmpty(),
                todayText = readDayLocked(today),
                todayLabel = today,
                monthText = readMonthLocked(month),
                monthLabel = month,
                yearText = readYearLocked(year),
                yearLabel = year,
            )
        }.onFailure { logWarn(TAG, "render memory section failed", it) }.getOrNull()
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
        dirty = true
    }

    private fun corpusLocked(): List<MemoryDoc> {
        corpusCache?.let { return it }
        val docs = ArrayList<MemoryDoc>()
        docs += MemorySearch.chunk("profile.md", profileFile.readTextOrEmpty())
        docs += MemorySearch.chunk("context.md", contextFile.readTextOrEmpty())
        docs += MemorySearch.chunk("cookbook.md", cookbookFile.readTextOrEmpty())
        dailyDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".md") }
            .sortedBy { it.path }
            .forEach { file ->
                val label = file.relativeTo(rootDir).invariantSeparatorsPath
                docs += MemorySearch.chunk(label, file.readTextOrEmpty())
            }
        corpusCache = docs
        return docs
    }

    private fun dayKeysLocked(): List<String> =
        dailyDir.walkTopDown()
            .filter { it.isFile && DAY_FILE.matches(it.name) }
            .map { it.name.removeSuffix(".md") }
            .sorted()
            .toList()

    private fun readDayLocked(day: String): String = dayFile(day).readTextOrEmpty()

    private fun readMonthLocked(month: String): String = monthFileFor(month).readTextOrEmpty()

    private fun readYearLocked(year: String): String = yearFileFor(year).readTextOrEmpty()

    private fun appendDayLocked(day: String, block: String) {
        if (block.isBlank()) return
        val file = dayFile(day)
        val current = file.readTextOrEmpty().trimEnd()
        val head = if (current.isBlank()) "# $day" else current
        val updated = head + "\n\n" + block.trim() + "\n"
        writeAtomically(file, updated)
    }

    private fun dayFile(day: String): File {
        val date = runCatching { LocalDate.parse(day) }.getOrElse { MemoryText.today() }
        val year = date.year.toString()
        val month = "%02d".format(date.monthValue)
        return File(dailyDir, "$year/$month/$year-$month/${MemoryText.dayKey(date)}.md")
    }

    private fun monthFile(dayKey: String): File = monthFileFor(dayKey.take(7))

    private fun monthFileFor(month: String): File {
        val year = month.take(4)
        val mm = month.takeLast(2)
        return File(dailyDir, "$year/$mm/$month.md")
    }

    private fun yearFile(monthKey: String): File = yearFileFor(monthKey.take(4))

    private fun yearFileFor(year: String): File = File(dailyDir, "$year/$year.md")

    private fun pendingFile(sessionId: String): File =
        File(pendingDir, sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt")

    private fun prunePendingLocked() {
        val files = pendingDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".txt") }
            .sortedBy { it.lastModified() }
        if (files.size <= MAX_PENDING_RECAPS) return
        files.take(files.size - MAX_PENDING_RECAPS).forEach { it.delete() }
    }

    private fun parsePending(file: File): PendingRecap? = runCatching {
        val parts = file.readText().split("\n\n", limit = 2)
        if (parts.size < 2) return@runCatching null
        val header = parts[0].split('\n')
        if (header.size < 3) return@runCatching null
        val decoder = Base64.getDecoder()
        fun decode(value: String): String = String(decoder.decode(value), Charsets.UTF_8)
        PendingRecap(
            sessionId = file.name.removeSuffix(".txt"),
            sessionFile = decode(header[0]).takeIf { it.isNotBlank() },
            transcript = parts[1],
            lastEntryId = decode(header[1]).takeIf { it.isNotBlank() },
            reason = decode(header[2]).ifBlank { "auto" },
        )
    }.getOrNull()

    private fun File.readTextOrEmpty(): String = runCatching { if (isFile) readText() else "" }.getOrDefault("")

    private fun writeAtomically(file: File, text: String) {
        runCatching {
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
        }.onFailure { logWarn(TAG, "write memory file failed", it) }
    }

    private companion object {
        private const val TAG = "SuperPIMemory"
        private const val MAX_PENDING_RECAPS = 30
        private val DAY_FILE = Regex("\\d{4}-\\d{2}-\\d{2}\\.md")
        private val MONTH_FILE = Regex("\\d{4}-\\d{2}\\.md")
    }
}

internal class MemoryWriteRejected(message: String) : Exception(message)

private fun unknownTarget(target: String): String =
    "Unknown target: $target (write: profile / context / cookbook / today; read: also month / year)."
