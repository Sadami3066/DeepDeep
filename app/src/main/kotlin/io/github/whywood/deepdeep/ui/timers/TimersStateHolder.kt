package io.github.whywood.deepdeep.ui.timers

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.NewTimer
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerDeliveryException
import io.github.whywood.deepdeep.runtime.TimerPatch
import io.github.whywood.deepdeep.runtime.TimerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TimersUiState(
    val loading: Boolean = true,
    val items: List<Timer> = emptyList(),
    val titles: Map<String, String?> = emptyMap(),
    val query: String = "",
    val editorOpen: Boolean = false,
    val editingId: String? = null,
    val editorDraft: TimerDraft = TimerDraft(),
    val busy: Boolean = false,
    val errorRes: Int? = null,
    val conversations: List<ConversationSummary> = emptyList(),
    val workspaceNames: Map<String, String> = emptyMap(),
) {
    val filtered: List<Timer> get() = filterTimers(items, titles, query)

    val active: List<Timer>
        get() = filtered.filter { it.enabled }
            .sortedWith(compareBy({ it.nextRunAt ?: Long.MAX_VALUE }, { it.name }))

    val inactive: List<Timer>
        get() = filtered.filterNot { it.enabled }.sortedByDescending { it.updatedAt }
}

internal interface TimersDataSource {
    val timers: StateFlow<List<Timer>>

    suspend fun list(): List<Timer>

    suspend fun create(newTimer: NewTimer): Timer

    suspend fun update(id: String, patch: TimerPatch): Timer?

    suspend fun delete(id: String): Boolean

    suspend fun runNow(id: String): Result<Unit>

    suspend fun sessionTitle(sessionId: String): String?

    suspend fun conversations(): List<ConversationSummary>

    suspend fun workspaces(): List<RuntimeWorkspace>
}

internal class TimerServiceDataSource(
    private val service: TimerService,
    private val runtime: ConversationQueries,
) : TimersDataSource {
    override val timers: StateFlow<List<Timer>> get() = service.timers

    override suspend fun list(): List<Timer> = service.list()

    override suspend fun create(newTimer: NewTimer): Timer = service.create(newTimer)

    override suspend fun update(id: String, patch: TimerPatch): Timer? = service.update(id, patch)

    override suspend fun delete(id: String): Boolean = service.delete(id)

    override suspend fun runNow(id: String): Result<Unit> = service.runNow(id)

    override suspend fun sessionTitle(sessionId: String): String? = runtime.sessionTitle(sessionId)

    override suspend fun conversations(): List<ConversationSummary> = runtime.allSessions()

    override suspend fun workspaces(): List<RuntimeWorkspace> = runtime.workspaces()
}

internal class TimersStateHolder(
    private val source: TimersDataSource,
    private val scope: CoroutineScope,
    private val initialConversationId: String? = null,
) {
    private val _state = MutableStateFlow(TimersUiState())
    val state: StateFlow<TimersUiState> = _state.asStateFlow()

    private val refreshLock = Mutex()
    private var observeJob: Job? = null

    fun start() {
        if (observeJob != null) return
        observeJob = scope.launch {
            source.timers.collect { items ->
                _state.update { it.copy(items = items) }
                resolveTitles()
            }
        }
    }

    suspend fun load() {
        start()
        _state.update { it.copy(loading = true, errorRes = null) }
        try {
            val conversations = source.conversations()
            val workspaces = source.workspaces()
            val items = source.list()
            _state.update {
                it.copy(
                    loading = false,
                    items = items,
                    conversations = conversations.filterNot { conversation -> conversation.archived }
                        .sortedByDescending { conversation -> conversation.updatedAt },
                    workspaceNames = workspaces.associate { workspace -> workspace.cwd to workspace.name },
                )
            }
            resolveTitles()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(loading = false, errorRes = R.string.chat_reload_failed) }
        }
    }

    suspend fun refresh() {
        if (!refreshLock.tryLock()) {
            refreshLock.withLock { }
            return
        }
        try {
            val items = source.list()
            _state.update { it.copy(items = items, errorRes = null) }
            resolveTitles()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(errorRes = R.string.chat_reload_failed) }
        } finally {
            refreshLock.unlock()
        }
    }

    suspend fun retry() {
        if (_state.value.loading || _state.value.items.isEmpty()) load() else refresh()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun openNew() {
        val conversation = initialConversationId?.let { id ->
            _state.value.conversations.firstOrNull { it.id == id }
        }
        _state.update {
            it.copy(
                editorOpen = true,
                editingId = null,
                errorRes = null,
                editorDraft = TimerDraft(
                    sessionId = conversation?.id.orEmpty(),
                    sessionFile = conversation?.filePath.orEmpty(),
                    cwd = conversation?.cwd.orEmpty(),
                    conversationTitle = conversation?.title.orEmpty(),
                ),
            )
        }
    }

    suspend fun openExisting(timer: Timer) {
        val sessionId = timer.sessionId
        val knownTitle = _state.value.titles[sessionId]
        val title = if (sessionId.isNotBlank() && knownTitle == null) {
            runCatching { source.sessionTitle(sessionId) }.getOrNull()
        } else {
            knownTitle
        }
        if (sessionId.isNotBlank() && knownTitle == null) {
            _state.update { it.copy(titles = it.titles + (sessionId to title)) }
        }
        _state.update {
            it.copy(
                editorOpen = true,
                editingId = timer.id,
                errorRes = null,
                editorDraft = TimerDraft.from(timer, title.orEmpty()),
            )
        }
    }

    fun dismissEditor() {
        _state.update { it.copy(editorOpen = false, editingId = null, errorRes = null) }
    }

    suspend fun save(draft: TimerDraft) = mutate(R.string.timer_error_save_failed) {
        val trigger = draft.toTrigger()
        if (draft.id == null) {
            source.create(
                NewTimer(
                    draft.name,
                    draft.prompt,
                    trigger,
                    draft.enabled,
                    draft.sessionId,
                    draft.sessionFile,
                    draft.cwd,
                ),
            )
        } else {
            source.update(
                draft.id,
                TimerPatch(
                    draft.name,
                    draft.prompt,
                    trigger,
                    draft.enabled,
                    draft.sessionId,
                    draft.sessionFile,
                    draft.cwd,
                ),
            )
        }
        _state.update { it.copy(editorOpen = false, editingId = null) }
    }

    suspend fun remove(id: String) = mutate(R.string.timer_error_delete_failed) {
        source.delete(id)
        _state.update { it.copy(editorOpen = false, editingId = null) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        try {
            source.update(id, TimerPatch(enabled = enabled))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(errorRes = R.string.timer_error_save_failed) }
        }
    }

    suspend fun runNow(draft: TimerDraft): Boolean {
        val id = draft.id ?: return false
        _state.update { it.copy(busy = true, errorRes = null) }
        var succeeded = false
        try {
            val result = source.runNow(id)
            val error = result.exceptionOrNull()
            if (error == null) {
                succeeded = true
                _state.update { it.copy(editorOpen = false, editingId = null) }
            } else {
                _state.update {
                    it.copy(errorRes = deliveryErrorRes((error as? TimerDeliveryException)?.error))
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(errorRes = R.string.timer_error_run_failed) }
        } finally {
            _state.update { it.copy(busy = false) }
        }
        return succeeded
    }

    private suspend fun mutate(errorRes: Int, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, errorRes = null) }
        try {
            block()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(errorRes = errorRes) }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun resolveTitles() {
        val missing = _state.value.items.asSequence()
            .map { it.sessionId }
            .filter { it.isNotBlank() }
            .toSet() - _state.value.titles.keys
        if (missing.isEmpty()) return
        val loaded = HashMap<String, String?>(missing.size)
        for (id in missing) loaded[id] = runCatching { source.sessionTitle(id) }.getOrNull()
        _state.update { it.copy(titles = it.titles + loaded) }
    }
}
