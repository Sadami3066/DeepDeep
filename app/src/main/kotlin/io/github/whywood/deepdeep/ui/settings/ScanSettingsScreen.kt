package io.github.whywood.deepdeep.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdChip
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.shareChooser
import io.github.whywood.deepdeep.ui.common.scan.ScanLaunch
import io.github.whywood.deepdeep.ui.common.scan.ScanLauncher
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategy
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyIssue
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyJson
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyRepository
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyStore
import io.github.whywood.deepdeep.ui.common.scan.scanAppInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScanSettingsScreen(
    onBack: () -> Unit,
    onOpenJsonEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val store = remember(context) { ScanStrategyStore(context) }
    var snapshot by remember { mutableStateOf<ScanStrategyRepository.Snapshot?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var officialDetail by remember { mutableStateOf<ScanStrategy?>(null) }
    var restoreOpen by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        snapshot = withContext(Dispatchers.IO) { store.load(force = true) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(context, R.string.scan_settings_import_failed, Toast.LENGTH_SHORT).show()
            } else {
                val result = store.importCustom(text)
                if (result.count == null) {
                    Toast.makeText(context, R.string.scan_settings_import_failed, Toast.LENGTH_SHORT).show()
                } else {
                    snapshot = store.load(force = true)
                    Toast.makeText(context, R.string.scan_settings_imported, Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.scan_settings_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            val loaded = snapshot
            if (loaded == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = DdSpacing.Xxl), contentAlignment = Alignment.Center) {
                    DdSpinner()
                }
            } else {
                val issue = loaded.issue
                if (issue != null) {
                    Text(
                        text = issueText(issue),
                        style = DdType.bodySmall,
                        color = colors.warning,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                DdSectionLabel(stringResource(R.string.scan_settings_official))
                Spacer(Modifier.height(6.dp))
                DdGroupCard {
                    loaded.official.forEachIndexed { index, strategy ->
                        if (index > 0) DdHairline()
                        StrategyRow(
                            strategy = strategy,
                            onClick = { officialDetail = strategy },
                        )
                    }
                    DdHairline()
                    DdListRow(
                        icon = DdIcons.Refresh,
                        title = stringResource(R.string.scan_settings_restore),
                        onClick = { restoreOpen = true },
                        modifier = Modifier.testTag("scan_settings_restore"),
                    )
                }

                Spacer(Modifier.height(DdSpacing.Card))
                DdSectionLabel(stringResource(R.string.scan_settings_custom))
                Spacer(Modifier.height(6.dp))
                DdGroupCard {
                    if (loaded.custom.isEmpty()) {
                        DdListRow(
                            title = stringResource(R.string.scan_settings_custom_empty),
                            enabled = false,
                        )
                    } else {
                        loaded.custom.forEachIndexed { index, strategy ->
                            if (index > 0) DdHairline()
                            StrategyRow(
                                strategy = strategy,
                                onClick = {
                                    editingIndex = index
                                    editorOpen = true
                                },
                            )
                        }
                    }
                    DdHairline()
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

                Spacer(Modifier.height(DdSpacing.Card))
                DdSectionLabel(stringResource(R.string.scan_settings_file_section))
                Spacer(Modifier.height(6.dp))
                DdGroupCard {
                    DdListRow(
                        icon = DdIcons.File,
                        title = stringResource(R.string.scan_settings_file),
                        subtitle = stringResource(R.string.scan_settings_file_hint),
                        subtitleMaxLines = 2,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("scan-strategies", store.file.absolutePath),
                            )
                            Toast.makeText(context, R.string.scan_settings_file_copied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("scan_settings_file"),
                    )
                    DdHairline()
                    DdListRow(
                        icon = DdIcons.Compose,
                        title = stringResource(R.string.scan_settings_json_editor),
                        onClick = onOpenJsonEditor,
                        showChevron = true,
                        modifier = Modifier.testTag("scan_settings_json"),
                    )
                    DdHairline()
                    DdListRow(
                        icon = DdIcons.Download,
                        title = stringResource(R.string.scan_settings_import),
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("scan_settings_import"),
                    )
                    DdHairline()
                    DdListRow(
                        icon = DdIcons.Share,
                        title = stringResource(R.string.scan_settings_export),
                        onClick = { exportStrategies(context, store) },
                        modifier = Modifier.testTag("scan_settings_export"),
                    )
                }
                Spacer(Modifier.height(DdSpacing.Huge))
            }
        }
    }

    if (editorOpen) {
        StrategyEditorDialog(
            existing = editingIndex?.let { snapshot?.custom?.getOrNull(it) },
            onDismiss = { editorOpen = false },
            onSave = { updated ->
                val next = snapshot?.custom.orEmpty().toMutableList()
                val index = editingIndex
                if (index == null) next += updated else next[index] = updated
                snapshot = store.saveCustom(next)
                editorOpen = false
                Toast.makeText(context, R.string.scan_settings_saved, Toast.LENGTH_SHORT).show()
            },
            onDelete = editingIndex?.takeIf { index -> index < snapshot?.custom.orEmpty().size }?.let { index ->
                {
                    val next = snapshot?.custom.orEmpty().toMutableList()
                    next.removeAt(index)
                    snapshot = store.saveCustom(next)
                    editorOpen = false
                    Toast.makeText(context, R.string.scan_settings_saved, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    officialDetail?.let { strategy ->
        OfficialStrategyDialog(strategy = strategy, onDismiss = { officialDetail = null })
    }

    if (restoreOpen) {
        DdDialog(
            onDismissRequest = { restoreOpen = false },
            title = stringResource(R.string.scan_settings_restore_title),
            content = {
                Text(
                    text = stringResource(R.string.scan_settings_restore_message),
                    style = DdType.body,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.scan_settings_cancel),
                    onClick = { restoreOpen = false },
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.weight(1f))
                DdTextAction(
                    text = stringResource(R.string.scan_settings_restore_confirm),
                    onClick = {
                        val current = snapshot
                        if (current != null) snapshot = store.restoreOfficial(current)
                        restoreOpen = false
                        Toast.makeText(context, R.string.scan_settings_restored, Toast.LENGTH_SHORT).show()
                    },
                    color = colors.danger,
                )
            },
        )
    }
}

@Composable
private fun issueText(issue: ScanStrategyIssue): String = when (issue) {
    ScanStrategyIssue.CorruptCustom -> stringResource(R.string.scan_settings_corrupt)
    ScanStrategyIssue.UnreadableCustom -> stringResource(R.string.scan_settings_unreadable)
    is ScanStrategyIssue.SkippedEntries -> stringResource(R.string.scan_settings_skipped, issue.count)
}

@Composable
private fun StrategyRow(strategy: ScanStrategy, onClick: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(strategy.packageName) {
        runCatching { context.packageManager.getApplicationInfo(strategy.packageName, 0) }.isSuccess
    }
    val info = remember(strategy.packageName) {
        if (installed) scanAppInfo(context, strategy.packageName) else null
    }
    DdListRow(
        title = info?.label ?: strategy.packageName,
        subtitle = if (info != null) strategy.packageName else stringResource(R.string.scan_settings_not_installed),
        onClick = onClick,
        showChevron = true,
    )
}

@Composable
private fun OfficialStrategyDialog(strategy: ScanStrategy, onDismiss: () -> Unit) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    DdDialog(
        onDismissRequest = onDismiss,
        title = remember(strategy.packageName) { scanAppInfo(context, strategy.packageName).label },
        content = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailLine(stringResource(R.string.scan_settings_field_package), strategy.packageName)
                if (strategy.hosts.isNotEmpty()) {
                    DetailLine(stringResource(R.string.scan_settings_field_host), strategy.hosts.joinToString(", "))
                }
                if (strategy.schemes.isNotEmpty()) {
                    DetailLine(stringResource(R.string.scan_settings_field_scheme), strategy.schemes.joinToString(", "))
                }
                if (strategy.contains.isNotEmpty()) {
                    DetailLine(stringResource(R.string.scan_settings_field_contains), strategy.contains.joinToString(", "))
                }
                if (strategy.regexes.isNotEmpty()) {
                    DetailLine(
                        stringResource(R.string.scan_settings_field_regex),
                        strategy.regexes.joinToString("\n") { it.pattern },
                    )
                }
                DetailLine(
                    stringResource(R.string.scan_settings_field_launch),
                    launchDescription(strategy.launch),
                )
                strategy.note?.let { DetailLine(stringResource(R.string.scan_settings_field_note), it) }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.scan_settings_try),
                onClick = {
                    if (ScanLauncher.launchStrategy(context, strategy)) {
                        Toast.makeText(context, R.string.scan_settings_try_opened, Toast.LENGTH_SHORT).show()
                    }
                },
                color = colors.accent,
            )
            Spacer(Modifier.weight(1f))
            DdTextAction(
                text = stringResource(R.string.scan_settings_close),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = deepDeepColors()
    Text(text = label, style = DdType.bodySmall, color = colors.inkTertiary)
    Spacer(Modifier.height(DdSpacing.Xxs))
    Text(text = value, style = DdType.bodySmall, color = colors.ink)
    Spacer(Modifier.height(10.dp))
}

private fun launchDescription(launch: ScanLaunch): String = when (launch) {
    is ScanLaunch.Uri -> launch.uri
    is ScanLaunch.Component -> buildString {
        append(launch.activity)
        launch.action?.let { append("\n").append(it) }
        if (launch.extras.isNotEmpty()) append("\n").append(launch.extras.keys.joinToString(", "))
        if (launch.flags.isNotEmpty()) append("\n").append(launch.flags.joinToString(", "))
    }
}

@Composable
private fun StrategyEditorDialog(
    existing: ScanStrategy?,
    onDismiss: () -> Unit,
    onSave: (ScanStrategy) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val existingLaunch = existing?.launch as? ScanLaunch.Component
    var packageName by remember { mutableStateOf(existing?.packageName.orEmpty()) }
    var hosts by remember { mutableStateOf(existing?.hosts.orEmpty().joinToString(", ")) }
    var schemes by remember { mutableStateOf(existing?.schemes.orEmpty().joinToString(", ")) }
    var contains by remember { mutableStateOf(existing?.contains.orEmpty().joinToString(", ")) }
    var regex by remember { mutableStateOf(existing?.regexes.orEmpty().joinToString("\n") { it.pattern }) }
    var uriKind by remember { mutableStateOf(existing?.launch !is ScanLaunch.Component) }
    var uri by remember { mutableStateOf((existing?.launch as? ScanLaunch.Uri)?.uri.orEmpty()) }
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FormField(stringResource(R.string.scan_settings_field_package), packageName, { packageName = it }, "com.example.app")
                DdTextAction(
                    text = stringResource(R.string.scan_settings_pick_app),
                    onClick = { pickerOpen = true },
                    modifier = Modifier.testTag("scan_settings_pick_app"),
                )
                Spacer(Modifier.height(DdSpacing.Sm))
                FormField(stringResource(R.string.scan_settings_field_host), hosts, { hosts = it }, "example.com, example.cn")
                FormField(stringResource(R.string.scan_settings_field_scheme), schemes, { schemes = it }, "example")
                FormField(stringResource(R.string.scan_settings_field_contains), contains, { contains = it }, "brand")
                FormField(stringResource(R.string.scan_settings_field_regex), regex, { regex = it }, stringResource(R.string.scan_settings_hint_lines), singleLine = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DdChip(
                        text = stringResource(R.string.scan_settings_kind_uri),
                        onClick = { uriKind = true },
                        selected = uriKind,
                    )
                    Spacer(Modifier.width(DdSpacing.Sm))
                    DdChip(
                        text = stringResource(R.string.scan_settings_kind_component),
                        onClick = { uriKind = false },
                        selected = !uriKind,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (uriKind) {
                    FormField(stringResource(R.string.scan_settings_field_uri), uri, { uri = it }, stringResource(R.string.scan_settings_hint_uri))
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
                    Spacer(Modifier.height(DdSpacing.Sm))
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
            DdTextAction(
                text = stringResource(R.string.scan_settings_try),
                onClick = {
                    val built = build() ?: return@DdTextAction
                    if (ScanLauncher.launchStrategy(context, built)) {
                        Toast.makeText(context, R.string.scan_settings_try_opened, Toast.LENGTH_SHORT).show()
                    }
                },
                color = colors.accent,
            )
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
    val packageManager = context.packageManager
    var query by remember { mutableStateOf("") }
    val apps = remember { installedApps(packageManager) }
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
                Spacer(Modifier.height(DdSpacing.Sm))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            packageManager = packageManager,
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

@Composable
private fun AppPickerRow(
    app: InstalledApp,
    packageManager: PackageManager,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val icon = remember(app.packageName) {
        runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }
    DdPressableRow(onClick = onClick, contentDescription = app.label) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DdSpacing.Sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                val bitmap = remember(icon) { icon.toBitmap(96, 96) }
                Image(
                    painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(DdSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(text = app.label, style = DdType.body, color = colors.ink, maxLines = 1)
                Text(text = app.packageName, style = DdType.bodySmall, color = colors.inkTertiary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DdPressableRow(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    io.github.whywood.deepdeep.design.DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DdShapes.Segmented,
        contentDescription = contentDescription,
    ) {
        content()
    }
}

@Composable
private fun editorErrorText(error: EditorError): String = when (error) {
    EditorError.Package -> stringResource(R.string.scan_settings_invalid_package)
    EditorError.Action -> stringResource(R.string.scan_settings_invalid_action)
    EditorError.Extras -> stringResource(R.string.scan_settings_invalid_extras)
    is EditorError.RegexPattern -> stringResource(R.string.scan_settings_invalid_regex, error.pattern)
}

private sealed interface EditorError {
    data object Package : EditorError
    data object Action : EditorError
    data object Extras : EditorError
    data class RegexPattern(val pattern: String) : EditorError
}

private fun exportStrategies(context: Context, store: ScanStrategyStore) {
    val file = store.exportFile()
    if (file == null) {
        Toast.makeText(context, R.string.scan_settings_export_failed, Toast.LENGTH_SHORT).show()
        return
    }
    val shared = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(shareChooser(context, intent, context.getString(R.string.scan_settings_export)))
    }.isSuccess
    if (!shared) file.delete()
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
