package io.github.whywood.deepdeep.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.StorageMount
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.data.mountDisplayPath
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.shell.LinuxInstallState

@Composable
internal fun LinuxEnvironmentSection(
    state: LinuxInstallState,
    supported: Boolean,
    enabled: Boolean,
    sharedStorageEnabled: Boolean,
    onInstall: () -> Unit,
    onReset: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSharedStorageChange: (Boolean) -> Unit,
    mounts: List<StorageMount>,
    onSaveMount: (StorageMount) -> Unit,
    onRemoveMount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val installedState = state as? LinuxInstallState.Installed
    val installed = installedState != null
    val installing = state is LinuxInstallState.Installing
    val available = installed && supported && enabled
    var resetConfirm by remember { mutableStateOf(false) }
    var sharedStorageGranted by remember { mutableStateOf(isSharedStorageGranted()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingMount by remember { mutableStateOf<StorageMount?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftDescription by remember { mutableStateOf("") }
    var draftPath by remember { mutableStateOf("") }
    var draftError by remember { mutableStateOf<Int?>(null) }

    val pickDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val path = publicStoragePath(uri.authority, documentId)
        if (path == null) {
            draftError = R.string.settings_linux_mount_invalid_path
        } else {
            draftPath = path
            if (draftName.isBlank()) draftName = folderName(path)
            draftError = null
        }
    }

    LifecycleResumeEffect(Unit) {
        sharedStorageGranted = isSharedStorageGranted()
        onPauseOrDispose {}
    }

    fun openCreate() {
        if (!isSharedStorageGranted()) {
            openSharedStorageSettings(context)
            return
        }
        editingMount = null
        draftName = ""
        draftDescription = ""
        draftPath = ""
        draftError = null
        editorOpen = true
    }

    fun openEdit(mount: StorageMount) {
        editingMount = mount
        draftName = mount.name
        draftDescription = mount.description
        draftPath = mount.path
        draftError = null
        editorOpen = true
    }

    fun saveDraft() {
        val name = draftName.trim()
        when {
            name.isEmpty() -> draftError = R.string.settings_linux_mount_name_required
            draftPath.isBlank() -> draftError = R.string.settings_linux_mount_choose_required
            else -> {
                onSaveMount(
                    StorageMount(
                        id = editingMount?.id.orEmpty(),
                        name = name,
                        description = draftDescription.trim(),
                        path = draftPath,
                    ),
                )
                editorOpen = false
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        if (!supported) {
            Text(
                text = stringResource(R.string.settings_linux_unsupported),
                modifier = Modifier.padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Md),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            DdHairline()
        }
        val retryAvailable = supported && !installed && !installing
        DdListRow(
            title = stringResource(R.string.settings_linux_builtin),
            subtitle = when (state) {
                LinuxInstallState.NotInstalled -> stringResource(R.string.settings_linux_status_not_installed)
                LinuxInstallState.Installing -> stringResource(R.string.settings_linux_status_installing)
                is LinuxInstallState.Installed -> stringResource(R.string.settings_linux_status_installed, state.version)
                is LinuxInstallState.Failed -> stringResource(R.string.settings_linux_status_failed)
            },
            onClick = if (retryAvailable) onInstall else null,
            showChevron = retryAvailable,
            modifier = Modifier.testTag("linux_builtin"),
            trailing = if (installing) {
                { DdSpinner(size = 16.dp, strokeWidth = 2.dp) }
            } else {
                null
            },
        )
        DdHairline()
        DdListRow(
            title = stringResource(R.string.settings_linux_enable),
            enabled = installed && supported,
            trailing = {
                DdSwitch(
                    checked = enabled && installed,
                    onCheckedChange = onEnabledChange,
                    enabled = installed && supported,
                    stateDescription = stringResource(
                        if (enabled && installed) {
                            R.string.settings_switch_on
                        } else {
                            R.string.settings_switch_off
                        },
                    ),
                    modifier = Modifier.testTag("linux_enable_switch"),
                )
            },
        )
        DdHairline()
        SharedStorageRow(
            granted = sharedStorageGranted,
            checked = sharedStorageEnabled,
            available = available,
            onCheckedChange = onSharedStorageChange,
        )
        if (available) {
            DdHairline()
            Row(
                Modifier.fillMaxWidth().padding(start = DdSpacing.Lg, end = DdSpacing.Lg, top = 14.dp, bottom = DdSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdSectionLabel(
                    text = stringResource(R.string.settings_linux_mounts),
                    modifier = Modifier.weight(1f),
                )
                if (mounts.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_linux_mount_count, mounts.size),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }
            if (mounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_linux_mounts_empty),
                    modifier = Modifier.padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Xs),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            mounts.forEach { mount ->
                DdListRow(
                    icon = DdIcons.Folder,
                    title = mount.name,
                    subtitle = mount.description.ifBlank { null },
                    subtitleMaxLines = 2,
                    onClick = { openEdit(mount) },
                    showChevron = true,
                    modifier = Modifier.testTag("linux_mount_${mount.id}"),
                )
            }
            DdListRow(
                icon = DdIcons.Plus,
                title = stringResource(R.string.settings_linux_mount_add),
                subtitle = if (sharedStorageGranted) null else stringResource(R.string.settings_linux_mount_add_grant),
                subtitleMaxLines = 2,
                onClick = { openCreate() },
                modifier = Modifier.testTag("linux_mount_add"),
            )
        }
        if (installedState != null) {
            DdHairline()
            val sizeBytes = installedState.sizeBytes
            DdListRow(
                title = stringResource(R.string.settings_linux_size_label),
                value = if (sizeBytes == null) {
                    stringResource(R.string.settings_linux_size_computing)
                } else {
                    stringResource(R.string.settings_linux_size_value, (sizeBytes / (1024 * 1024)).toInt())
                },
            )
            DdHairline()
            DdListRow(
                icon = DdIcons.Refresh,
                title = stringResource(R.string.settings_linux_reset),
                onClick = { resetConfirm = true },
                showChevron = true,
                modifier = Modifier.testTag("linux_reset"),
            )
        }
    }

    if (resetConfirm) {
        DdDialog(
            onDismissRequest = { resetConfirm = false },
            title = stringResource(R.string.settings_linux_reset_title),
            content = {
                Text(
                    text = stringResource(R.string.settings_linux_reset_dialog),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.settings_linux_cancel),
                    onClick = { resetConfirm = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.settings_linux_reset_confirm),
                    onClick = {
                        resetConfirm = false
                        onReset()
                    },
                )
            },
        )
    }

    if (editorOpen) {
        DdSheet(onDismiss = { editorOpen = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DdSpacing.ScreenHorizontal),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DdIconButton(
                        icon = DdIcons.Close,
                        onClick = { editorOpen = false },
                        contentDescription = stringResource(R.string.dev_close),
                        tint = colors.inkSecondary,
                        iconSize = 20.dp,
                    )
                }
                Text(
                    text = stringResource(
                        if (editingMount == null) {
                            R.string.settings_linux_mount_add_title
                        } else {
                            R.string.settings_linux_mount_edit_title
                        },
                    ),
                    style = DdType.heading,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.settings_linux_mount_subtitle),
                    modifier = Modifier.padding(top = DdSpacing.Xs, bottom = DdSpacing.Xl),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
                Text(
                    text = stringResource(R.string.settings_linux_mount_section),
                    modifier = Modifier.padding(bottom = DdSpacing.Sm),
                    style = DdType.label,
                    color = colors.ink,
                )
                val cardShape = DdShapes.Large
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .border(1.dp, colors.hairline, cardShape)
                        .padding(horizontal = DdSpacing.Lg, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DdIcon(icon = DdIcons.Folder, tint = colors.accent, size = 26.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (draftPath.isEmpty()) {
                                stringResource(R.string.settings_linux_mount_choose)
                            } else {
                                folderName(draftPath)
                            },
                            style = DdType.body,
                            color = if (draftPath.isEmpty()) colors.inkTertiary else colors.ink,
                        )
                        if (draftPath.isNotEmpty()) {
                            Text(
                                text = displayMountPath(draftPath),
                                style = DdType.bodySmall,
                                color = colors.inkTertiary,
                            )
                        }
                    }
                    DdTextAction(
                        text = stringResource(
                            if (draftPath.isEmpty()) {
                                R.string.settings_linux_mount_pick
                            } else {
                                R.string.settings_linux_mount_change
                            },
                        ),
                        onClick = { pickDirectory.launch(null) },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_linux_mount_name_label),
                    modifier = Modifier.padding(top = DdSpacing.Xl, bottom = DdSpacing.Sm),
                    style = DdType.label,
                    color = colors.ink,
                )
                DdTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    placeholder = stringResource(R.string.settings_linux_mount_name_hint),
                    singleLine = true,
                    testTag = "linux_mount_name",
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = DdSpacing.Xl, bottom = DdSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_linux_mount_description_label),
                        style = DdType.label,
                        color = colors.ink,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_linux_mount_optional),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                DdTextArea(
                    value = draftDescription,
                    onValueChange = { draftDescription = it },
                    placeholder = stringResource(R.string.settings_linux_mount_description_hint),
                    minHeight = 96.dp,
                    maxHeight = 180.dp,
                    testTag = "linux_mount_description",
                )
                draftError?.let { message ->
                    Text(
                        text = stringResource(message),
                        modifier = Modifier.padding(top = 10.dp),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(DdSpacing.Xl))
                DdPrimaryButton(
                    text = stringResource(
                        if (editingMount == null) {
                            R.string.settings_linux_mount_add_confirm
                        } else {
                            R.string.settings_linux_mount_save
                        },
                    ),
                    onClick = { saveDraft() },
                    modifier = Modifier.fillMaxWidth().testTag("linux_mount_save"),
                    enabled = draftName.isNotBlank() && draftPath.isNotBlank(),
                )
                if (editingMount != null) {
                    Box(Modifier.fillMaxWidth().padding(top = DdSpacing.Xs), contentAlignment = Alignment.Center) {
                        DdTextAction(
                            text = stringResource(R.string.settings_linux_mount_remove),
                            onClick = {
                                onRemoveMount(editingMount!!.id)
                                editorOpen = false
                            },
                            color = colors.danger,
                        )
                    }
                }
                Spacer(Modifier.height(DdSpacing.Md))
            }
        }
    }
}

@Composable
private fun SharedStorageRow(
    granted: Boolean,
    checked: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    DdListRow(
        title = stringResource(R.string.settings_linux_shared_storage),
        subtitle = if (granted) null else stringResource(R.string.settings_linux_shared_storage_grant),
        subtitleMaxLines = 2,
        enabled = available,
        onClick = if (granted) null else ({ openSharedStorageSettings(context) }),
        showChevron = !granted,
        modifier = Modifier.testTag("linux_shared_storage"),
        trailing = if (granted) {
            {
                DdSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = available,
                    stateDescription = stringResource(
                        if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
                    ),
                    modifier = Modifier.testTag("linux_shared_storage_switch"),
                )
            }
        } else {
            null
        },
    )
}

private fun folderName(path: String): String =
    path.trimEnd('/').substringAfterLast('/')

private fun displayMountPath(path: String): String =
    mountDisplayPath(path).split('/').filter { it.isNotBlank() }.joinToString(" / ")

private fun openSharedStorageSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
}
