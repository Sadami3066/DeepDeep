package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSegmented
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginManager
import io.github.whywood.deepdeep.plugin.PluginOperation
import io.github.whywood.deepdeep.plugin.PluginSnapshot
import io.github.whywood.deepdeep.ui.settings.SettingsGroup

@Composable
fun PluginsScreen(
    manager: PluginManager,
    onBack: () -> Unit,
    onOpenPlugin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val state by manager.state.collectAsState()
    val interactive = !state.busy && state.interaction == null
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { manager.refreshIfStale() }
    LifecycleResumeEffect(Unit) {
        manager.refreshIfStale()
        onPauseOrDispose { }
    }

    val kind = pluginTabKind(tabIndex)
    val items = state.plugins
        .filter { it.plugin.kind == kind }
        .map { snapshot ->
            PluginListItem(
                snapshot = snapshot,
                name = stringResource(snapshot.plugin.nameRes),
                summary = stringResource(snapshot.plugin.summaryRes),
            )
        }
    val visible = filterPlugins(items, query)

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
                onClick = { manager.refresh() },
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
            onSelect = { tabIndex = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            testTags = listOf("plugins_tab_external", "plugins_tab_internal"),
        )
        DdSearchField(
            query = query,
            onQueryChange = { query = it },
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
            when {
                items.isEmpty() -> Text(
                    text = stringResource(
                        if (kind == PluginKind.EXTERNAL) R.string.plugins_empty_external
                        else R.string.plugins_empty_internal,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                        .testTag(if (kind == PluginKind.EXTERNAL) "plugins_empty_external" else "plugins_empty_internal"),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )

                visible.isEmpty() -> Text(
                    text = stringResource(R.string.search_no_match),
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )

                else -> SettingsGroup {
                    visible.forEachIndexed { index, item ->
                        if (index > 0) DdHairline()
                        PluginRow(
                            snapshot = item.snapshot,
                            name = item.name,
                            summary = item.summary,
                            operation = state.operation?.takeIf { it.pluginId == item.snapshot.plugin.id },
                            interactive = interactive,
                            onOpen = { onOpenPlugin(item.snapshot.plugin.id) },
                            onDownload = { manager.install(item.snapshot.plugin.id) },
                            onToggle = { enabled -> manager.setEnabled(item.snapshot.plugin.id, enabled) },
                        )
                    }
                }
            }
            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("plugins_error"),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

internal data class PluginListItem(
    val snapshot: PluginSnapshot,
    val name: String,
    val summary: String,
)

internal fun pluginTabKind(index: Int): PluginKind =
    if (index == 0) PluginKind.EXTERNAL else PluginKind.INTERNAL

internal fun pluginMatchesQuery(
    query: String,
    id: String,
    name: String,
    summary: String,
): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return id.contains(normalized, ignoreCase = true) ||
        name.contains(normalized, ignoreCase = true) ||
        summary.contains(normalized, ignoreCase = true)
}

internal fun filterPlugins(items: List<PluginListItem>, query: String): List<PluginListItem> =
    items.filter { item ->
        pluginMatchesQuery(query, item.snapshot.plugin.id, item.name, item.summary)
    }

@Composable
private fun PluginRow(
    snapshot: PluginSnapshot,
    name: String,
    summary: String,
    operation: PluginOperation?,
    interactive: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    val plugin = snapshot.plugin
    val stage = operation?.let {
        it.stage
            ?: it.progress?.let { percent -> stringResource(R.string.plugins_downloading_percent, percent) }
            ?: stringResource(R.string.plugins_stage_preparing)
    }
    val enabled = interactive && operation == null
    val brandIcon = VendorPluginIcons[plugin.id]
        ?.takeIf { plugin.kind == PluginKind.EXTERNAL }
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("plugin_row_${plugin.id}"),
        shape = RoundedCornerShape(20.dp),
        enabled = snapshot.installed,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(
                    horizontal = LocalDdRowHorizontalPadding.current,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                brandIcon != null -> Image(
                    painter = painterResource(brandIcon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit,
                )

                plugin.tintIcon -> DdIcon(icon = plugin.icon, tint = colors.ink, size = 24.dp)

                else -> Image(
                    painter = painterResource(plugin.icon.res),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stage ?: summary,
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
                            .testTag("plugin_switch_${plugin.id}")
                            .semantics { contentDescription = name },
                    )
                } else if (plugin.kind != PluginKind.INTERNAL) {
                    DdTextAction(
                        text = stringResource(R.string.plugins_action_download),
                        onClick = onDownload,
                        enabled = enabled && snapshot.availability is PluginAvailability.Available,
                        modifier = Modifier.testTag("plugin_download_${plugin.id}"),
                    )
                }
            }
        }
    }
}
