package io.github.whywood.deepdeep.ui.plugins

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.host.update.PluginRepositorySettings
import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginUpdateUi
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PluginsScreen(
    manager: PluginManagerFacade,
    onBack: () -> Unit,
    onOpenPluginPage: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    pluginsRoot: File? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resolvedPluginsRoot = pluginsRoot ?: remember(context) { File(context.filesDir, "plugins") }
    val holder = remember(manager) { PluginsStateHolder(manager, scope) }
    val state by manager.state.collectAsStateWithLifecycle()
    val query by holder.query.collectAsStateWithLifecycle()
    val togglingIds by holder.togglingIds.collectAsStateWithLifecycle()
    var inspection by remember { mutableStateOf<ExternalInspectionUi?>(null) }
    var trustPublisher by remember { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Boolean?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var updateBusyId by remember { mutableStateOf<String?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var stagedUpdateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var updates by remember { mutableStateOf<List<PluginUpdateUi>?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var sourceSettings by remember { mutableStateOf<PluginRepositorySettings?>(null) }
    var uninstallTarget by remember { mutableStateOf<PluginSnapshot?>(null) }
    var infoTarget by remember { mutableStateOf<PluginSnapshot?>(null) }
    var menuPluginId by remember { mutableStateOf<String?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { copyToCache(context, uri) } ?: return@launch
            inspection = holder.inspectExternal(file)
            trustPublisher = false
            importError = null
        }
    }

    LaunchedEffect(Unit) { holder.load() }
    LifecycleResumeEffect(holder) {
        holder.load()
        onPauseOrDispose { }
    }

    PluginsContent(
        state = state,
        query = query,
        togglingIds = togglingIds,
        pluginsRoot = resolvedPluginsRoot,
        menuPluginId = menuPluginId,
        updateBusy = updateBusy,
        onBack = onBack,
        onQueryChange = holder::setQuery,
        onRefresh = holder::refresh,
        onOpenPluginPage = onOpenPluginPage,
        onToggle = holder::setEnabled,
        onToggleMenu = { pluginId -> menuPluginId = if (menuPluginId == pluginId) null else pluginId },
        onDismissMenu = { menuPluginId = null },
        onRetryActivation = { pluginId ->
            menuPluginId = null
            scope.launch { holder.retryActivation(pluginId) }
        },
        onRollback = { pluginId ->
            menuPluginId = null
            scope.launch { holder.rollback(pluginId) }
        },
        onOpenInfo = { snapshot ->
            menuPluginId = null
            infoTarget = snapshot
        },
        onUninstall = { snapshot ->
            menuPluginId = null
            uninstallTarget = snapshot
        },
        onCheckPluginUpdates = { pluginId ->
            menuPluginId = null
            scope.launch {
                updateBusyId = pluginId
                updateError = null
                val all = runCatching { holder.checkExternalUpdates() }.getOrElse { emptyList() }
                stagedUpdateIds = emptySet()
                updates = all.filter { it.pluginId == pluginId }
                updateBusyId = null
            }
        },
        onImport = { importPicker.launch(arrayOf("*/*")) },
        onShowUpdates = { updates = state.availableUpdates },
        onOpenSource = { scope.launch { sourceSettings = holder.loadRepositorySettings() } },
        onCheckUpdates = {
            scope.launch {
                updateBusy = true
                updates = runCatching { holder.checkExternalUpdates() }.getOrDefault(emptyList())
                updateError = null
                stagedUpdateIds = emptySet()
                updateBusy = false
            }
        },
        modifier = modifier,
    )

    inspection?.let { current ->
        PluginImportDialog(
            inspection = current,
            trustPublisher = trustPublisher,
            busy = importBusy,
            error = importError,
            onTrustChange = { trustPublisher = it },
            onConfirm = {
                scope.launch {
                    importBusy = true
                    importError = null
                    val result = holder.installExternal(current.file, current.trusted || trustPublisher)
                    importBusy = false
                    when {
                        result.success -> {
                            inspection = null
                            importResult = result.restartRequired
                        }

                        result.requiresConfirmation -> trustPublisher = true
                        else -> importError = result.error ?: result.toString()
                    }
                }
            },
            onDismiss = { inspection = null },
        )
    }

    importResult?.let { restartRequired ->
        PluginImportResultDialog(restartRequired = restartRequired, onDismiss = { importResult = null })
    }

    updates?.let { list ->
        PluginUpdatesDialog(
            updates = list,
            busyId = updateBusyId,
            stagedIds = stagedUpdateIds,
            error = updateError,
            onUpdate = { pluginId ->
                scope.launch {
                    updateBusyId = pluginId
                    updateError = null
                    val failure = holder.stageUpdate(pluginId)
                    updateBusyId = null
                    if (failure == null) {
                        stagedUpdateIds = stagedUpdateIds + pluginId
                    } else {
                        updateError = failure
                    }
                }
            },
            onDismiss = { updates = null },
        )
    }

    sourceSettings?.let { settings ->
        PluginRepositoryDialog(
            settings = settings,
            onSave = { updated ->
                holder.saveRepositorySettings(updated)
                sourceSettings = null
            },
            onDismiss = { sourceSettings = null },
        )
    }

    uninstallTarget?.let { target ->
        DdDialog(
            onDismissRequest = { uninstallTarget = null },
            title = stringResource(R.string.plugins_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.plugins_delete_message, target.name),
                    style = DdType.bodySmall,
                    color = deepDeepColors().inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.plugins_cancel),
                    onClick = { uninstallTarget = null },
                    color = deepDeepColors().inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.plugins_delete_confirm),
                    onClick = {
                        uninstallTarget = null
                        holder.uninstall(target.id)
                    },
                    color = deepDeepColors().danger,
                )
            },
        )
    }

    infoTarget?.let { target ->
        PluginInfoDialog(snapshot = target, onDismiss = { infoTarget = null })
    }
}

@Composable
private fun PluginsContent(
    state: PluginsUiState,
    query: String,
    togglingIds: Set<String>,
    pluginsRoot: File?,
    menuPluginId: String?,
    updateBusy: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenPluginPage: (String, String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onToggleMenu: (String) -> Unit,
    onDismissMenu: () -> Unit,
    onRetryActivation: (String) -> Unit,
    onRollback: (String) -> Unit,
    onOpenInfo: (PluginSnapshot) -> Unit,
    onUninstall: (PluginSnapshot) -> Unit,
    onCheckPluginUpdates: (String) -> Unit,
    onImport: () -> Unit,
    onCheckUpdates: () -> Unit,
    onShowUpdates: () -> Unit,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val visible = filterPlugins(state.plugins, query)
    val interactive = pluginListInteractive(state)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugins_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.plugins_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.plugins_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            DdIconButton(
                icon = DdIcons.Refresh,
                onClick = onRefresh,
                contentDescription = stringResource(R.string.plugins_refresh),
                modifier = Modifier.testTag("plugins_refresh"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        DdSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.plugins_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "plugins_search_field",
            clearTestTag = "plugins_search_clear",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            PluginGroup {
                DdListRow(
                    title = stringResource(R.string.plugins_import_title),
                    subtitle = stringResource(R.string.plugins_import_pick),
                    onClick = onImport,
                    showChevron = true,
                    modifier = Modifier.testTag("plugins_import"),
                )
                DdHairline()
                if (state.availableUpdates.isNotEmpty()) {
                    DdListRow(
                        title = stringResource(
                            R.string.plugins_updates_available,
                            state.availableUpdates.size,
                        ),
                        onClick = onShowUpdates,
                        showChevron = true,
                        modifier = Modifier.testTag("plugins_updates_available"),
                    )
                    DdHairline()
                }
                DdListRow(
                    title = stringResource(R.string.plugins_source_row),
                    onClick = onOpenSource,
                    showChevron = true,
                    modifier = Modifier.testTag("plugins_source"),
                )
                DdHairline()
                DdListRow(
                    title = stringResource(R.string.plugins_check_updates),
                    onClick = if (updateBusy) null else onCheckUpdates,
                    showChevron = !updateBusy,
                    enabled = !updateBusy,
                    trailing = if (updateBusy) {
                        {
                            DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("plugins_check_updates"),
                )
            }
            Spacer(Modifier.height(14.dp))
            if (!pluginCatalogReady(state)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .testTag("plugins_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                }
            } else if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.plugins_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag("plugins_empty"),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            visible.forEach { snapshot ->
                PluginRow(
                    snapshot = snapshot,
                    pluginsRoot = pluginsRoot,
                    interactive = interactive && snapshot.id !in togglingIds,
                    menuExpanded = menuPluginId == snapshot.id,
                    onOpenPage = {
                        snapshot.pages.firstOrNull()?.let { page -> onOpenPluginPage(snapshot.id, page.id) }
                    },
                    onToggle = { enabled -> onToggle(snapshot.id, enabled) },
                    onToggleMenu = { onToggleMenu(snapshot.id) },
                    onDismissMenu = onDismissMenu,
                    onRetryActivation = { onRetryActivation(snapshot.id) },
                    onRollback = { onRollback(snapshot.id) },
                    onOpenInfo = { onOpenInfo(snapshot) },
                    onUninstall = { onUninstall(snapshot) },
                    onCheckUpdates = { onCheckPluginUpdates(snapshot.id) },
                )
                Spacer(Modifier.height(4.dp))
            }
            state.error?.let { error ->
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.weight(1f).testTag("plugins_error"),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                    DdTextAction(
                        text = stringResource(R.string.assistant_retry),
                        onClick = onRefresh,
                        modifier = Modifier.testTag("plugins_error_retry"),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PluginRow(
    snapshot: PluginSnapshot,
    pluginsRoot: File?,
    interactive: Boolean,
    menuExpanded: Boolean,
    onOpenPage: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRetryActivation: () -> Unit,
    onRollback: () -> Unit,
    onOpenInfo: () -> Unit,
    onUninstall: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val colors = deepDeepColors()
    val available = snapshot.availability is PluginAvailabilityUi.Available
    val failure = snapshot.lastError?.takeIf {
        snapshot.phase == PluginPhaseUi.FAILED || snapshot.phase == PluginPhaseUi.DEGRADED
    }
    val description = when {
        !available -> (snapshot.availability as? PluginAvailabilityUi.Unavailable)?.reason
            ?: snapshot.summary
        snapshot.phase == PluginPhaseUi.PENDING_RESTART -> stringResource(R.string.plugins_phase_pending_restart)
        else -> snapshot.summary
    }
    DdPressable(
        onClick = onOpenPage,
        modifier = Modifier.fillMaxWidth().testTag("plugin_row_${snapshot.id}"),
        shape = RoundedCornerShape(20.dp),
        enabled = snapshot.hasPages,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = LocalDdRowHorizontalPadding.current, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginIcon(
                icon = snapshot.icon,
                assetsDir = pluginAssetsDir(pluginsRoot, snapshot),
                tint = colors.ink,
                size = 28.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = snapshot.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = failure ?: description,
                    style = DdType.bodySmall,
                    color = if (failure != null) colors.danger else colors.inkTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            DdSwitch(
                checked = snapshot.enabled,
                onCheckedChange = onToggle,
                enabled = interactive,
                stateDescription = stringResource(
                    if (snapshot.enabled) R.string.plugins_enable else R.string.plugins_disable,
                ),
                modifier = Modifier
                    .testTag("plugin_switch_${snapshot.id}")
                    .semantics { contentDescription = snapshot.name },
                activeColor = if (snapshot.enabled && !available) colors.warning else null,
            )
            Box {
                DdIconButton(
                    icon = DdIcons.More,
                    onClick = onToggleMenu,
                    contentDescription = stringResource(R.string.plugins_menu_more),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("plugin_menu_${snapshot.id}"),
                    size = 36.dp,
                    iconSize = 18.dp,
                    tint = colors.inkTertiary,
                    enabled = interactive,
                )
                DdMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onDismissMenu,
                    menuWidth = 176.dp,
                ) {
                    DdMenuItem(
                        text = stringResource(R.string.plugins_menu_uninstall),
                        onClick = {
                            onDismissMenu()
                            onUninstall()
                        },
                        destructive = true,
                        icon = DdIcons.Trash,
                        testTag = "plugin_menu_uninstall_${snapshot.id}",
                    )
                    DdMenuItem(
                        text = stringResource(R.string.plugins_menu_check_updates),
                        onClick = {
                            onDismissMenu()
                            onCheckUpdates()
                        },
                        icon = DdIcons.Refresh,
                        testTag = "plugin_menu_check_updates_${snapshot.id}",
                    )
                    DdMenuItem(
                        text = stringResource(R.string.plugins_menu_info),
                        onClick = {
                            onDismissMenu()
                            onOpenInfo()
                        },
                        icon = DdIcons.Info,
                        testTag = "plugin_menu_info_${snapshot.id}",
                    )
                    if (failure != null) {
                        DdMenuItem(
                            text = stringResource(R.string.plugins_recovery_retry),
                            onClick = {
                                onDismissMenu()
                                onRetryActivation()
                            },
                            icon = DdIcons.Play,
                            testTag = "plugin_menu_retry_${snapshot.id}",
                        )
                        snapshot.previousVersion?.let { previous ->
                            DdMenuItem(
                                text = stringResource(R.string.plugins_recovery_rollback, previous),
                                onClick = {
                                    onDismissMenu()
                                    onRollback()
                                },
                                icon = DdIcons.Refresh,
                                testTag = "plugin_menu_rollback_${snapshot.id}",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginInfoDialog(
    snapshot: PluginSnapshot,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.plugins_info_title),
        content = {
            PluginGroup {
                DdListRow(
                    title = stringResource(R.string.plugins_info_version),
                    value = snapshot.version,
                )
                if (snapshot.installedVersion != null) {
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_info_installed_version),
                        value = snapshot.installedVersion,
                    )
                }
                if (snapshot.installedSizeBytes != null) {
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_info_size),
                        value = formatPluginSize(context, snapshot.installedSizeBytes),
                    )
                }
                if (snapshot.skills.isNotEmpty()) {
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_info_skills),
                        value = snapshot.skills.joinToString("、"),
                    )
                }
                snapshot.publisher?.let { publisher ->
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_info_publisher),
                        value = publisher.name.ifBlank { publisher.keyId },
                        subtitle = publisher.keyId.takeIf { publisher.name.isNotBlank() },
                    )
                }
                if (snapshot.phase == PluginPhaseUi.PENDING_RESTART) {
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_phase_pending_restart),
                        subtitle = stringResource(R.string.plugins_pending_restart_notice),
                    )
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.plugins_close),
                onClick = onDismiss,
                color = deepDeepColors().inkSecondary,
            )
        },
    )
}

private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
    val directory = File(context.cacheDir, "plugin-import").apply { mkdirs() }
    val target = File(directory, "plugin-${System.currentTimeMillis()}.ddp")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    target
}.getOrNull()
