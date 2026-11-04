package io.github.whywood.deepdeep.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdChip
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.scan.ScanLaunch
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategy
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyJson
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyStore
import io.github.whywood.deepdeep.ui.common.scan.scanAppInfo

@Composable
fun ScanSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val store = remember(context) { ScanStrategyStore(context) }
    val initial = remember(store) { store.load() }
    var strategies by remember { mutableStateOf(initial.strategies) }
    var error by remember { mutableStateOf(initial.error) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var resetOpen by remember { mutableStateOf(false) }

    fun persist(next: List<ScanStrategy>) {
        runCatching { store.save(next) }.onFailure {
            Toast.makeText(context, R.string.scan_settings_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        strategies = next
        error = null
        Toast.makeText(context, R.string.scan_settings_saved, Toast.LENGTH_SHORT).show()
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
        },
        modifier = modifier,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_settings_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = DdType.bodySmall,
                    color = colors.warning,
                )
                Spacer(Modifier.height(10.dp))
            }
            DdGroupCard {
                strategies.forEachIndexed { index, strategy ->
                    if (index > 0) DdHairline()
                    StrategyRow(strategy = strategy, onClick = {
                        editingIndex = index
                        editorOpen = true
                    })
                }
                if (strategies.isNotEmpty()) DdHairline()
                DdListRow(
                    icon = DdIcons.Plus,
                    title = stringResource(R.string.scan_settings_add),
                    onClick = {
                        editingIndex = null
                        editorOpen = true
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("scan_settings_add"),
                )
            }
            Spacer(Modifier.height(16.dp))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.File,
                    title = stringResource(R.string.scan_settings_file),
                    subtitle = stringResource(R.string.scan_settings_file_hint),
                    subtitleMaxLines = 2,
                    value = null,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("scan-strategies", store.file.absolutePath))
                        Toast.makeText(context, R.string.scan_settings_file_copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("scan_settings_file"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Refresh,
                    title = stringResource(R.string.scan_settings_restore),
                    onClick = { resetOpen = true },
                    modifier = Modifier.testTag("scan_settings_restore"),
                )
            }
            Spacer(Modifier.height(28.dp))
            if (strategies.isEmpty()) {
                Text(
                    text = stringResource(R.string.scan_settings_empty),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (editorOpen) {
        StrategyEditorDialog(
            existing = editingIndex?.let { strategies.getOrNull(it) },
            onDismiss = { editorOpen = false },
            onSave = { updated ->
                val next = strategies.toMutableList()
                val index = editingIndex
                if (index == null) next += updated else next[index] = updated
                persist(next)
                editorOpen = false
            },
            onDelete = editingIndex?.takeIf { strategies.size > it }?.let { index ->
                {
                    val next = strategies.toMutableList()
                    next.removeAt(index)
                    persist(next)
                    editorOpen = false
                }
            },
        )
    }

    if (resetOpen) {
        DdDialog(
            onDismissRequest = { resetOpen = false },
            title = stringResource(R.string.scan_settings_restore_title),
            content = {
                Text(
                    text = stringResource(R.string.scan_settings_restore_message),
                    style = DdType.body,
                    color = deepDeepColors().inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.scan_settings_cancel),
                    onClick = { resetOpen = false },
                    color = deepDeepColors().inkSecondary,
                )
                Spacer(Modifier.weight(1f))
                DdTextAction(
                    text = stringResource(R.string.scan_settings_restore_confirm),
                    onClick = {
                        store.reset()
                        strategies = store.load().strategies
                        error = null
                        resetOpen = false
                        Toast.makeText(context, R.string.scan_settings_saved, Toast.LENGTH_SHORT).show()
                    },
                    color = deepDeepColors().danger,
                )
            },
        )
    }
}

@Composable
private fun StrategyRow(strategy: ScanStrategy, onClick: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(strategy.packageName) {
        runCatching { context.packageManager.getApplicationInfo(strategy.packageName, 0) }.isSuccess
    }
    val info = remember(strategy.packageName) { scanAppInfo(context, strategy.packageName) }
    DdListRow(
        title = info.label,
        subtitle = if (installed) strategy.packageName else stringResource(R.string.scan_settings_not_installed),
        onClick = onClick,
        showChevron = true,
    )
}

@Composable
private fun StrategyEditorDialog(
    existing: ScanStrategy?,
    onDismiss: () -> Unit,
    onSave: (ScanStrategy) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = deepDeepColors()
    val existingLaunch = existing?.launch as? ScanLaunch.Component
    var packageName by remember { mutableStateOf(existing?.packageName.orEmpty()) }
    var hosts by remember { mutableStateOf(existing?.hosts.orEmpty().joinToString(", ")) }
    var schemes by remember { mutableStateOf(existing?.schemes.orEmpty().joinToString(", ")) }
    var contains by remember { mutableStateOf(existing?.contains.orEmpty().joinToString(", ")) }
    var regex by remember { mutableStateOf(existing?.regexes.orEmpty().joinToString("\n") { it.pattern }) }
    var uriKind by remember { mutableStateOf(existing?.launch !is ScanLaunch.Component) }
    var uri by remember {
        mutableStateOf((existing?.launch as? ScanLaunch.Uri)?.uri.orEmpty())
    }
    var activity by remember { mutableStateOf(existingLaunch?.activity.orEmpty()) }
    var action by remember { mutableStateOf(existingLaunch?.action.orEmpty()) }
    var extras by remember {
        mutableStateOf(existingLaunch?.extras?.let { ScanStrategyJson.extrasText(it) }.orEmpty())
    }
    var fallbackMain by remember { mutableStateOf(existing?.fallbackMain ?: true) }
    var pickerOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<EditorError?>(null) }

    fun build(): ScanStrategy? {
        if (packageName.isBlank()) {
            error = EditorError.Package
            return null
        }
        val launch = if (uriKind) {
            if (uri.isBlank()) {
                error = EditorError.Action
                return null
            }
            ScanLaunch.Uri(uri.trim())
        } else {
            if (activity.isBlank()) {
                error = EditorError.Action
                return null
            }
            val parsedExtras = ScanStrategyJson.parseExtrasText(extras)
            if (parsedExtras == null) {
                error = EditorError.Extras
                return null
            }
            ScanLaunch.Component(
                activity = activity.trim(),
                action = action.trim().takeIf { it.isNotEmpty() },
                extras = parsedExtras,
                flags = existingLaunch?.flags.orEmpty(),
            )
        }
        val regexes = mutableListOf<Regex>()
        for (raw in regex.split('\n').map { it.trim() }.filter { it.isNotEmpty() }) {
            val compiled = runCatching { Regex(raw, RegexOption.IGNORE_CASE) }.getOrNull()
            if (compiled == null) {
                error = EditorError.RegexPattern(raw)
                return null
            }
            regexes += compiled
        }
        return ScanStrategy(
            packageName = packageName.trim(),
            hosts = splitList(hosts).map { it.lowercase() },
            schemes = splitList(schemes).map { it.lowercase() },
            contains = splitList(contains).map { it.lowercase() },
            regexes = regexes,
            priority = existing?.priority ?: 0,
            fallbackMain = fallbackMain,
            launch = launch,
            note = existing?.note,
        )
    }

    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (existing == null) R.string.scan_settings_new else R.string.scan_settings_edit,
        ),
        content = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FormField(stringResource(R.string.scan_settings_field_package), packageName, { packageName = it }, "com.example.app")
                DdTextAction(
                    text = stringResource(R.string.scan_settings_pick_app),
                    onClick = { pickerOpen = true },
                    modifier = Modifier.testTag("scan_settings_pick_app"),
                )
                Spacer(Modifier.height(8.dp))
                FormField(stringResource(R.string.scan_settings_field_host), hosts, { hosts = it }, "example.com, example.cn")
                FormField(stringResource(R.string.scan_settings_field_scheme), schemes, { schemes = it }, "example")
                FormField(stringResource(R.string.scan_settings_field_contains), contains, { contains = it }, "brand")
                FormField(stringResource(R.string.scan_settings_field_regex), regex, { regex = it }, "每行一条", singleLine = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DdChip(
                        text = stringResource(R.string.scan_settings_kind_uri),
                        onClick = { uriKind = true },
                        selected = uriKind,
                    )
                    Spacer(Modifier.width(8.dp))
                    DdChip(
                        text = stringResource(R.string.scan_settings_kind_component),
                        onClick = { uriKind = false },
                        selected = !uriKind,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (uriKind) {
                    FormField(stringResource(R.string.scan_settings_field_uri), uri, { uri = it }, "https://… 或 scheme://…")
                } else {
                    FormField(stringResource(R.string.scan_settings_field_activity), activity, { activity = it }, "com.example.app.ScanActivity")
                    FormField(stringResource(R.string.scan_settings_field_action), action, { action = it }, "android.intent.action.VIEW")
                    FormField(stringResource(R.string.scan_settings_field_extras), extras, { extras = it }, "{ \"key\": true }", singleLine = false)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.scan_settings_field_fallback),
                        style = DdType.body,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    DdSwitch(checked = fallbackMain, onCheckedChange = { fallbackMain = it })
                }
                val currentError = error
                if (currentError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = editorErrorText(currentError),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
            }
        },
        actions = {
            if (onDelete != null) {
                DdTextAction(
                    text = stringResource(R.string.scan_settings_delete),
                    onClick = onDelete,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.weight(1f))
            DdTextAction(
                text = stringResource(R.string.scan_settings_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.scan_settings_save),
                onClick = {
                    val built = build() ?: return@DdTextAction
                    onSave(built)
                },
                color = colors.accent,
            )
        },
    )

    if (pickerOpen) {
        AppPickerDialog(
            onSelect = {
                packageName = it
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    Text(
        text = label,
        style = DdType.bodySmall,
        color = deepDeepColors().inkTertiary,
    )
    Spacer(Modifier.height(6.dp))
    DdTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = singleLine,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun AppPickerDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember { installedApps(context.packageManager) }
    val filtered = remember(query, apps) {
        if (query.isBlank()) {
            apps
        } else {
            val keyword = query.lowercase()
            apps.filter { it.label.lowercase().contains(keyword) || it.packageName.contains(keyword) }
        }
    }
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.scan_settings_app_picker_title),
        content = {
            Column(Modifier.fillMaxWidth()) {
                DdSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.scan_settings_app_search),
                    clearContentDescription = stringResource(R.string.scan_settings_cancel),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        DdListRow(
                            title = app.label,
                            subtitle = app.packageName,
                            onClick = { onSelect(app.packageName) },
                        )
                    }
                }
            }
        },
        actions = {
            Spacer(Modifier.weight(1f))
            DdTextAction(
                text = stringResource(R.string.scan_settings_cancel),
                onClick = onDismiss,
                color = deepDeepColors().inkSecondary,
            )
        },
    )
}

private data class InstalledApp(val label: String, val packageName: String)

private fun installedApps(packageManager: PackageManager): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { it.activityInfo?.applicationInfo }
        .distinctBy { it.packageName }
        .map { appInfo ->
            InstalledApp(
                label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(appInfo.packageName),
                packageName = appInfo.packageName,
            )
        }
        .sortedBy { it.label.lowercase() }
}

private fun splitList(text: String): List<String> =
    text.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

private sealed interface EditorError {
    data object Package : EditorError
    data object Action : EditorError
    data object Extras : EditorError
    data class RegexPattern(val pattern: String) : EditorError
}

@Composable
private fun editorErrorText(error: EditorError): String = when (error) {
    EditorError.Package -> stringResource(R.string.scan_settings_invalid_package)
    EditorError.Action -> stringResource(R.string.scan_settings_invalid_action)
    EditorError.Extras -> stringResource(R.string.scan_settings_invalid_extras)
    is EditorError.RegexPattern -> stringResource(R.string.scan_settings_invalid_regex, error.pattern)
}
