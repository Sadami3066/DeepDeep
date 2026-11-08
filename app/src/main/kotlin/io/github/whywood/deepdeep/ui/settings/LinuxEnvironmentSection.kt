package io.github.whywood.deepdeep.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
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
    val failed = state as? LinuxInstallState.Failed
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            DdHairline()
        }
        if (installing) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_linux_installing),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            DdHairline()
        }
        if (!installed && !installing) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (failed != null) {
                    Text(
                        text = stringResource(R.string.settings_linux_status_failed, failed.message),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                DdPrimaryButton(
                    text = stringResource(R.string.settings_linux_install),
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth().testTag("linux_install"),
                    enabled = supported,
                )
            }
            DdHairline()
        }
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
            DdListRow(
                icon = DdIcons.Folder,
                title = stringResource(R.string.settings_linux_mounts),
                subtitle = if (mounts.isEmpty()) {
                    stringResource(R.string.settings_linux_mounts_empty)
                } else {
                    stringResource(R.string.settings_linux_mount_count, mounts.size)
                },
                subtitleMaxLines = 2,
            )
            mounts.forEach { mount ->
                DdListRow(
                    icon = DdIcons.FolderOpen,
                    title = mount.name,
                    subtitle = mount.description.ifBlank { null },
                    subtitleMaxLines = 2,
                    value = mountDisplayPath(mount.path),
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
        DdDialog(
            onDismissRequest = { editorOpen = false },
            title = stringResource(
                if (editingMount == null) {
                    R.string.settings_linux_mount_add
                } else {
                    R.string.settings_linux_mount_edit
                },
            ),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DdTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = stringResource(R.string.settings_linux_mount_name_hint),
                        singleLine = true,
                        testTag = "linux_mount_name",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (draftPath.isEmpty()) {
                                stringResource(R.string.settings_linux_mount_choose)
                            } else {
                                mountDisplayPath(draftPath)
                            },
                            modifier = Modifier.weight(1f),
                            style = DdType.body,
                            color = if (draftPath.isEmpty()) colors.inkTertiary else colors.ink,
                        )
                        DdTextAction(
                            text = stringResource(
                                if (draftPath.isEmpty()) {
                                    R.string.settings_linux_mount_choose
                                } else {
                                    R.string.settings_linux_mount_change
                                },
                            ),
                            onClick = { pickDirectory.launch(null) },
                        )
                    }
                    DdTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        placeholder = stringResource(R.string.settings_linux_mount_description_hint),
                        singleLine = false,
                        testTag = "linux_mount_description",
                    )
                    draftError?.let { message ->
                        Text(text = stringResource(message), style = DdType.bodySmall, color = colors.danger)
                    }
                }
            },
            actions = {
                if (editingMount != null) {
                    DdTextAction(
                        text = stringResource(R.string.settings_linux_mount_remove),
                        onClick = {
                            onRemoveMount(editingMount!!.id)
                            editorOpen = false
                        },
                        color = colors.danger,
                    )
                }
                DdTextAction(
                    text = stringResource(R.string.settings_linux_mount_cancel),
                    onClick = { editorOpen = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.settings_linux_mount_save),
                    onClick = { saveDraft() },
                )
            },
        )
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

private fun openSharedStorageSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
}
