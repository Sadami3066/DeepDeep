package io.github.whywood.deepdeep.ui.plugins

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.whywood.deepdeep.design.DdSegmented
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState
import io.github.whywood.deepdeep.market.MarketEntryType
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.ui.common.market.MARKET_TAB_MARKET
import io.github.whywood.deepdeep.ui.common.market.MarketRefreshButton
import io.github.whywood.deepdeep.ui.common.market.MarketTab
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PluginsScreen(
    manager: PluginManagerFacade,
    marketRepository: MarketRepository,
    onBack: () -> Unit,
    onOpenPluginPage: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    pluginsRoot: File? = null,
    pendingImportUri: String? = null,
    onPendingImportHandled: () -> Unit = {},
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
    var uninstallTarget by remember { mutableStateOf<PluginSnapshot?>(null) }
    var detailPluginId by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(MARKET_TAB_MARKET) }
    var marketQuery by rememberSaveable { mutableStateOf("") }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { copyToCache(context, uri) } ?: return@launch
            inspection = holder.inspectExternal(file)
            trustPublisher = false
            importError = null
        }
    }

    val unreadableMessage = stringResource(R.string.plugins_import_unreadable)
    val invalidMessage = stringResource(R.string.plugins_import_invalid)
    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri?.let(Uri::parse) ?: return@LaunchedEffect
        val file = withContext(Dispatchers.IO) { copyToCache(context, uri) }
        if (file == null) {
            Toast.makeText(context, unreadableMessage, Toast.LENGTH_SHORT).show()
        } else {
            val inspected = holder.inspectExternal(file)
            if (inspected == null) {
                Toast.makeText(context, invalidMessage, Toast.LENGTH_SHORT).show()
            } else {
                inspection = inspected
                trustPublisher = false
                importError = null
            }
        }
        onPendingImportHandled()
    }

    LaunchedEffect(Unit) { holder.load() }
    LifecycleResumeEffect(holder) {
        holder.refresh()
        onPauseOrDispose { }
    }

    PluginsContent(
        state = state,
        query = query,
        togglingIds = togglingIds,
        pluginsRoot = resolvedPluginsRoot,
        onBack = onBack,
        tab = tab,
        onTabSelect = { tab = it },
        marketQuery = marketQuery,
        onMarketQueryChange = { marketQuery = it },
        onQueryChange = holder::setQuery,
        onRefresh = holder::refresh,
        onOpenPluginPage = onOpenPluginPage,
        onToggle = holder::setEnabled,
        onOpenDetail = { snapshot -> detailPluginId = snapshot.id },
        onRetryActivation = { snapshot -> scope.launch { holder.retryActivation(snapshot.id) } },
        onRollback = { snapshot -> scope.launch { holder.rollback(snapshot.id) } },
        onUninstall = { snapshot -> uninstallTarget = snapshot },
        onImport = { importPicker.launch(arrayOf("*/*")) },
        marketRepository = marketRepository,
        onMarketInstalled = { holder.refresh() },
        modifier = modifier,
    )

    val detailSnapshot = detailPluginId?.let { id -> state.plugins.firstOrNull { it.id == id } }
    detailSnapshot?.let { snapshot ->
        PluginDetailSheet(
            snapshot = snapshot,
            pluginsRoot = resolvedPluginsRoot,
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
    onBack: () -> Unit,
    tab: Int,
    onTabSelect: (Int) -> Unit,
    marketQuery: String,
    onMarketQueryChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenPluginPage: (String, String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpenDetail: (PluginSnapshot) -> Unit,
    onRetryActivation: (PluginSnapshot) -> Unit,
    onRollback: (PluginSnapshot) -> Unit,
    onUninstall: (PluginSnapshot) -> Unit,
    onImport: () -> Unit,
    marketRepository: MarketRepository,
    onMarketInstalled: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val visible = filterPlugins(state.plugins, query)
    val interactive = pluginListInteractive(state)
    val showMarket = tab == MARKET_TAB_MARKET

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugins_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.plugins_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(DdSpacing.Xs))
            Text(
                text = stringResource(R.string.plugins_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (!showMarket) {
                DdIconButton(
                    icon = DdIcons.Plus,
                    onClick = onImport,
                    contentDescription = stringResource(R.string.plugins_import_title),
                    modifier = Modifier.testTag("plugins_import"),
                    tint = colors.ink,
                    iconSize = 21.dp,
                )
            } else {
                MarketRefreshButton(repository = marketRepository)
            }
        }
        DdSegmented(
            options = listOf(
                stringResource(R.string.market_tab_market),
                stringResource(R.string.market_tab_mine),
            ),
            selectedIndex = tab,
            onSelect = onTabSelect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DdSpacing.Lg, end = DdSpacing.Lg, top = DdSpacing.Xs),
            testTags = listOf("plugins_tab_market", "plugins_tab_mine"),
        )
        DdSearchField(
            query = if (showMarket) marketQuery else query,
            onQueryChange = if (showMarket) onMarketQueryChange else onQueryChange,
            placeholder = stringResource(R.string.plugins_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Sm),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "plugins_search_field",
            clearTestTag = "plugins_search_clear",
        )
        if (showMarket) {
            MarketTab(
                repository = marketRepository,
                type = MarketEntryType.Plugin,
                query = marketQuery,
                testTagPrefix = "plugins",
                modifier = Modifier.fillMaxWidth().weight(1f),
                onInstalled = onMarketInstalled,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = DdSpacing.Lg, end = DdSpacing.Lg, top = DdSpacing.Sm, bottom = DdSpacing.Huge),
            ) {
                when {
                    state.loading && state.plugins.isEmpty() -> Unit

                    visible.isEmpty() -> {
                        if (state.plugins.isEmpty()) {
                            item { EmptyPlugins() }
                        } else {
                            item {
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
                    }

                    else -> itemsIndexed(visible, key = { _, snapshot -> snapshot.id }) { index, snapshot ->
                        Column {
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
                }
                state.error?.let { error ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = DdSpacing.Xs, end = DdSpacing.Xs),
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
                }
            }
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
        Spacer(Modifier.height(DdSpacing.Lg))
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
        shape = DdShapes.Row,
        enabled = snapshot.hasPages,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = DdSpacing.Md),
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
            Spacer(Modifier.width(DdSpacing.Sm))
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
                    .padding(start = DdSpacing.Xxl, end = DdSpacing.Md),
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
                    .padding(horizontal = DdSpacing.Xxl)
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (snapshot.summary.isNotBlank()) {
                    Text(
                        text = snapshot.summary,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(DdSpacing.Lg))
                }
                failure?.let { error ->
                    Text(
                        text = error,
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                    Spacer(Modifier.height(DdSpacing.Md))
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
                    if (publisher != null) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_info_publisher),
                            subtitle = publisher.keyId.ifBlank { null },
                            subtitleMaxLines = 2,
                            value = publisher.name.ifBlank { publisher.keyId.take(12) },
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
                    if (snapshot.capabilities.isNotEmpty()) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_info_capabilities),
                            subtitle = capabilitySummary(snapshot.capabilities),
                            subtitleMaxLines = 3,
                        )
                    }
                }
                Spacer(Modifier.height(DdSpacing.Xxl))
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
