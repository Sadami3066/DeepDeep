package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.WorkspaceAdmin
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class WorkspaceController(
    private val runtime: WorkspaceAdmin,
    private val queries: ConversationQueries,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val state: () -> ChatUiState,
    private val update: ((ChatUiState) -> ChatUiState) -> Unit,
    private val selectWorkspace: (String) -> Unit,
    private val refreshSessions: suspend () -> Unit,
    private val userFacingError: (String) -> String,
    private val createWorkspaceFailedText: () -> String,
) {
    fun create(name: String) {
        scope.launch {
            try {
                val workspace = withContext(ioDispatcher) { runtime.createWorkspace(name) }
                val workspaces = withContext(ioDispatcher) { queries.workspaces() }
                update { it.copy(workspaces = workspaces) }
                selectWorkspace(workspace.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update { it.copy(error = userFacingError(error.message ?: createWorkspaceFailedText())) }
            }
        }
    }

    fun rename(workspaceId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            try {
                runtime.renameWorkspace(workspaceId, trimmed)
                refreshSessions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update { it.copy(error = userFacingError(error.message ?: createWorkspaceFailedText())) }
            }
        }
    }

    fun delete(workspaceId: String) {
        if (workspaceId == HOME_ID) return
        scope.launch {
            try {
                runtime.deleteWorkspace(workspaceId)
                if (state().currentWorkspaceId == workspaceId) {
                    val workspaces = withContext(ioDispatcher) { queries.workspaces() }
                    val home = workspaces.firstOrNull { it.isHome } ?: workspaces.firstOrNull()
                    if (home != null) selectWorkspace(home.id) else refreshSessions()
                } else {
                    refreshSessions()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update { it.copy(error = userFacingError(error.message ?: createWorkspaceFailedText())) }
            }
        }
    }

    companion object {
        const val HOME_ID = "home"
    }
}
