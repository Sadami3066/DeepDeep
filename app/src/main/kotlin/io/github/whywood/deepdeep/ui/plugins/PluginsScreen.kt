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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSegmented
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginOperation
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
    onOpenPlugin: (String) -> Unit,
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
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
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
    var sourceSettings by remember { mutableStateOf<PluginSourceSettings?>(null) }

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
        tabIndex = tabIndex,
        pluginsRoot = resolvedPluginsRoot,
        onBack = onBack,
        onTabSelected = { tabIndex = it },
        onQueryChange = holder::setQuery,
        onRefresh = holder::refresh,
        onOpenPlugin = onOpenPlugin,
        onInstall = holder::install,
        onToggle = holder::setEnabled,
        onImport = { importPicker.launch(arrayOf("*/*")) },
        onShowUpdates = { updates = state.availableUpdates },
        onOpenSource = { scope.launch { sourceSettings = holder.loadSourceSettings() } },
        onCheckUpdates = {
            scope.launch {
                updateBusy = true
                updates = runCatching { holder.checkExternalUpdates() }.getOrDefault(emptyList())
                updateError = null
                stagedUpdateIds = emptySet()
                updateBusy = false
            }
        },
        updateBusy = updateBusy,
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
        PluginSourceDialog(
            settings = settings,
            onSave = { updated ->
                holder.saveSourceSettings(updated)
                sourceSettings = null
            },
            onDismiss = { sourceSettings = null },
        )
    }
}

@Composable
private fun PluginsContent(
    state: PluginsUiState,
    query: String,
    togglingIds: Set<String>,
    tabIndex: Int,
    pluginsRoot: File?,
    onBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenPlugin: (String) -> Unit,
    onInstall: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onImport: () -> Unit,
    onCheckUpdates: () -> Unit,
    onShowUpdates: () -> Unit,
    onOpenSource: () -> Unit,
    updateBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = deepDeepColors()
    val tabSource = pluginTabSource(tabIndex)
    val visible = filterPlugins(pluginItems(state.plugins, tabSource), query)
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
        DdSegmented(
            options = listOf(
                stringResource(R.string.plugins_tab_external),
                stringResource(R.string.plugins_tab_internal),
            ),
            selectedIndex = tabIndex,
            onSelect = onTabSelected,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            testTags = listOf("plugins_tab_external", "plugins_tab_internal"),
        )
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
            if (tabSource == PluginSource.EXTERNAL) {
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
                        value = if (updateBusy) stringResource(R.string.plugins_stage_preparing) else null,
                        onClick = if (updateBusy) null else onCheckUpdates,
                        showChevron = !updateBusy,
                        enabled = !updateBusy,
                        modifier = Modifier.testTag("plugins_check_updates"),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
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
                    text = stringResource(
                        if (tabSource == PluginSource.EXTERNAL) {
                            R.string.plugins_empty_external
                        } else {
                            R.string.plugins_empty_internal
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag("plugins_empty"),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            visible.forEach { item ->
                PluginRow(
                    snapshot = item.snapshot,
                    pluginsRoot = pluginsRoot,
                    operation = state.operation?.takeIf { it.pluginId == item.snapshot.id },
                    interactive = interactive && item.snapshot.id !in togglingIds,
                    onOpen = { onOpenPlugin(item.snapshot.id) },
                    onInstall = { onInstall(item.snapshot.id) },
                    onToggle = { enabled -> onToggle(item.snapshot.id, enabled) },
                )
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
    operation: PluginOperation?,
    interactive: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    val enabled = interactive && operation == null
    val available = snapshot.availability is io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi.Available
    val stage = operation?.let {
        it.stage
            ?: it.progress?.let { percent -> stringResource(R.string.plugins_downloading_percent, percent) }
            ?: stringResource(R.string.plugins_stage_preparing)
    }
    val description = when {
        !available -> (snapshot.availability as? io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi.Unavailable)?.reason
            ?: snapshot.summary
        stage != null -> stage
        snapshot.phase == PluginPhaseUi.PENDING_RESTART -> stringResource(R.string.plugins_phase_pending_restart)
        else -> snapshot.summary
    }
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("plugin_row_${snapshot.id}"),
        shape = RoundedCornerShape(20.dp),
        enabled = snapshot.installed,
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
                size = 24.dp,
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
                    text = description,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (snapshot.installed) {
                    DdSwitch(
                        checked = snapshot.enabled,
                        onCheckedChange = onToggle,
                        enabled = enabled,
                        stateDescription = stringResource(
                            if (snapshot.enabled) R.string.plugins_enable else R.string.plugins_disable,
                        ),
                        modifier = Modifier
                            .testTag("plugin_switch_${snapshot.id}")
                            .semantics { contentDescription = snapshot.name },
                        activeColor = if (snapshot.enabled && !available) colors.warning else null,
                    )
                } else if (snapshot.installable) {
                    DdTextAction(
                        text = stringResource(R.string.plugins_action_download),
                        onClick = onInstall,
                        enabled = enabled && available,
                        modifier = Modifier.testTag("plugin_download_${snapshot.id}"),
                    )
                }
            }
        }
    }
}

private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
    val directory = File(context.cacheDir, "plugin-import").apply { mkdirs() }
    val target = File(directory, "plugin-${System.currentTimeMillis()}.ddplugin")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    target
}.getOrNull()
