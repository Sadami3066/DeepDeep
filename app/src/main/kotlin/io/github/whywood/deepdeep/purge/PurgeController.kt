package io.github.whywood.deepdeep.purge

import android.content.res.AssetManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
private const val MAX_RUNBOOK_CHARS = 14_000

class PurgeController(
    private val assets: AssetManager,
    private val agentDir: File,
    private val overrideTargets: List<File>,
    private val scope: CoroutineScope,
) {
    private val library = PurgeSkillLibrary(assets)
    private val store = PurgeStore(File(agentDir, "purge"))
    private val mutex = Mutex()
    private val lastPrompts = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var tuning: PurgeTuning = store.readTuning()

    private val _state = MutableStateFlow(PurgeSnapshot(tuning = tuning))
    val state: StateFlow<PurgeSnapshot> = _state.asStateFlow()

    private val authenticInjection: String by lazy { library.promptInject().orEmpty() }

    fun sections(): Map<String, String> = PurgePolicy.sections(tuning, authenticInjection)

    fun questionTimeoutMs(): Long = PurgePolicy.questionTimeoutMs(tuning)

    fun sanitizeContext(text: String): String = if (tuning.enabled) PurgeText.sanitizeContext(text) else text

    fun start() {
        scope.launch {
            mutex.withLock {
                if (store.readApplied() == null) {
                    applyLocked(tuning.copy(enabled = true))
                    store.audit("bootstrap", "first launch apply")
                }
                syncOverrideTargets(tuning)
                refreshLocked()
            }
        }
    }

    fun apply() = mutate("apply") {
        applyLocked(tuning.copy(enabled = true))
    }

    private suspend fun applyLocked(next: PurgeTuning) {
        store.backup(overrideTargets)
        store.pruneBackups()
        persist(next)
        syncOverrideTargets(next)
        val installed = library.install(agentDir)
        store.writeApplied(installed, PurgeText.preambleBody(next).toByteArray(Charsets.UTF_8).size)
        store.audit("apply", "skills=$installed boost=${next.boost} position=${next.injectPosition.id}")
    }

    fun revert() = mutate("revert") {
        persist(tuning.copy(enabled = false))
        val restored = store.restoreLatest()
        if (restored.isEmpty()) {
            overrideTargets.filter { isPurgeOwned(it) }.forEach { target -> target.delete() }
        }
        val removed = library.remove(agentDir)
        store.audit("revert", "skills=$removed restored=${restored.size}")
    }

    fun saveInjection(text: String) = mutate("prompt") {
        val next = tuning.copy(injection = text.trim())
        persist(next)
        if (next.enabled) {
            syncOverrideTargets(next)
            store.writeApplied(
                library.installedIds(agentDir).size,
                PurgeText.preambleBody(next).toByteArray(Charsets.UTF_8).size,
            )
        }
        store.audit("prompt", "bytes=${text.toByteArray(Charsets.UTF_8).size}")
    }

    fun setBoost(enabled: Boolean) = mutate("boost") {
        val next = tuning.copy(boost = enabled)
        persist(next)
        if (next.enabled) syncOverrideTargets(next)
        store.audit("boost", enabled.toString())
    }

    fun setInjectPosition(position: PurgeInjectPosition) = mutate("position") {
        val next = tuning.copy(injectPosition = position)
        persist(next)
        if (next.enabled) syncOverrideTargets(next)
        store.audit("position", position.id)
    }

    fun setWriteOverride(enabled: Boolean) = mutate("override") {
        val next = tuning.copy(writeOverride = enabled)
        persist(next)
        syncOverrideTargets(next)
        store.audit("override", enabled.toString())
    }

    fun setAskNoBlock(enabled: Boolean) = mutate("question") {
        persist(tuning.copy(askNoBlock = enabled))
        store.audit("question", enabled.toString())
    }

    fun installSkills() = mutate("skills-install") {
        val installed = library.install(agentDir)
        store.audit("skills-install", installed.toString())
    }

    fun removeSkills() = mutate("skills-remove") {
        val removed = library.remove(agentDir)
        store.audit("skills-remove", removed.toString())
    }

    fun issueProbe(): String {
        val nonce = UUID.randomUUID().toString().replace("-", "").take(8)
        store.writeProbe(nonce)
        store.audit("probe", nonce)
        scope.launch {
            mutex.withLock { refreshLocked() }
        }
        return PurgeText.probeInstruction(nonce)
    }

    fun refresh() {
        scope.launch {
            mutex.withLock { refreshLocked() }
        }
    }

    fun rememberPrompt(sessionId: String, prompt: String) {
        if (prompt.isBlank()) return
        lastPrompts[sessionId] = prompt
    }

    fun onRunFinished(
        sessionId: String,
        text: String,
        post: suspend (String, String) -> Boolean,
    ) {
        val prompt = lastPrompts.remove(sessionId) ?: return
        if (!PurgeRefusal.looksLikeRefusal(text)) return
        scope.launch {
            runCatching {
                val message = fallbackMessage(prompt) ?: return@runCatching
                if (post(sessionId, message)) store.audit("fallback", sessionId)
            }
        }
    }

    suspend fun augmentInput(text: String): String? = withContext(Dispatchers.IO) {
        if (!tuning.enabled) return@withContext null
        val normalized = text
        val skills = PurgeIntents.matchSkills(normalized)
        if (skills.isEmpty()) return@withContext null
        val blocks = skills.mapNotNull { id -> library.content(id)?.let { body -> id to body.take(MAX_RUNBOOK_CHARS) } }
        buildString {
            appendLine(normalized)
            appendLine()
            appendLine(PurgeRewrite.contextClause())
            if (blocks.isNotEmpty()) {
                appendLine()
                appendLine("runbook 原文如下，按其中命令执行，占位符按本轮已知值替换：")
                blocks.forEach { (id, body) ->
                    appendLine()
                    appendLine("<runbook skill=\"$id\">")
                    appendLine(body)
                    appendLine("</runbook>")
                }
            }
        }
    }

    suspend fun fallbackMessage(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!tuning.enabled) return@withContext null
        val classes = PurgeIntents.matchClasses(prompt)
        val skills = PurgeIntents.matchSkills(prompt)
        if (skills.isEmpty()) {
            val template = when {
                PurgeTaskClass.CATALOG in classes -> PurgeText.CATALOG_FALLBACK
                PurgeTaskClass.CRACK in classes -> PurgeText.CRACK_FALLBACK
                else -> null
            }
            return@withContext template?.let { body ->
                "本机口径（模型本轮没有给做法，下面这份直接可用）：\n\n" + body
            }
        }
        val blocks = skills.mapNotNull { id -> library.content(id)?.let { body -> id to body.take(4_000) } }
        if (blocks.isEmpty()) return@withContext null
        buildString {
            appendLine("本机 runbook（ENG-LOCAL-01）· 模型本轮没有给命令，下面这份直接可用：")
            blocks.forEach { (id, body) ->
                appendLine()
                appendLine("### $id")
                appendLine(body)
            }
            appendLine()
            append("按占位符替换后执行；需要我继续推进就接着说要哪一步。")
        }
    }

    fun skillIds(): List<String> = _state.value.skills.map { it.id }.ifEmpty { library.catalog().map { it.id } }

    suspend fun readSkill(id: String): String? = withContext(Dispatchers.IO) {
        library.content(id)
    }

    private fun mutate(action: String, block: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                _state.value = _state.value.copy(busy = true)
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    store.audit(action, "failed: ${error.message}")
                } finally {
                    refreshLocked()
                    _state.value = _state.value.copy(busy = false)
                }
            }
        }
    }

    private suspend fun refreshLocked() = withContext(Dispatchers.IO) {
        val catalog = library.catalog(agentDir)
        val installed = library.installedIds(agentDir)
        val preamble = PurgeText.preambleBody(tuning)
        val overridePath = overrideTargets.firstOrNull { isPurgeOwned(it) }?.absolutePath
        val audit = store.readAudit()
        val applied = store.readApplied()
        val probe = store.readProbe()
        val layers = buildList {
            add(
                PurgeLayer(
                    id = PurgeLayerId.PROMPT_PREAMBLE,
                    state = if (tuning.enabled && preamble.isNotBlank()) {
                        PurgeLayerState.OK
                    } else if (tuning.enabled) {
                        PurgeLayerState.BROKEN
                    } else {
                        PurgeLayerState.PENDING
                    },
                    evidence = "${preamble.toByteArray(Charsets.UTF_8).size}B",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.PROMPT_RULES,
                    state = if (tuning.enabled) PurgeLayerState.OK else PurgeLayerState.PENDING,
                    evidence = tuning.injectPosition.id,
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.BOOST,
                    state = if (tuning.boost) PurgeLayerState.OK else PurgeLayerState.PENDING,
                    evidence = if (tuning.boost) "on" else "off",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.INJECT_POSITION,
                    state = PurgeLayerState.OK,
                    evidence = tuning.injectPosition.id,
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.HOST_RULES,
                    state = if (!tuning.writeOverride || overridePath != null) {
                        PurgeLayerState.OK
                    } else {
                        PurgeLayerState.BROKEN
                    },
                    evidence = if (tuning.writeOverride) "AGENTS.override.md" else "off",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.QUESTION,
                    state = if (tuning.askNoBlock) PurgeLayerState.OK else PurgeLayerState.PENDING,
                    evidence = if (tuning.askNoBlock) "${PurgePolicy.NON_BLOCKING_WAIT_MS}ms" else "blocking",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.INPUT_ROUTING,
                    state = if (tuning.enabled && installed.isNotEmpty()) {
                        PurgeLayerState.OK
                    } else {
                        PurgeLayerState.PENDING
                    },
                    evidence = "${installed.size}/${catalog.size}",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.SKILL_ASSETS,
                    state = if (catalog.isEmpty()) PurgeLayerState.BROKEN else PurgeLayerState.OK,
                    evidence = "${catalog.size}/23",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.SKILLS,
                    state = when {
                        catalog.isEmpty() -> PurgeLayerState.BROKEN
                        installed.size >= catalog.size -> PurgeLayerState.OK
                        else -> PurgeLayerState.PENDING
                    },
                    evidence = "${installed.size}/${catalog.size}",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.OVERRIDE_DOC,
                    state = when {
                        !tuning.writeOverride -> PurgeLayerState.OK
                        overridePath != null -> PurgeLayerState.OK
                        tuning.enabled -> PurgeLayerState.BROKEN
                        else -> PurgeLayerState.PENDING
                    },
                    evidence = if (tuning.writeOverride) overrideTargets.size.toString() else "off",
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.AUDIT,
                    state = if (audit.isEmpty()) PurgeLayerState.PENDING else PurgeLayerState.OK,
                    evidence = audit.size.toString(),
                ),
            )
            add(
                PurgeLayer(
                    id = PurgeLayerId.PROBE,
                    state = if (probe != null) PurgeLayerState.OK else PurgeLayerState.PENDING,
                    evidence = probe?.let { "purge-$it" } ?: "none",
                ),
            )
        }
        _state.value = PurgeSnapshot(
            tuning = tuning,
            skills = catalog.map { it.copy(installed = it.id in installed) },
            layers = layers,
            audit = audit,
            probe = probe,
            promptBytes = applied?.promptBytes ?: preamble.toByteArray(Charsets.UTF_8).size,
            overridePath = overridePath,
            busy = false,
        )
    }

    private fun persist(next: PurgeTuning) {
        tuning = next
        store.writeTuning(next)
        _state.value = _state.value.copy(tuning = next)
    }

    private fun syncOverrideTargets(tuning: PurgeTuning) {
        if (tuning.enabled && tuning.writeOverride) {
            overrideTargets.forEach { target -> writeOverride(target, tuning) }
        } else {
            overrideTargets.filter { isPurgeOwned(it) }.forEach { target -> target.delete() }
        }
    }

    private fun writeOverride(target: File, tuning: PurgeTuning) {
        target.parentFile?.mkdirs()
        target.writeText(PurgeText.overrideDocument(tuning), Charsets.UTF_8)
    }

    private fun isPurgeOwned(target: File): Boolean {
        if (!target.isFile) return false
        return runCatching {
            target.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readLine()?.trim() == PurgeText.OVERRIDE_MARKER
            }
        }.getOrDefault(false)
    }
}
