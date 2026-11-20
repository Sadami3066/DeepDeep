package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.runtime.DEFAULT_REFRESH_AGE
import io.github.whywood.superpi.runtime.MemoryBackend
import io.github.whywood.superpi.runtime.MemoryWriteOutcome
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class RelayMemoryState(
    val active: Boolean = false,
    val version: Long = 0,
    val lastSyncedAt: Long? = null,
    val pending: Int = 0,
    val error: String? = null,
    val notice: RelayMemoryNotice? = null,
)

enum class RelayMemoryNotice {
    LocalBackedUp,
}

class RelayMemorySync(
    filesDir: File,
    private val rootDir: File,
    private val accounts: RelayAccountStore,
    private val client: RelayMemoryApi,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : MemoryBackend {
    private val mutex = Mutex()
    private val prefsFile = File(filesDir, PREFS)
    private val outboxFile = File(filesDir, OUTBOX)
    private val queue = ArrayList<QueuedWrite>()

    @Volatile
    private var reconciledAccount: String? = null

    @Volatile
    private var account: RelayAccount? = null

    @Volatile
    private var token: String? = null

    @Volatile
    private var lastAttemptAt = 0L

    private var versions: Map<String, Long> = emptyMap()
    private var syncedAt = 0L

    private val _state = MutableStateFlow(RelayMemoryState())
    val state: StateFlow<RelayMemoryState> = _state.asStateFlow()

    init {
        loadPrefs()
        loadQueue()
        scope.launch {
            accounts.account.collect { value ->
                val previous = account
                account = value
                if (value == null) {
                    token = null
                    versions = emptyMap()
                    _state.update { it.copy(active = false, version = 0, lastSyncedAt = null) }
                } else {
                    token = accounts.token()
                    _state.update { it.copy(active = true) }
                    if (previous?.accountId != value.accountId) {
                        runCatching { refreshNow(value) }.onFailure(::recordError)
                    }
                }
            }
        }
    }

    override suspend fun append(path: String, section: String?, text: String): MemoryWriteOutcome =
        submitWrite(op = "append", path = path, section = section, text = text, baseVersion = null)

    override suspend fun replaceSection(path: String, section: String, text: String): MemoryWriteOutcome =
        submitWrite(
            op = "replace_section",
            path = path,
            section = section,
            text = text,
            baseVersion = versionOf(path),
        )

    override suspend fun overwrite(path: String, text: String): MemoryWriteOutcome =
        submitWrite(
            op = "overwrite",
            path = path,
            section = null,
            text = text,
            baseVersion = versionOf(path),
        )

    override suspend fun refreshIfStale(maxAgeMillis: Long): Boolean {
        val current = account ?: return false
        val reconciled = reconciledAccount == current.accountId
        val now = clock()
        if (!reconciled && now - lastAttemptAt < RECONCILE_RETRY_MILLIS) return false
        if (reconciled && now - syncedAt < maxAgeMillis) return false
        lastAttemptAt = now
        return runCatching { refreshNow(current) }
            .onFailure(::recordError)
            .getOrDefault(false)
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun refreshNow(current: RelayAccount): Boolean = withContext(io) {
        val secret = token ?: accounts.token() ?: return@withContext false
        token = secret
        try {
            if (reconciledAccount != current.accountId) {
                reconcile(current, secret)
            } else {
                flushQueued(current, secret)
                pull(current, secret)
            }
            true
        } catch (error: RelayMemoryException) {
            recordError(error)
            false
        }
    }

    private suspend fun reconcile(current: RelayAccount, secret: String) {
        val snapshot = client.snapshot(current, secret)
        val localContent = hasLocalContent()
        val serverContent = snapshot.files.any { (path, text) -> path != SUMMARY_LOG && text.isNotBlank() }
        when {
            !serverContent && localContent -> {
                uploadBaseline(current, secret, snapshot)
                pull(current, secret)
            }

            serverContent && localContent -> {
                backupLocal()
                applySnapshot(snapshot)
                _state.update { it.copy(notice = RelayMemoryNotice.LocalBackedUp) }
            }

            else -> applySnapshot(snapshot)
        }
        reconciledAccount = current.accountId
        savePrefs()
    }

    private suspend fun uploadBaseline(
        current: RelayAccount,
        secret: String,
        snapshot: RelayMemorySnapshot,
    ) {
        localMemoryFiles().forEach { (path, text) ->
            client.write(
                current,
                secret,
                RelayMemoryWrite(
                    op = "overwrite",
                    path = path,
                    text = text,
                    section = null,
                    baseVersion = snapshot.versions[path] ?: 0L,
                    idempotencyKey = idempotencyKey(current),
                ),
            )
        }
    }

    private suspend fun pull(current: RelayAccount, secret: String) {
        applySnapshot(client.snapshot(current, secret))
    }

    private suspend fun applySnapshot(snapshot: RelayMemorySnapshot) = withContext(io) {
        mutex.withLock {
            rootDir.mkdirs()
            val files = snapshot.files.filterKeys { validPath(it) && it != SUMMARY_LOG }
            files.forEach { (path, text) -> writeMirror(path, text) }
            pruneMirror(files.keys)
            versions = snapshot.versions.filterKeys { it in files.keys }
            syncedAt = clock()
            savePrefs()
            _state.update { state ->
                state.copy(
                    active = true,
                    version = snapshot.version,
                    lastSyncedAt = syncedAt,
                    pending = queue.size,
                    error = null,
                )
            }
        }
    }

    private suspend fun flushQueued(current: RelayAccount, secret: String) {
        while (true) {
            val entry = mutex.withLock { queue.firstOrNull() } ?: return
            when (val outcome = submit(current, secret, entry, allowRetry = true)) {
                is SubmitOutcome.Applied -> {
                    val pending = mutex.withLock {
                        queue.remove(entry)
                        persistQueueLocked()
                        queue.size
                    }
                    _state.update { it.copy(pending = pending) }
                }

                SubmitOutcome.Offline -> return

                is SubmitOutcome.Rejected -> {
                    val pending = mutex.withLock {
                        queue.remove(entry)
                        persistQueueLocked()
                        queue.size
                    }
                    _state.update { it.copy(pending = pending, error = outcome.message) }
                }
            }
        }
    }

    private suspend fun submitWrite(
        op: String,
        path: String,
        section: String?,
        text: String,
        baseVersion: Long?,
    ): MemoryWriteOutcome {
        val current = account ?: return MemoryWriteOutcome.Inactive
        val secret = token ?: accounts.token() ?: return MemoryWriteOutcome.Inactive
        token = secret
        val entry = QueuedWrite(
            op = op,
            path = path,
            section = section,
            text = text,
            baseVersion = baseVersion,
            idempotencyKey = idempotencyKey(current),
            createdAt = clock(),
        )
        return withContext(io) {
            when (val outcome = submit(current, secret, entry, allowRetry = true)) {
                is SubmitOutcome.Applied -> {
                    runCatching { pull(current, secret) }.onFailure(::recordError)
                    _state.update { it.copy(version = outcome.version, lastSyncedAt = syncedAt) }
                    MemoryWriteOutcome.Applied
                }

                SubmitOutcome.Offline -> {
                    val pending = mutex.withLock {
                        queue += entry
                        persistQueueLocked()
                        queue.size
                    }
                    _state.update { it.copy(pending = pending) }
                    MemoryWriteOutcome.Queued
                }

                is SubmitOutcome.Rejected -> MemoryWriteOutcome.Rejected(outcome.message)
            }
        }
    }

    private suspend fun submit(
        current: RelayAccount,
        secret: String,
        entry: QueuedWrite,
        allowRetry: Boolean,
    ): SubmitOutcome {
        val request = RelayMemoryWrite(
            op = entry.op,
            path = entry.path,
            text = entry.text,
            section = entry.section,
            baseVersion = entry.baseVersion,
            idempotencyKey = entry.idempotencyKey,
        )
        return try {
            SubmitOutcome.Applied(client.write(current, secret, request))
        } catch (error: RelayMemoryException) {
            when (error.failure) {
                RelayMemoryFailure.Conflict -> {
                    if (!allowRetry || entry.baseVersion == null) {
                        SubmitOutcome.Rejected(CONFLICT_MESSAGE)
                    } else {
                        val snapshot = runCatching { client.snapshot(current, secret) }.getOrNull()
                            ?: return SubmitOutcome.Offline
                        applySnapshot(snapshot)
                        val fresh = mutex.withLock { versions[entry.path] ?: 0L }
                        submit(current, secret, entry.copy(baseVersion = fresh), allowRetry = false)
                    }
                }

                RelayMemoryFailure.Unreachable,
                RelayMemoryFailure.BadResponse,
                -> SubmitOutcome.Offline

                RelayMemoryFailure.Unauthorized -> SubmitOutcome.Rejected(SIGNED_OUT_MESSAGE)
                RelayMemoryFailure.Rejected -> SubmitOutcome.Rejected(error.detail ?: WRITE_FAILED_MESSAGE)
            }
        }
    }

    private fun versionOf(path: String): Long = versions[path] ?: 0L

    private fun idempotencyKey(current: RelayAccount): String =
        "${current.deviceId}:${UUID.randomUUID()}"

    private fun recordError(error: Throwable) {
        val message = when (error) {
            is RelayMemoryException -> error.detail ?: error.failure.name
            else -> error.message ?: "memory sync failed"
        }
        _state.update { it.copy(error = message) }
    }

    private fun hasLocalContent(): Boolean = localMemoryFiles().isNotEmpty()

    private fun localMemoryFiles(): Map<String, String> {
        val files = LinkedHashMap<String, String>()
        ZONE_FILES.forEach { name ->
            val text = File(rootDir, name).readTextOrEmpty()
            if (textHasBody(text)) files[name] = text
        }
        val daily = File(rootDir, "daily")
        if (daily.isDirectory) {
            daily.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".md") }
                .sortedBy { it.path }
                .forEach { file ->
                    val text = file.readTextOrEmpty()
                    if (textHasBody(text)) {
                        files[file.relativeTo(rootDir).invariantSeparatorsPath] = text
                    }
                }
        }
        return files
    }

    private fun textHasBody(text: String): Boolean = text.lineSequence().any { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("# ")
    }

    private fun backupLocal() {
        val target = File(rootDir, ".conflict-${STAMP.format(LocalDateTime.now())}")
        if (!target.mkdirs()) return
        ZONE_FILES.forEach { name ->
            val file = File(rootDir, name)
            if (file.isFile) file.copyTo(File(target, name), overwrite = true)
        }
        val daily = File(rootDir, "daily")
        if (daily.isDirectory) {
            daily.copyRecursively(File(target, "daily"), overwrite = true)
        }
    }

    private fun writeMirror(path: String, text: String) {
        runCatching {
            val file = File(rootDir, path)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, ".${file.name}.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) file.writeText(text)
            }
        }
    }

    private fun pruneMirror(keep: Set<String>) {
        val daily = File(rootDir, "daily")
        if (daily.isDirectory) {
            daily.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".md") }
                .forEach { file ->
                    if (file.relativeTo(rootDir).invariantSeparatorsPath !in keep) {
                        runCatching { file.delete() }
                    }
                }
        }
        ZONE_FILES.forEach { name ->
            if (name !in keep) runCatching { File(rootDir, name).delete() }
        }
    }

    private fun validPath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.contains("..")) return false
        if (path in ZONE_FILES || path == SUMMARY_LOG) return true
        return path.startsWith("daily/") && path.endsWith(".md")
    }

    private fun loadPrefs() {
        val text = runCatching { prefsFile.readText() }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        reconciledAccount = json["account"]?.jsonPrimitive?.contentOrNull
        syncedAt = json["syncedAt"]?.jsonPrimitive?.longOrNull ?: 0L
    }

    private fun savePrefs() {
        val payload = buildJsonObject {
            reconciledAccount?.let { put("account", it) }
            if (syncedAt > 0L) put("syncedAt", syncedAt)
        }
        runCatching {
            prefsFile.parentFile?.mkdirs()
            prefsFile.writeText(payload.toString())
        }
    }

    private fun loadQueue() {
        val text = runCatching { outboxFile.readText() }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return
        val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return
        array.forEach { element ->
            val json = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val entry = runCatching {
                QueuedWrite(
                    op = json.getValue("op").jsonPrimitive.content,
                    path = json.getValue("path").jsonPrimitive.content,
                    section = json["section"]?.jsonPrimitive?.contentOrNull,
                    text = json.getValue("text").jsonPrimitive.content,
                    baseVersion = json["baseVersion"]?.jsonPrimitive?.longOrNull,
                    idempotencyKey = json.getValue("idempotencyKey").jsonPrimitive.content,
                    createdAt = json["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }.getOrNull() ?: return@forEach
            queue += entry
        }
        _state.update { it.copy(pending = queue.size) }
    }

    private fun persistQueueLocked() {
        val payload = buildJsonArray {
            queue.forEach { entry ->
                add(buildJsonObject {
                    put("op", entry.op)
                    put("path", entry.path)
                    entry.section?.let { put("section", it) }
                    put("text", entry.text)
                    entry.baseVersion?.let { put("baseVersion", it) }
                    put("idempotencyKey", entry.idempotencyKey)
                    put("createdAt", entry.createdAt)
                })
            }
        }
        runCatching {
            outboxFile.parentFile?.mkdirs()
            outboxFile.writeText(payload.toString())
        }
    }

    private fun File.readTextOrEmpty(): String =
        runCatching { if (isFile) readText() else "" }.getOrDefault("")

    private data class QueuedWrite(
        val op: String,
        val path: String,
        val section: String?,
        val text: String,
        val baseVersion: Long?,
        val idempotencyKey: String,
        val createdAt: Long,
    )

    private sealed interface SubmitOutcome {
        data class Applied(val version: Long) : SubmitOutcome

        data object Offline : SubmitOutcome

        data class Rejected(val message: String) : SubmitOutcome
    }

    private companion object {
        private const val PREFS = "relay-memory.json"
        private const val OUTBOX = "relay-memory-outbox.json"
        private const val SUMMARY_LOG = "summaries.log"
        private const val RECONCILE_RETRY_MILLIS = 60_000L
        private const val CONFLICT_MESSAGE =
            "Memory changed on another device. Read it again, then retry the write."
        private const val SIGNED_OUT_MESSAGE =
            "This phone is no longer connected to the account, so memory was not written."
        private const val WRITE_FAILED_MESSAGE = "The shared memory rejected this write."
        private val ZONE_FILES = listOf("profile.md", "context.md", "cookbook.md")
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
