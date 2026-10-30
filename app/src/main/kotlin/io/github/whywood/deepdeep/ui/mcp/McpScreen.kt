package io.github.whywood.deepdeep.ui.mcp

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import io.github.whywood.deepdeep.runtime.McpServerProbe
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

    LaunchedEffect(holder) {
        holder.load()
    }

    McpScreenContent(
        state = state,
        onBack = onBack,
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
        modifier = modifier,
    )

    if (editorOpen) {
        McpEditorSheet(
            target = McpEditorTarget(editorName),
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
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (McpServerEntry, Boolean) -> Unit,
    onOpen: (McpServerEntry) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val error = state.error
    val visibleServers = state.visibleServers
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("mcp_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.mcp_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.mcp_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (state.probing || state.loading) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                }
            } else {
                DdIconButton(
                    icon = DdIcons.Refresh,
                    onClick = onRefresh,
                    contentDescription = stringResource(R.string.mcp_refresh),
                    modifier = Modifier.testTag("mcp_probe_refresh"),
                    tint = colors.ink,
                    iconSize = 21.dp,
                )
            }
            DdIconButton(
                icon = DdIcons.Plus,
                onClick = onAdd,
                contentDescription = stringResource(R.string.mcp_add),
                modifier = Modifier.testTag("mcp_add"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        DdSearchField(
            query = state.searchQuery,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.mcp_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "mcp_search_field",
            clearTestTag = "mcp_search_clear",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (state.notices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.notices.joinToString("\n"),
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("mcp_errors"),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            when {
                state.loading -> Box(
                    Modifier.fillMaxWidth().padding(top = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp)
                }

                state.servers.isEmpty() && error != null -> Box(
                    Modifier.fillMaxWidth().padding(top = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    McpErrorRow(error = error, onRetry = onRetry)
                }

                state.servers.isEmpty() -> EmptyMcp()

                visibleServers.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(top = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_no_match),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }

                else -> {
                    if (error != null) {
                        McpErrorRow(
                            error = error,
                            onRetry = onRetry,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    visibleServers.forEachIndexed { index, entry ->
                        if (index > 0) DdHairline()
                        McpServerRow(
                            entry = entry,
                            probeResult = state.probes[entry.name],
                            switchEnabled = state.toggleEnabled,
                            onOpen = { onOpen(entry) },
                            onToggle = { enabled -> onToggle(entry, enabled) },
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
private fun EmptyMcp() {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Plug, tint = colors.inkTertiary, size = 28.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.mcp_empty_title),
            style = DdType.label,
            color = colors.ink,
        )
    }
}

@Composable
private fun McpServerRow(
    entry: McpServerEntry,
    probeResult: McpProbeResult?,
    switchEnabled: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    DdListRow(
        title = entry.name,
        subtitle = rowSubtitle(entry, probeResult),
        onClick = onOpen,
        modifier = Modifier.testTag("mcp_row_${entry.name}").heightIn(min = 64.dp),
        trailing = {
            DdSwitch(
                checked = entry.config.enabled,
                onCheckedChange = onToggle,
                enabled = switchEnabled,
                stateDescription = stringResource(
                    if (entry.config.enabled) R.string.mcp_state_enabled else R.string.mcp_status_disabled,
                ),
                modifier = Modifier.testTag("mcp_switch_${entry.name}"),
            )
        },
    )
}

@Composable
private fun rowSubtitle(entry: McpServerEntry, result: McpProbeResult?): String {
    val status = when {
        !entry.config.enabled -> stringResource(R.string.mcp_status_disabled)
        entry.config !is McpServerConfig.Http -> stringResource(R.string.mcp_status_unsupported)
        result == null -> stringResource(R.string.mcp_status_untested)
        result.state == McpProbeState.OK -> stringResource(R.string.mcp_status_ok, result.toolCount)
        result.state == McpProbeState.AUTH_REQUIRED -> stringResource(R.string.mcp_status_auth)
        result.state == McpProbeState.FAILED -> stringResource(R.string.mcp_status_failed)
        else -> stringResource(R.string.mcp_status_unsupported)
    }
    val host = (entry.config as? McpServerConfig.Http)?.let { urlHost(it.url) }.orEmpty()
    return if (host.isBlank()) status else stringResource(R.string.mcp_row_subtitle, host, status)
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
