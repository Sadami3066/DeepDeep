package io.github.whywood.deepdeep.ui.workspaces

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.data.mountDisplayPath
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdRowDivider
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

private const val MAX_WORKSPACE_NAME_LENGTH = 40

@Composable
fun WorkspaceCreateDialog(
    existingNames: List<String>,
    privateDirectory: String,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var useSharedStorage by rememberSaveable { mutableStateOf(false) }
    var sharedDirectory by rememberSaveable { mutableStateOf<String?>(null) }
    var sharedStorageGranted by remember { mutableStateOf(isSharedStorageGranted()) }
    var pickFailed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val pickDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            val directory = sharedStorageDirectory(uri.authority, documentId)
            pickFailed = directory == null
            if (directory != null) {
                sharedDirectory = directory
                useSharedStorage = true
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        sharedStorageGranted = isSharedStorageGranted()
        onPauseOrDispose {}
    }
    val nameError = workspaceNameError(name, existingNames)
    val submit: () -> Unit = {
        submitted = true
        if (nameError == null) {
            onConfirm(name.trim(), sharedDirectory.takeIf { useSharedStorage })
        }
    }
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workspace_create_dialog"),
        title = stringResource(R.string.workspace_create),
        content = {
            Column {
                WorkspaceNameField(
                    value = name,
                    onValueChange = { name = it },
                    error = nameError,
                    submitted = submitted,
                    focusRequester = focusRequester,
                    onDone = { submit() },
                )
                Spacer(Modifier.height(18.dp))
                DdSectionLabel(stringResource(R.string.workspace_directory_label))
                Spacer(Modifier.height(6.dp))
                DdListRow(
                    title = stringResource(R.string.workspace_directory_private),
                    modifier = Modifier.testTag("workspace_directory_private"),
                    onClick = {
                        useSharedStorage = false
                        pickFailed = false
                    },
                    trailing = if (!useSharedStorage) {
                        { DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp) }
                    } else {
                        null
                    },
                )
                DdRowDivider(leadingInset = 32.dp)
                DdListRow(
                    title = stringResource(R.string.workspace_directory_public),
                    subtitle = when {
                        !sharedStorageGranted -> stringResource(R.string.workspace_directory_permission)
                        sharedDirectory != null -> mountDisplayPath(sharedDirectory.orEmpty())
                        else -> stringResource(R.string.workspace_directory_choose)
                    },
                    modifier = Modifier.testTag("workspace_directory_public"),
                    onClick = {
                        if (sharedStorageGranted) {
                            pickFailed = false
                            pickDirectory.launch(null)
                        } else {
                            openAllFilesAccess(context)
                        }
                    },
                    showChevron = true,
                    trailing = if (useSharedStorage) {
                        { DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp) }
                    } else {
                        null
                    },
                )
                val directoryMessage = when {
                    pickFailed -> R.string.workspace_directory_invalid
                    else -> null
                }
                if (directoryMessage != null) {
                    Text(
                        text = stringResource(directoryMessage),
                        modifier = Modifier
                            .padding(start = 2.dp, top = 10.dp)
                            .testTag("workspace_directory_error"),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.workspace_create_confirm),
                onClick = { submit() },
                modifier = Modifier.testTag("workspace_name_confirm"),
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun WorkspaceNameDialog(
    initial: String,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    var submitted by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val error = workspaceNameError(name, existingNames, preset = initial.trim())
    val submit: () -> Unit = {
        submitted = true
        if (error == null) onConfirm(name.trim())
    }
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workspace_name_dialog"),
        title = stringResource(R.string.workspace_rename_title),
        content = {
            WorkspaceNameField(
                value = name,
                onValueChange = { name = it },
                error = error,
                submitted = submitted,
                focusRequester = focusRequester,
                onDone = { submit() },
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.workspace_name_confirm),
                onClick = { submit() },
                modifier = Modifier.testTag("workspace_name_confirm"),
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun WorkspaceNameField(
    value: String,
    onValueChange: (String) -> Unit,
    error: Int?,
    submitted: Boolean,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
) {
    val colors = deepDeepColors()
    Column {
        DdTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.workspace_name_placeholder),
            focusRequester = focusRequester,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onDone = onDone,
            testTag = "workspace_name_input",
        )
        if (submitted && error != null) {
            Text(
                text = stringResource(error),
                modifier = Modifier
                    .padding(start = 2.dp, top = 10.dp)
                    .testTag("workspace_name_error"),
                style = DdType.bodySmall,
                color = colors.danger,
            )
        }
    }
}

private fun workspaceNameError(name: String, existingNames: List<String>, preset: String? = null): Int? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> R.string.workspace_name_error_empty
        trimmed.length > MAX_WORKSPACE_NAME_LENGTH -> R.string.workspace_name_error_too_long
        existingNames.any { existing ->
            existing.equals(trimmed, ignoreCase = true) &&
                (preset == null || !existing.equals(preset, ignoreCase = true))
        } -> R.string.workspace_name_error_duplicate

        else -> null
    }
}

private fun openAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }
}

@Composable
fun DeleteWorkspaceDialog(
    name: String,
    sessionCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workspace_delete_dialog"),
        title = stringResource(R.string.workspace_delete_title),
        content = {
            Text(
                text = if (sessionCount > 0) {
                    stringResource(R.string.workspace_delete_message, name, sessionCount)
                } else {
                    stringResource(R.string.workspace_delete_message_empty, name)
                },
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.workspace_delete_confirm),
                onClick = onConfirm,
                modifier = Modifier.testTag("workspace_delete_confirm"),
                color = colors.danger,
            )
        },
    )
}
