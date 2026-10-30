package io.github.whywood.deepdeep.ui.schedules

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.NewSchedule
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleDeliveryException
import io.github.whywood.deepdeep.runtime.SchedulePatch
import io.github.whywood.deepdeep.runtime.ScheduleService
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

internal data class SchedulesUiState(
    val loading: Boolean = true,
    val items: List<Schedule> = emptyList(),
    val titles: Map<String, String?> = emptyMap(),
    val query: String = "",
    val editorOpen: Boolean = false,
    val editingId: String? = null,
    val editorDraft: ScheduleDraft = ScheduleDraft(),
    val busy: Boolean = false,
    val errorRes: Int? = null,
    val conversations: List<ConversationSummary> = emptyList(),
    val workspaceNames: Map<String, String> = emptyMap(),
) {
    val filtered: List<Schedule> get() = filterSchedules(items, titles, query)

    val active: List<Schedule>
        get() = filtered.filter { it.enabled }
            .sortedWith(compareBy({ it.nextRunAt ?: Long.MAX_VALUE }, { it.name }))

    val inactive: List<Schedule>
        get() = filtered.filterNot { it.enabled }.sortedByDescending { it.updatedAt }
}

internal interface SchedulesDataSource {
    val schedules: StateFlow<List<Schedule>>

    suspend fun list(): List<Schedule>

    suspend fun create(newSchedule: NewSchedule): Schedule

    suspend fun update(id: String, patch: SchedulePatch): Schedule?

    suspend fun delete(id: String): Boolean

    suspend fun runNow(id: String): Result<Unit>

    suspend fun sessionTitle(sessionId: String): String?

    suspend fun conversations(): List<ConversationSummary>

    suspend fun workspaces(): List<RuntimeWorkspace>
}

internal class ScheduleServiceDataSource(
    private val service: ScheduleService,
    private val runtime: ConversationQueries,
) : SchedulesDataSource {
    override val schedules: StateFlow<List<Schedule>> get() = service.schedules

    override suspend fun list(): List<Schedule> = service.list()

    override suspend fun create(newSchedule: NewSchedule): Schedule = service.create(newSchedule)

    override suspend fun update(id: String, patch: SchedulePatch): Schedule? = service.update(id, patch)

    override suspend fun delete(id: String): Boolean = service.delete(id)

    override suspend fun runNow(id: String): Result<Unit> = service.runNow(id)

    override suspend fun sessionTitle(sessionId: String): String? = runtime.sessionTitle(sessionId)

    override suspend fun conversations(): List<ConversationSummary> = runtime.allSessions()

    override suspend fun workspaces(): List<RuntimeWorkspace> = runtime.workspaces()
}

internal class SchedulesStateHolder(
    private val source: SchedulesDataSource,
    private val scope: CoroutineScope,
    private val initialConversationId: String? = null,
) {
    private val _state = MutableStateFlow(SchedulesUiState())
    val state: StateFlow<SchedulesUiState> = _state.asStateFlow()

    private val refreshLock = Mutex()
    private var observeJob: Job? = null

    fun start() {
        if (observeJob != null) return
        observeJob = scope.launch {
            source.schedules.collect { items ->
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
                editorDraft = ScheduleDraft(
                    sessionId = conversation?.id.orEmpty(),
                    sessionFile = conversation?.filePath.orEmpty(),
                    cwd = conversation?.cwd.orEmpty(),
                    conversationTitle = conversation?.title.orEmpty(),
                ),
            )
        }
    }

    suspend fun openExisting(schedule: Schedule) {
        val sessionId = schedule.sessionId
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
                editingId = schedule.id,
                errorRes = null,
                editorDraft = ScheduleDraft.from(schedule, title.orEmpty()),
            )
        }
    }

    fun dismissEditor() {
        _state.update { it.copy(editorOpen = false, editingId = null, errorRes = null) }
    }

    suspend fun save(draft: ScheduleDraft) = mutate(R.string.schedule_error_save_failed) {
        val trigger = draft.toTrigger()
        if (draft.id == null) {
            source.create(
                NewSchedule(
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
                SchedulePatch(
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

    suspend fun remove(id: String) = mutate(R.string.schedule_error_delete_failed) {
        source.delete(id)
        _state.update { it.copy(editorOpen = false, editingId = null) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        try {
            source.update(id, SchedulePatch(enabled = enabled))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(errorRes = R.string.schedule_error_save_failed) }
        }
    }

    suspend fun runNow(draft: ScheduleDraft): Boolean {
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
                    it.copy(errorRes = deliveryErrorRes((error as? ScheduleDeliveryException)?.error))
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(errorRes = R.string.schedule_error_run_failed) }
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
