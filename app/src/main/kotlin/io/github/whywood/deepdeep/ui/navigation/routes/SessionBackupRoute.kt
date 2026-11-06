package io.github.whywood.deepdeep.ui.navigation.routes

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.session.ExportResult
import io.github.whywood.deepdeep.data.session.ImportResult
import io.github.whywood.deepdeep.data.session.SessionArchive
import io.github.whywood.deepdeep.data.session.SessionArchiveError
import io.github.whywood.deepdeep.data.session.SessionMarkdown
import io.github.whywood.deepdeep.data.session.SessionMarkdownLabels
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.SessionHistory
import io.github.whywood.deepdeep.ui.backup.SessionBackupNotice
import io.github.whywood.deepdeep.ui.backup.SessionBackupScreen
import io.github.whywood.deepdeep.ui.backup.SessionBackupUiState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SessionBackupRoute(
    dependencies: AppDependencies,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SessionBackupUiState()) }
    val labels = SessionMarkdownLabels(
        untitled = stringResource(R.string.backup_markdown_untitled),
        updatedAt = stringResource(R.string.backup_markdown_updated_at),
        messageCount = stringResource(R.string.backup_markdown_message_count),
        user = stringResource(R.string.backup_markdown_user),
        assistant = stringResource(R.string.backup_markdown_assistant),
        reasoning = stringResource(R.string.backup_markdown_reasoning),
        tool = stringResource(R.string.backup_markdown_tool),
        running = stringResource(R.string.backup_markdown_status_running),
        done = stringResource(R.string.backup_markdown_status_done),
        failed = stringResource(R.string.backup_markdown_status_failed),
        stopped = stringResource(R.string.backup_markdown_status_stopped),
        error = stringResource(R.string.backup_markdown_error),
    )
    LaunchedEffect(Unit) {
        val count = withContext(Dispatchers.IO) { loadSessionCount(dependencies) }
        if (state.sessionCount == null) state = state.copy(sessionCount = count)
    }
    SessionBackupScreen(
        state = state,
        onIncludeMarkdownChange = { include -> if (!state.busy) state = state.copy(includeMarkdown = include) },
        onExport = { uri ->
            if (!state.busy) {
                val includeMarkdown = state.includeMarkdown
                state = state.copy(busy = true, notice = null)
                scope.launch {
                    val notice = withContext(Dispatchers.IO) {
                        exportBackup(context, dependencies, uri, includeMarkdown, labels)
                    }
                    state = state.copy(busy = false, notice = notice)
                }
            }
        },
        onImport = { uri ->
            if (!state.busy) {
                state = state.copy(busy = true, notice = null)
                scope.launch {
                    val notice = withContext(Dispatchers.IO) { importBackup(context, dependencies, uri) }
                    val count = if (notice is SessionBackupNotice.Imported) {
                        withContext(Dispatchers.IO) { loadSessionCount(dependencies) }
                    } else {
                        state.sessionCount
                    }
                    state = state.copy(busy = false, notice = notice, sessionCount = count)
                }
            }
        },
        onDismissNotice = { state = state.copy(notice = null) },
        onBack = onBack,
    )
}

private suspend fun loadSessionCount(dependencies: AppDependencies): Int? = runCatching {
    dependencies.host.ensureStarted()
    backupSessions(dependencies).size
}.getOrNull()

private suspend fun backupSessions(dependencies: AppDependencies): List<ConversationSummary> =
    (dependencies.host.allSessions() + dependencies.host.archivedSessions()).distinctBy { it.id }

private suspend fun exportBackup(
    context: Context,
    dependencies: AppDependencies,
    uri: Uri,
    includeMarkdown: Boolean,
    labels: SessionMarkdownLabels,
): SessionBackupNotice {
    val temp = File(context.cacheDir, "session-backup-${System.currentTimeMillis()}.zip")
    return try {
        val result = SessionArchive.export(
            target = temp,
            sessions = backupSessions(dependencies),
            includeMarkdown = includeMarkdown,
            appVersion = BuildConfig.VERSION_NAME,
            markdownFor = { session ->
                SessionMarkdown.render(
                    title = session.title,
                    updatedAt = session.updatedAt,
                    messages = SessionHistory.load(session.filePath),
                    labels = labels,
                )
            },
        )
        when (result) {
            is ExportResult.Success -> {
                val output = context.contentResolver.openOutputStream(uri)
                if (output == null) {
                    SessionBackupNotice.Failed(SessionArchiveError.Io)
                } else {
                    output.use { sink -> temp.inputStream().use { source -> source.copyTo(sink) } }
                    SessionBackupNotice.Exported(result.sessions, result.markdown, result.skipped)
                }
            }
            is ExportResult.Failure -> SessionBackupNotice.Failed(result.error)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        SessionBackupNotice.Failed(SessionArchiveError.Io)
    } finally {
        temp.delete()
    }
}

private suspend fun importBackup(
    context: Context,
    dependencies: AppDependencies,
    uri: Uri,
): SessionBackupNotice {
    val temp = File.createTempFile("session-import-", ".zip", context.cacheDir)
    return try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            SessionBackupNotice.Failed(SessionArchiveError.Io)
        } else {
            input.use { source -> temp.outputStream().use { sink -> source.copyTo(sink) } }
            when (val result = SessionArchive.import(temp, File(context.filesDir, "agent"))) {
                is ImportResult.Success -> {
                    dependencies.host.refreshSessions()
                    SessionBackupNotice.Imported(result.imported, result.skipped)
                }
                is ImportResult.Failure -> SessionBackupNotice.Failed(result.error)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        SessionBackupNotice.Failed(SessionArchiveError.Io)
    } finally {
        temp.delete()
    }
}
