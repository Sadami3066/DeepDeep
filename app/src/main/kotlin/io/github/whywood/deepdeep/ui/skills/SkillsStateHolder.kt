package io.github.whywood.deepdeep.ui.skills

import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.InstalledSkill
import io.github.whywood.deepdeep.data.SkillArchiveError
import io.github.whywood.deepdeep.data.SkillArchiveResult
import io.github.whywood.deepdeep.data.SkillRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class SkillsUiState(
    val loading: Boolean = true,
    val items: List<InstalledSkill> = emptyList(),
    val error: SkillsError? = null,
    val searchQuery: String = "",
    val togglingIds: Set<String> = emptySet(),
    val installing: Boolean = false,
    val installError: SkillsError? = null,
) {
    val visibleItems: List<InstalledSkill> get() = filterSkills(items, searchQuery)

    val busy: Boolean get() = togglingIds.isNotEmpty()
}

data class SkillsError(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val retryable: Boolean = true,
)

internal interface SkillsDataSource {
    val changes: StateFlow<Long>

    suspend fun list(): Result<List<InstalledSkill>>

    suspend fun syncActiveSkills(): Int

    suspend fun syncIfChanged(): Int

    suspend fun setGlobalEnabled(id: String, enabled: Boolean): Boolean

    suspend fun delete(id: String): Boolean

    suspend fun install(bytes: ByteArray, fallbackName: String): SkillArchiveResult

    suspend fun readContent(id: String): String?
}

internal class SkillRepositorySource(
    private val repository: SkillRepository,
) : SkillsDataSource {
    override val changes: StateFlow<Long> = repository.changes

    override suspend fun list(): Result<List<InstalledSkill>> = repository.list()

    override suspend fun syncActiveSkills(): Int = repository.syncActiveSkills()

    override suspend fun syncIfChanged(): Int = repository.syncIfChanged()

    override suspend fun setGlobalEnabled(id: String, enabled: Boolean): Boolean =
        repository.setGlobalEnabled(id, enabled)

    override suspend fun delete(id: String): Boolean = repository.delete(id)

    override suspend fun install(bytes: ByteArray, fallbackName: String): SkillArchiveResult =
        repository.install(bytes, fallbackName)

    override suspend fun readContent(id: String): String? = repository.readContent(id)
}

class SkillsStateHolder internal constructor(
    private val source: SkillsDataSource,
    private val scope: CoroutineScope,
) {
    constructor(repository: SkillRepository, scope: CoroutineScope) : this(
        SkillRepositorySource(repository),
        scope,
    )

    private val _state = MutableStateFlow(SkillsUiState())
    val state: StateFlow<SkillsUiState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var refreshPending = false
    private var retryAction: (suspend () -> Unit)? = null

    fun start() {
        scope.launch { observeChanges() }
        scope.launch { load() }
    }

    fun resume() {
        scope.launch { onResume() }
    }

    fun toggle(skill: InstalledSkill, enabled: Boolean) {
        scope.launch { setEnabled(skill, enabled) }
    }

    fun remove(skill: InstalledSkill) {
        scope.launch { delete(skill) }
    }

    suspend fun observeChanges() {
        source.changes.collect { refresh() }
    }

    suspend fun load() {
        _state.update { it.copy(loading = true) }
        try {
            runCatching { source.syncActiveSkills() }
            refresh()
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }

    suspend fun refresh() {
        if (refreshMutex.tryLock()) {
            try {
                do {
                    refreshPending = false
                    reload { refresh() }
                } while (refreshPending)
            } finally {
                refreshMutex.unlock()
            }
        } else {
            refreshPending = true
        }
    }

    suspend fun onResume() {
        runCatching { source.syncIfChanged() }
        refresh()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun retry() {
        val action = retryAction ?: return
        retryAction = null
        _state.update { it.copy(error = null) }
        scope.launch { action() }
    }

    suspend fun setEnabled(skill: InstalledSkill, enabled: Boolean) {
        if (skill.id in _state.value.togglingIds) return
        _state.update { it.copy(togglingIds = it.togglingIds + skill.id) }
        try {
            val updated = source.setGlobalEnabled(skill.id, enabled)
            refresh()
            if (!updated) {
                setError { setEnabled(skill, enabled) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError { setEnabled(skill, enabled) }
        } finally {
            _state.update { it.copy(togglingIds = it.togglingIds - skill.id) }
        }
    }

    suspend fun delete(skill: InstalledSkill) {
        if (skill.id in _state.value.togglingIds) return
        _state.update { it.copy(togglingIds = it.togglingIds + skill.id) }
        try {
            val deleted = source.delete(skill.id)
            refresh()
            if (!deleted) {
                setError { delete(skill) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError { delete(skill) }
        } finally {
            _state.update { it.copy(togglingIds = it.togglingIds - skill.id) }
        }
    }

    suspend fun uninstall(skill: InstalledSkill) {
        delete(skill)
    }

    suspend fun install(bytes: ByteArray, fallbackName: String): SkillArchiveResult? {
        if (_state.value.installing) return null
        _state.update { it.copy(installing = true) }
        try {
            return when (val result = source.install(bytes, fallbackName)) {
                is SkillArchiveResult.Failure -> {
                    setInstallError(R.string.skills_install_failed_title, result.error.messageRes())
                    result
                }

                is SkillArchiveResult.Success -> {
                    _state.update { it.copy(installError = null) }
                    refresh()
                    result
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setInstallError(R.string.skills_install_failed_title, R.string.skills_error_io)
            return SkillArchiveResult.Failure(SkillArchiveError.Io)
        } finally {
            _state.update { it.copy(installing = false) }
        }
    }

    suspend fun readContent(id: String): String? = source.readContent(id)

    fun reportInstallError(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        setInstallError(titleRes, messageRes)
    }

    fun dismissInstallError() {
        _state.update { it.copy(installError = null) }
    }

    private suspend fun reload(retry: suspend () -> Unit) {
        source.list().fold(
            onSuccess = { items ->
                retryAction = null
                _state.update { it.copy(items = items, error = null) }
            },
            onFailure = {
                setError(retry)
            },
        )
    }

    private fun setError(retry: (suspend () -> Unit)?) {
        retryAction = retry
        _state.update {
            it.copy(
                error = SkillsError(
                    titleRes = R.string.skills_error_title,
                    messageRes = R.string.skills_error_io,
                    retryable = retry != null,
                ),
            )
        }
    }

    private fun setInstallError(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        _state.update {
            it.copy(
                installError = SkillsError(
                    titleRes = titleRes,
                    messageRes = messageRes,
                    retryable = false,
                ),
            )
        }
    }
}

internal fun SkillArchiveError.messageRes(): Int = when (this) {
    SkillArchiveError.NotZip -> R.string.skills_error_not_zip
    SkillArchiveError.Empty -> R.string.skills_error_empty
    SkillArchiveError.NoSkill -> R.string.skills_error_no_skill
    SkillArchiveError.MultipleSkills -> R.string.skills_error_multiple_skills
    SkillArchiveError.MissingDescription -> R.string.skills_error_missing_description
    SkillArchiveError.UnsafePath -> R.string.skills_error_unsafe_path
    SkillArchiveError.TooLarge -> R.string.skills_install_too_large
    SkillArchiveError.Io -> R.string.skills_error_io
}

internal fun filterSkills(skills: List<InstalledSkill>, query: String): List<InstalledSkill> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return skills
    return skills.filter { skill ->
        skill.name.contains(normalized, ignoreCase = true) ||
            skill.description.contains(normalized, ignoreCase = true) ||
            skill.id.contains(normalized, ignoreCase = true)
    }
}
