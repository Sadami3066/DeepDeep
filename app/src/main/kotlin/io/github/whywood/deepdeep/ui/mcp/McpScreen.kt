package io.github.whywood.deepdeep.ui.mcp

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.ui.common.rememberHapticFeedback
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSegmented
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.market.MarketEntry
import io.github.whywood.deepdeep.market.MarketEntryType
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.market.MarketState
import io.github.whywood.deepdeep.ui.common.DelayedLoadingBlock
import io.github.whywood.deepdeep.ui.common.market.MARKET_TAB_MARKET
import io.github.whywood.deepdeep.ui.common.market.MARKET_TAB_MINE
import io.github.whywood.deepdeep.ui.common.market.MarketEntryIcon
import io.github.whywood.deepdeep.ui.common.market.MarketIconLoader
import io.github.whywood.deepdeep.ui.common.market.MarketRefreshButton
import io.github.whywood.deepdeep.ui.common.market.MarketTab
import io.github.whywood.deepdeep.ui.common.market.rememberMarketIconLoader
import java.net.URI
import kotlinx.coroutines.launch

data class McpServerEntry(
    val name: String,
    val config: McpServerConfig,
)

@Composable
fun McpScreen(
    store: McpConfigStore,
    probe: McpServerProbe,
    oauth: McpOAuthService,
    marketRepository: MarketRepository,
    onBack: () -> Unit,
    onReloadSessions: suspend () -> Unit,
    onReconnect: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val holder = remember(store, probe, oauth) { McpStateHolder(store, probe, oauth, scope) }
    val state by holder.state.collectAsStateWithLifecycle()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorName by rememberSaveable { mutableStateOf("") }
    var importing by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(MARKET_TAB_MARKET) }
    var marketQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(holder) {
        holder.refresh()
    }

    McpScreenContent(
        state = state,
        onBack = onBack,
        tab = tab,
        onTabSelect = { tab = it },
        marketQuery = marketQuery,
        onMarketQueryChange = { marketQuery = it },
        onQueryChange = holder::setQuery,
        onRefresh = { scope.launch { holder.refresh() } },
        onRetry = holder::retry,
        onToggle = { entry, enabled ->
            scope.launch {
                if (holder.setEnabled(entry.name, enabled)) onReloadSessions()
            }
        },
        onOpen = { entry ->
            editorName = entry.name
            editorOpen = true
        },
        onAdd = { importing = true },
        marketRepository = marketRepository,
        onMarketInstalled = {
            onReloadSessions()
            holder.refresh()
        },
        onAuthorizeMcp = { id ->
            tab = MARKET_TAB_MINE
            scope.launch {
                holder.refresh()
                editorName = id
                editorOpen = true
            }
        },
        modifier = modifier,
    )

    val marketState by marketRepository.state.collectAsStateWithLifecycle()
    val editorMarketEntry = remember(marketState, editorName, state.visibleServers) {
        val url = (state.visibleServers.firstOrNull { it.name == editorName }?.config as? McpServerConfig.Http)?.url
        (marketState as? MarketState.Ready)?.let {
            marketRepository.entries(MarketEntryType.Mcp).firstOrNull { it.mcp?.url == url }
        }
    }
    val editorIconLoader = rememberMarketIconLoader()

    if (editorOpen) {
        McpEditorSheet(
            target = McpEditorTarget(editorName),
            marketEntry = editorMarketEntry,
            iconLoader = editorIconLoader,
            store = store,
            probe = probe,
            oauth = oauth,
            onDismiss = { editorOpen = false },
            onReloadSessions = onReloadSessions,
            onReconnect = onReconnect,
            onCommitted = {
                holder.invalidateProbe(editorName)
                scope.launch { holder.load() }
            },
        )
    }

    if (importing) {
        McpImportSheet(
            store = store,
            onDismiss = { importing = false },
            onReloadSessions = onReloadSessions,
            onCommitted = {
                holder.clearProbeCache()
                scope.launch { holder.load() }
            },
        )
    }
}

@Composable
private fun McpScreenContent(
    state: McpUiState,
    onBack: () -> Unit,
    tab: Int,
    onTabSelect: (Int) -> Unit,
    marketQuery: String,
    onMarketQueryChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (McpServerEntry, Boolean) -> Unit,
    onOpen: (McpServerEntry) -> Unit,
    onAdd: () -> Unit,
    marketRepository: MarketRepository,
    onMarketInstalled: suspend () -> Unit,
    onAuthorizeMcp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val haptic = rememberHapticFeedback()
    val error = state.error
    val visibleServers = state.visibleServers
    val showMarket = tab == MARKET_TAB_MARKET
    val marketState by marketRepository.state.collectAsStateWithLifecycle()
    val marketEntriesByUrl = remember(marketState) {
        (marketState as? MarketState.Ready)?.let {
            marketRepository.entries(MarketEntryType.Mcp)
                .mapNotNull { entry -> entry.mcp?.url?.let { url -> url to entry } }
                .toMap()
        }.orEmpty()
    }
    val iconLoader = rememberMarketIconLoader()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("mcp_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.mcp_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(DdSpacing.Xs))
            Text(
                text = stringResource(R.string.mcp_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (!showMarket) {
                DdIconButton(
                    icon = DdIcons.Refresh,
                    onClick = onRefresh,
                    contentDescription = stringResource(R.string.mcp_refresh),
                    modifier = Modifier.testTag("mcp_probe_refresh"),
                    tint = colors.ink,
                    iconSize = 21.dp,
                )
                DdIconButton(
                    icon = DdIcons.Plus,
                    onClick = onAdd,
                    contentDescription = stringResource(R.string.mcp_add),
                    modifier = Modifier.testTag("mcp_add"),
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
            onSelect = { index ->
                haptic(HapticFeedbackType.SegmentTick)
                onTabSelect(index)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DdSpacing.Lg, end = DdSpacing.Lg, top = DdSpacing.Xs),
            testTags = listOf("mcp_tab_market", "mcp_tab_mine"),
        )
        DdSearchField(
            query = if (showMarket) marketQuery else state.searchQuery,
            onQueryChange = if (showMarket) onMarketQueryChange else onQueryChange,
            placeholder = stringResource(R.string.mcp_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Sm),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "mcp_search_field",
            clearTestTag = "mcp_search_clear",
        )
        if (showMarket) {
            MarketTab(
                repository = marketRepository,
                type = MarketEntryType.Mcp,
                query = marketQuery,
                testTagPrefix = "mcp",
                modifier = Modifier.fillMaxWidth().weight(1f),
                onInstalled = onMarketInstalled,
                onAuthorizeMcp = onAuthorizeMcp,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = DdSpacing.Lg, end = DdSpacing.Lg, bottom = DdSpacing.Xxl),
            ) {
                if (state.notices.isNotEmpty()) {
                    item {
                        Text(
                            text = state.notices.joinToString("\n"),
                            modifier = Modifier.padding(top = DdSpacing.Sm, start = DdSpacing.Xs, end = DdSpacing.Xs).testTag("mcp_errors"),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
                when {
                    state.loading && state.servers.isEmpty() -> item {
                        DelayedLoadingBlock(
                            loading = true,
                            modifier = Modifier.padding(top = 72.dp),
                        )
                    }

                    state.servers.isEmpty() && error != null -> item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            McpErrorRow(error = error, onRetry = onRetry)
                        }
                    }

                    state.servers.isEmpty() -> item { EmptyMcp(onAdd = onAdd) }

                    visibleServers.isEmpty() -> item {
                        DdEmptyState(
                            text = stringResource(R.string.search_no_match),
                            icon = DdIcons.Search,
                            modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
                        )
                    }

                    else -> {
                        if (error != null) {
                            item {
                                McpErrorRow(
                                    error = error,
                                    onRetry = onRetry,
                                    modifier = Modifier.padding(top = DdSpacing.Sm),
                                )
                            }
                        }
                        itemsIndexed(visibleServers, key = { _, entry -> entry.name }) { index, entry ->
                            Column(Modifier.animateItem()) {
                                if (index > 0) DdHairline()
                                McpServerRow(
                                    entry = entry,
                                    probeResult = state.probes[entry.name],
                                    switchEnabled = state.toggleEnabled,
                                    marketEntry = marketEntriesByUrl[(entry.config as? McpServerConfig.Http)?.url],
                                    iconLoader = iconLoader,
                                    onOpen = { onOpen(entry) },
                                    onToggle = { enabled -> onToggle(entry, enabled) },
                                )
                            }
                        }
                        item { Spacer(Modifier.height(DdSpacing.Xxxl)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpErrorRow(
    error: McpError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val title = stringResource(error.titleRes)
    val message = error.messageRes?.let { stringResource(it) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = DdSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(title, message, error.detail?.takeIf { it.isNotBlank() }).joinToString(" · "),
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.danger,
        )
        if (error.retryable) {
            DdTextAction(
                text = stringResource(R.string.assistant_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("mcp_error_retry"),
            )
        }
    }
}

@Composable
private fun EmptyMcp(onAdd: () -> Unit) {
    DdEmptyState(
        text = stringResource(R.string.mcp_empty_title),
        icon = DdIcons.Plug,
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        action = {
            DdTextAction(
                text = stringResource(R.string.mcp_add),
                onClick = onAdd,
                modifier = Modifier.testTag("mcp_empty_add"),
            )
        },
    )
}

@Composable
private fun McpServerRow(
    entry: McpServerEntry,
    probeResult: McpProbeResult?,
    switchEnabled: Boolean,
    marketEntry: MarketEntry?,
    iconLoader: MarketIconLoader,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    val status = rowStatus(entry, probeResult)
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("mcp_row_${entry.name}"),
        shape = DdShapes.Row,
        contentDescription = entry.name,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = DdSpacing.Xs, vertical = DdSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (marketEntry != null) {
                MarketEntryIcon(entry = marketEntry, loader = iconLoader, size = 40.dp)
            } else {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    DdIcon(icon = DdIcons.Plug, tint = colors.inkTertiary, size = 22.dp)
                }
            }
            Spacer(Modifier.width(DdSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = DdType.body,
                    color = if (entry.config.enabled) colors.ink else colors.inkTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = rowDescription(entry)
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(DdSpacing.Xxs))
                    Text(
                        text = description,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(DdSpacing.Sm))
            Text(
                text = status.text,
                style = DdType.bodySmall,
                color = status.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
            Spacer(Modifier.width(DdSpacing.Sm))
            DdSwitch(
                checked = entry.config.enabled,
                onCheckedChange = onToggle,
                enabled = switchEnabled,
                stateDescription = stringResource(
                    if (entry.config.enabled) R.string.mcp_state_enabled else R.string.mcp_status_disabled,
                ),
                modifier = Modifier.testTag("mcp_switch_${entry.name}"),
            )
        }
    }
}

@Composable
private fun rowStatus(entry: McpServerEntry, result: McpProbeResult?): McpRowStatus {
    val colors = deepDeepColors()
    val styled = when {
        !entry.config.enabled -> stringResource(R.string.mcp_status_disabled) to colors.inkTertiary
        entry.config !is McpServerConfig.Http -> stringResource(R.string.mcp_status_unsupported) to colors.inkTertiary
        result == null -> stringResource(R.string.mcp_status_untested) to colors.inkTertiary
        result.state == McpProbeState.OK -> stringResource(R.string.mcp_row_tools, result.toolCount) to colors.success
        result.state == McpProbeState.AUTH_REQUIRED -> stringResource(R.string.mcp_status_auth) to colors.warning
        result.state == McpProbeState.FAILED -> stringResource(R.string.mcp_status_failed) to colors.danger
        else -> stringResource(R.string.mcp_status_unsupported) to colors.inkTertiary
    }
    return McpRowStatus(styled.first, styled.second)
}

private data class McpRowStatus(
    val text: String,
    val color: Color,
)

private fun rowDescription(entry: McpServerEntry): String {
    val http = entry.config as? McpServerConfig.Http ?: return ""
    return http.description?.takeIf { it.isNotBlank() } ?: urlHost(http.url)
}

private fun urlHost(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty()

internal data class CachedProbe(
    val result: McpProbeResult,
    val fresh: Boolean,
)

internal class McpProbeCache(private val ttlMs: Long = PROBE_CACHE_TTL_MS) {
    private data class Entry(
        val signature: String,
        val result: McpProbeResult,
        val storedAt: Long,
    )

    private val entries = mutableMapOf<String, Entry>()

    fun lookup(
        name: String,
        config: McpServerConfig,
        now: Long = System.currentTimeMillis(),
    ): CachedProbe? {
        val entry = entries[name] ?: return null
        if (entry.signature != config.probeSignature()) {
            entries.remove(name)
            return null
        }
        return CachedProbe(entry.result, now - entry.storedAt <= ttlMs)
    }

    fun store(
        name: String,
        config: McpServerConfig,
        result: McpProbeResult,
        now: Long = System.currentTimeMillis(),
    ) {
        entries[name] = Entry(config.probeSignature(), result, now)
    }

    fun invalidate(name: String) {
        entries.remove(name)
    }

    fun retain(names: Set<String>) {
        entries.keys.retainAll(names)
    }

    fun clear() {
        entries.clear()
    }

    internal companion object {
        const val PROBE_CACHE_TTL_MS = 60_000L
    }
}

internal fun McpServerConfig.probeSignature(): String = when (this) {
    is McpServerConfig.Http -> buildString {
        append("http|")
        append(url)
        append('|')
        append(headers.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" })
        append('|')
        append(authProvider.orEmpty())
        append('|')
        append(timeoutSeconds)
    }

    is McpServerConfig.Stdio -> "stdio|$command|${args.joinToString("\u0000")}"
}
