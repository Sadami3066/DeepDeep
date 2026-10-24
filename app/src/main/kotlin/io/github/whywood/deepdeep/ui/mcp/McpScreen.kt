package io.github.whywood.deepdeep.ui.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
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
    onReloadSessions: () -> Unit,
    onReconnect: (String) -> Unit,
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
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = entries.isEmpty()
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
        val targets = snapshot.servers.filter { (_, config) ->
            config.enabled && config is McpServerConfig.Http
        }
        probes = emptyMap()
        probing = targets.isNotEmpty()
        if (targets.isNotEmpty()) {
            probes = coroutineScope {
                targets.map { (name, config) ->
                    async { name to probe.probe(name, config) }
                }.awaitAll().toMap()
            }
        }
        probing = false
        loading = false
    }

    fun toggle(entry: McpServerEntry, enabled: Boolean) {
        if (busyName != null) return
        busyName = entry.name
        scope.launch {
            val result = runCatching { store.setEnabled(entry.name, enabled) }
            busyName = null
            if (result.isSuccess) {
                onReloadSessions()
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
            DdIconButton(
                icon = DdIcons.Plus,
                onClick = { editor = McpEditorTarget() },
                contentDescription = stringResource(R.string.mcp_add),
                modifier = Modifier.testTag("mcp_add"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
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
            if (!loading && (entries.isNotEmpty() || errors.isNotEmpty())) {
                Spacer(Modifier.height(4.dp))
                RefreshRow(probing = probing) { refreshKey++ }
                Spacer(Modifier.height(4.dp))
            }
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(top = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp)
                }

                entries.isEmpty() -> EmptyMcp()

                else -> {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) DdHairline()
                        McpServerRow(
                            entry = entry,
                            probeResult = probes[entry.name],
                            switchEnabled = busyName == null,
                            onOpen = { editor = McpEditorTarget(entry.name, entry.config) },
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
            existingNames = entries.map { it.name }.toSet() - target.name,
            store = store,
            probe = probe,
            oauth = oauth,
            onDismiss = { editor = null },
            onReloadSessions = onReloadSessions,
            onReconnect = onReconnect,
            onCommitted = { refreshKey++ },
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
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.mcp_empty_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RefreshRow(probing: Boolean, onClick: () -> Unit) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("mcp_probe_refresh")
            .clickable(enabled = !probing, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.mcp_refresh),
            modifier = Modifier.weight(1f),
            style = DdType.label,
            color = if (probing) colors.inkTertiary else colors.accent,
        )
        if (probing) DdSpinner(size = 16.dp, strokeWidth = 2.dp)
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
