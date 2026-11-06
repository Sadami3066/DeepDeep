package io.github.whywood.deepdeep.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.session.SessionArchiveError
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.time.LocalDate

internal data class SessionBackupUiState(
    val sessionCount: Int? = null,
    val includeMarkdown: Boolean = false,
    val busy: Boolean = false,
    val notice: SessionBackupNotice? = null,
)

internal sealed interface SessionBackupNotice {
    data class Exported(val sessions: Int, val markdown: Int, val skipped: Int) : SessionBackupNotice

    data class Imported(val imported: Int, val skipped: Int) : SessionBackupNotice

    data class Failed(val error: SessionArchiveError) : SessionBackupNotice
}

@Composable
internal fun SessionBackupScreen(
    state: SessionBackupUiState,
    onIncludeMarkdownChange: (Boolean) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onDismissNotice: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = deepDeepColors()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val count = state.sessionCount
    val exportFileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())
    val exportSummary = when {
        count == null -> stringResource(R.string.backup_export_summary_loading)
        count == 0 -> stringResource(R.string.backup_export_summary_empty)
        else -> stringResource(R.string.backup_export_summary, count)
    }
    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.weight(1f))
            if (state.busy) DdSpinner(modifier = Modifier.padding(end = 16.dp))
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.backup_title), style = DdType.pageTitle, color = colors.ink)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.backup_subtitle), style = DdType.body, color = colors.inkSecondary)
            Spacer(Modifier.height(28.dp))
            DdSectionLabel(stringResource(R.string.backup_section_content))
            Spacer(Modifier.height(8.dp))
            DdGroupCard {
                DdListRow(
                    title = stringResource(R.string.backup_export_title),
                    subtitle = exportSummary,
                    icon = DdIcons.File,
                    onClick = { exportLauncher.launch(exportFileName) },
                    enabled = !state.busy && (count ?: 0) > 0,
                    showChevron = true,
                )
                DdHairline()
                DdListRow(
                    title = stringResource(R.string.backup_import_title),
                    subtitle = stringResource(R.string.backup_import_summary),
                    icon = DdIcons.Download,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.busy,
                    showChevron = true,
                )
            }
            Spacer(Modifier.height(28.dp))
            DdSectionLabel(stringResource(R.string.backup_section_options))
            Spacer(Modifier.height(8.dp))
            DdGroupCard {
                DdListRow(
                    title = stringResource(R.string.backup_include_markdown_title),
                    subtitle = stringResource(R.string.backup_include_markdown_summary),
                    icon = DdIcons.Raw,
                    onClick = { onIncludeMarkdownChange(!state.includeMarkdown) },
                    enabled = !state.busy,
                    trailing = {
                        DdSwitch(
                            checked = state.includeMarkdown,
                            onCheckedChange = onIncludeMarkdownChange,
                            enabled = !state.busy,
                            stateDescription = stringResource(
                                if (state.includeMarkdown) R.string.settings_switch_on else R.string.settings_switch_off,
                            ),
                        )
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.backup_hint), style = DdType.bodySmall, color = colors.inkTertiary)
            Spacer(Modifier.height(40.dp))
        }
    }
    val notice = state.notice
    if (notice != null) {
        DdDialog(
            onDismissRequest = onDismissNotice,
            title = stringResource(
                if (notice is SessionBackupNotice.Failed) R.string.backup_notice_failed_title else R.string.backup_notice_title,
            ),
            content = {
                Text(noticeMessage(notice), style = DdType.body, color = colors.inkSecondary)
            },
            actions = {
                DdTextAction(text = stringResource(R.string.backup_notice_close), onClick = onDismissNotice)
            },
        )
    }
}

@Composable
private fun noticeMessage(notice: SessionBackupNotice): String = when (notice) {
    is SessionBackupNotice.Exported -> stringResource(
        R.string.backup_export_done,
        notice.sessions,
        notice.markdown,
        notice.skipped,
    )
    is SessionBackupNotice.Imported -> stringResource(R.string.backup_import_done, notice.imported, notice.skipped)
    is SessionBackupNotice.Failed -> stringResource(R.string.backup_error_message, archiveErrorText(notice.error))
}

@Composable
private fun archiveErrorText(error: SessionArchiveError): String = stringResource(
    when (error) {
        SessionArchiveError.NotZip -> R.string.backup_error_not_zip
        SessionArchiveError.MissingManifest -> R.string.backup_error_missing_manifest
        SessionArchiveError.InvalidManifest -> R.string.backup_error_invalid_manifest
        SessionArchiveError.UnsupportedVersion -> R.string.backup_error_unsupported_version
        SessionArchiveError.TooLarge -> R.string.backup_error_too_large
        SessionArchiveError.Io -> R.string.backup_error_io
    },
)
