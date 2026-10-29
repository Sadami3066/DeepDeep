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
import androidx.compose.runtime.mutableIntStateOf
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
import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class McpServerEntry(
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
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<McpServerEntry>>(emptyList()) }
    var probes by remember { mutableStateOf<Map<String, McpProbeResult>>(emptyMap()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var probing by remember { mutableStateOf(false) }
    var busyName by remember { mutableStateOf<String?>(null) }
    var failureRes by remember { mutableStateOf<Int?>(null) }
    var editor by remember { mutableStateOf<McpEditorTarget?>(null) }
    var importing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var forceProbe by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val visibleEntries = remember(entries, query) { filterMcpServers(entries, query) }

    LaunchedEffect(refreshKey) {
        loading = entries.isEmpty()
        val force = forceProbe
        forceProbe = false
        val snapshot = runCatching { store.load() }.getOrNull()
        if (snapshot == null) {
            failureRes = R.string.mcp_error_load_failed
            loading = false
            return@LaunchedEffect
        }
        errors = snapshot.errors
        entries = snapshot.servers
            .map { McpServerEntry(it.key, it.value) }
            .sortedBy { it.name.lowercase() }
        val targets = snapshot.servers
            .filter { (_, config) -> config.enabled && config is McpServerConfig.Http }
            .map { (name, config) -> name to config }
        val now = System.currentTimeMillis()
        mcpProbeCache.retain(snapshot.servers.keys)
        probes = targets.mapNotNull { (name, config) ->
            mcpProbeCache.lookup(name, config, now)?.let { cached -> name to cached.result }
        }.toMap()
        val stale = if (force) {
            targets
        } else {
            targets.filter { (name, config) -> mcpProbeCache.lookup(name, config, now)?.fresh != true }
        }
        probing = stale.isNotEmpty()
        loading = false
        if (stale.isNotEmpty()) {
            val refreshed = coroutineScope {
                stale.map { (name, config) ->
                    async { name to probe.probe(name, config) }
                }.awaitAll()
            }
            val storedAt = System.currentTimeMillis()
            refreshed.forEach { (name, result) ->
                targets.firstOrNull { it.first == name }?.let { (_, config) ->
                    mcpProbeCache.store(name, config, result, storedAt)
                }
            }
            probes = probes + refreshed.toMap()
        }
        probing = false
    }

    fun toggle(entry: McpServerEntry, enabled: Boolean) {
        if (busyName != null) return
        busyName = entry.name
        scope.launch {
            val result = runCatching {
                store.setEnabled(entry.name, enabled)
                onReloadSessions()
            }
            busyName = null
            if (result.isSuccess) {
                mcpProbeCache.invalidate(entry.name)
                refreshKey++
            } else {
                failureRes = R.string.mcp_error_toggle_failed
            }
        }
    }

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
            if (probing || loading) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                }
            } else {
                DdIconButton(
                    icon = DdIcons.Refresh,
                    onClick = {
                        forceProbe = true
                        refreshKey++
                    },
                    contentDescription = stringResource(R.string.mcp_refresh),
                    modifier = Modifier.testTag("mcp_probe_refresh"),
                    tint = colors.ink,
                    iconSize = 21.dp,
                )
            }
            DdIconButton(
                icon = DdIcons.Plus,
                onClick = { importing = true },
                contentDescription = stringResource(R.string.mcp_add),
                modifier = Modifier.testTag("mcp_add"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        DdSearchField(
            query = query,
            onQueryChange = { query = it },
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
            if (errors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errors.joinToString("\n"),
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("mcp_errors"),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(top = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp)
                }

                entries.isEmpty() -> EmptyMcp()

                visibleEntries.isEmpty() -> Box(
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
                    visibleEntries.forEachIndexed { index, entry ->
                        if (index > 0) DdHairline()
                        McpServerRow(
                            entry = entry,
                            probeResult = probes[entry.name],
                            switchEnabled = busyName == null,
                            onOpen = { editor = McpEditorTarget(entry.name) },
                            onToggle = { toggle(entry, it) },
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editor?.let { target ->
        McpEditorSheet(
            target = target,
            store = store,
            probe = probe,
            oauth = oauth,
            onDismiss = { editor = null },
            onReloadSessions = onReloadSessions,
            onReconnect = onReconnect,
            onCommitted = {
                mcpProbeCache.invalidate(target.name)
                refreshKey++
            },
        )
    }

    if (importing) {
        McpImportSheet(
            store = store,
            onDismiss = { importing = false },
            onReloadSessions = onReloadSessions,
            onCommitted = {
                mcpProbeCache.clear()
                refreshKey++
            },
        )
    }

    failureRes?.let { messageRes ->
        DdDialog(
            onDismissRequest = { failureRes = null },
            modifier = Modifier.testTag("mcp_error_dialog"),
            title = stringResource(R.string.mcp_error_title),
            content = {
                Text(
                    text = stringResource(messageRes),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.mcp_close),
                    onClick = { failureRes = null },
                    modifier = Modifier.testTag("mcp_error_close"),
                )
            },
        )
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

internal fun filterMcpServers(entries: List<McpServerEntry>, query: String): List<McpServerEntry> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return entries
    return entries.filter { entry ->
        entry.name.contains(normalized, ignoreCase = true) ||
            entry.config.description?.contains(normalized, ignoreCase = true) == true ||
            (entry.config as? McpServerConfig.Http)?.url?.contains(normalized, ignoreCase = true) == true
    }
}

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

internal val mcpProbeCache = McpProbeCache()

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
