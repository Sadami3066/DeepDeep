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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
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
    var detailPluginId by remember { mutableStateOf<String?>(null) }

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
        updateBusy = updateBusy,
        onBack = onBack,
        onQueryChange = holder::setQuery,
        onRefresh = holder::refresh,
        onOpenPluginPage = onOpenPluginPage,
        onToggle = holder::setEnabled,
        onOpenDetail = { snapshot -> detailPluginId = snapshot.id },
        onRetryActivation = { snapshot -> scope.launch { holder.retryActivation(snapshot.id) } },
        onRollback = { snapshot -> scope.launch { holder.rollback(snapshot.id) } },
        onUninstall = { snapshot -> uninstallTarget = snapshot },
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

    val detailSnapshot = detailPluginId?.let { id -> state.plugins.firstOrNull { it.id == id } }
    detailSnapshot?.let { snapshot ->
        PluginDetailSheet(
            snapshot = snapshot,
            pluginsRoot = resolvedPluginsRoot,
            availableUpdate = state.availableUpdates.firstOrNull { it.pluginId == snapshot.id },
            onDismiss = { detailPluginId = null },
        )
    }

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
}

@Composable
private fun PluginsContent(
    state: PluginsUiState,
    query: String,
    togglingIds: Set<String>,
    pluginsRoot: File?,
    updateBusy: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenPluginPage: (String, String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpenDetail: (PluginSnapshot) -> Unit,
    onRetryActivation: (PluginSnapshot) -> Unit,
    onRollback: (PluginSnapshot) -> Unit,
    onUninstall: (PluginSnapshot) -> Unit,
    onImport: () -> Unit,
    onShowUpdates: () -> Unit,
    onOpenSource: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val visible = filterPlugins(state.plugins, query)
    val interactive = pluginListInteractive(state)
    var topMenuOpen by remember { mutableStateOf(false) }

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
                icon = DdIcons.Plus,
                onClick = onImport,
                contentDescription = stringResource(R.string.plugins_import_title),
                modifier = Modifier.testTag("plugins_import"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            if (updateBusy) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                }
            } else {
                Box {
                    DdIconButton(
                        icon = DdIcons.DotsVertical,
                        onClick = { topMenuOpen = true },
                        contentDescription = stringResource(R.string.plugins_menu_more),
                        modifier = Modifier.testTag("plugins_more"),
                        tint = colors.ink,
                        iconSize = 21.dp,
                    )
                    DdMenu(
                        expanded = topMenuOpen,
                        onDismissRequest = { topMenuOpen = false },
                        menuWidth = 152.dp,
                    ) {
                        DdMenuItem(
                            text = stringResource(R.string.plugins_check_updates),
                            onClick = {
                                topMenuOpen = false
                                onCheckUpdates()
                            },
                            icon = DdIcons.Download,
                            testTag = "plugins_check_updates",
                        )
                        DdMenuItem(
                            text = stringResource(R.string.plugins_source_row),
                            onClick = {
                                topMenuOpen = false
                                onOpenSource()
                            },
                            icon = DdIcons.Globe,
                            testTag = "plugins_source",
                        )
                        DdMenuItem(
                            text = stringResource(R.string.plugins_refresh),
                            onClick = {
                                topMenuOpen = false
                                onRefresh()
                            },
                            icon = DdIcons.Refresh,
                            testTag = "plugins_refresh",
                        )
                    }
                }
            }
        }
        DdSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.plugins_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "plugins_search_field",
            clearTestTag = "plugins_search_clear",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (state.availableUpdates.isNotEmpty()) {
                UpdatesBanner(
                    count = state.availableUpdates.size,
                    onClick = onShowUpdates,
                )
                Spacer(Modifier.height(8.dp))
            }
            when {
                !pluginCatalogReady(state) -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 96.dp)
                        .testTag("plugins_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                }

                visible.isEmpty() -> {
                    if (state.plugins.isEmpty()) {
                        EmptyPlugins()
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                                .testTag("plugins_search_empty"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_match),
                                style = DdType.bodySmall,
                                color = colors.inkTertiary,
                            )
                        }
                    }
                }

                else -> visible.forEachIndexed { index, snapshot ->
                    if (index > 0) DdHairline()
                    PluginRow(
                        snapshot = snapshot,
                        pluginsRoot = pluginsRoot,
                        interactive = interactive && snapshot.id !in togglingIds,
                        onOpenPage = {
                            snapshot.pages.firstOrNull()?.let { page ->
                                onOpenPluginPage(snapshot.id, page.id)
                            }
                        },
                        onToggle = { enabled -> onToggle(snapshot.id, enabled) },
                        onOpenDetail = { onOpenDetail(snapshot) },
                        onRetryActivation = { onRetryActivation(snapshot) },
                        onRollback = { onRollback(snapshot) },
                        onUninstall = { onUninstall(snapshot) },
                    )
                }
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
private fun UpdatesBanner(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("plugins_updates_available"),
        shape = RoundedCornerShape(14.dp),
        background = colors.accentSoft,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Download, tint = colors.accent, size = 16.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.plugins_updates_available, count),
                modifier = Modifier.weight(1f),
                style = DdType.label,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.accent, size = 14.dp)
        }
    }
}

@Composable
private fun EmptyPlugins() {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp)
            .testTag("plugins_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Puzzle, tint = colors.inkTertiary, size = 28.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.plugins_empty),
            style = DdType.label,
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.plugins_empty_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PluginRow(
    snapshot: PluginSnapshot,
    pluginsRoot: File?,
    interactive: Boolean,
    onOpenPage: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
    onRetryActivation: () -> Unit,
    onRollback: () -> Unit,
    onUninstall: () -> Unit,
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
    var menuOpen by remember(snapshot.id) { mutableStateOf(false) }
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
                .padding(vertical = 12.dp),
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
                    onClick = { menuOpen = true },
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
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    menuWidth = 176.dp,
                ) {
                    DdMenuItem(
                        text = stringResource(R.string.plugins_menu_info),
                        onClick = {
                            menuOpen = false
                            onOpenDetail()
                        },
                        icon = DdIcons.Info,
                        testTag = "plugin_menu_info_${snapshot.id}",
                    )
                    if (failure != null) {
                        DdMenuItem(
                            text = stringResource(R.string.plugins_recovery_retry),
                            onClick = {
                                menuOpen = false
                                onRetryActivation()
                            },
                            icon = DdIcons.Play,
                            testTag = "plugin_menu_retry_${snapshot.id}",
                        )
                        snapshot.previousVersion?.let { previous ->
                            DdMenuItem(
                                text = stringResource(R.string.plugins_recovery_rollback, previous),
                                onClick = {
                                    menuOpen = false
                                    onRollback()
                                },
                                icon = DdIcons.Refresh,
                                testTag = "plugin_menu_rollback_${snapshot.id}",
                            )
                        }
                    }
                    DdMenuItem(
                        text = stringResource(R.string.plugins_menu_uninstall),
                        onClick = {
                            menuOpen = false
                            onUninstall()
                        },
                        destructive = true,
                        icon = DdIcons.Trash,
                        testTag = "plugin_menu_uninstall_${snapshot.id}",
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginDetailSheet(
    snapshot: PluginSnapshot,
    pluginsRoot: File?,
    availableUpdate: PluginUpdateUi?,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val publisher = snapshot.publisher
    val failure = snapshot.lastError?.takeIf {
        snapshot.phase == PluginPhaseUi.FAILED || snapshot.phase == PluginPhaseUi.DEGRADED
    }
    val bodyMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().testTag("plugin_detail")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PluginIcon(
                    icon = snapshot.icon,
                    assetsDir = pluginAssetsDir(pluginsRoot, snapshot),
                    tint = colors.ink,
                    size = 26.dp,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = snapshot.name,
                    modifier = Modifier.weight(1f),
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.plugins_close),
                    tint = colors.inkSecondary,
                    iconSize = 20.dp,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (snapshot.summary.isNotBlank()) {
                    Text(
                        text = snapshot.summary,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                failure?.let { error ->
                    Text(
                        text = error,
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                PluginGroup {
                    DdListRow(
                        title = stringResource(R.string.plugins_info_status),
                        value = stringResource(pluginPhaseLabelRes(snapshot.phase)),
                        subtitle = if (snapshot.phase == PluginPhaseUi.PENDING_RESTART) {
                            stringResource(R.string.plugins_pending_restart_notice)
                        } else {
                            null
                        },
                    )
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_info_version),
                        value = snapshot.version,
                    )
                    if (snapshot.installedVersion != null && snapshot.installedVersion != snapshot.version) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_info_installed_version),
                            value = snapshot.installedVersion,
                        )
                    }
                    if (availableUpdate != null) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_info_update),
                            value = availableUpdate.availableVersion,
                        )
                    }
                    if (publisher != null) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_info_publisher),
                            subtitle = publisher.keyId.ifBlank { null },
                            subtitleMaxLines = 2,
                            value = publisher.name.ifBlank { null },
                        )
                    }
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_info_source),
                        value = stringResource(
                            if (publisher != null) {
                                R.string.plugins_info_source_external
                            } else {
                                R.string.plugins_info_source_builtin
                            },
                        ),
                    )
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
                            subtitle = snapshot.skills.joinToString("、"),
                            subtitleMaxLines = 3,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
    val directory = File(context.cacheDir, "plugin-import").apply { mkdirs() }
    val target = File(directory, "plugin-${System.currentTimeMillis()}.ddp")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    target
}.getOrNull()
