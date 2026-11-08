package io.github.whywood.deepdeep.ui.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.runtime.McpConfigStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

enum class WorkspaceInheritedKind { Skills, Plugins, Mcp }

private data class InheritedItem(val id: String, val label: String, val enabled: Boolean)

private const val SAVE_DEBOUNCE_MILLIS = 300L

@Composable
fun WorkspaceInheritedSheet(
    workspaceCwd: String,
    kind: WorkspaceInheritedKind,
    store: WorkspaceToolPolicyStore,
    skillRepository: SkillRepository,
    pluginManager: PluginManagerFacade,
    mcpConfigStore: McpConfigStore,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<InheritedItem>>(emptyList()) }
    var latestExcluded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedExcluded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveFailed by remember { mutableStateOf(false) }
    var closeRequested by remember { mutableStateOf(false) }
    val saveSignals = remember { Channel<Unit>(Channel.CONFLATED) }

    LaunchedEffect(workspaceCwd, kind, reloadKey) {
        loading = true
        loadFailed = false
        saveFailed = false
        val policy = store.policyForCwd(workspaceCwd)
        val loaded = when (kind) {
            WorkspaceInheritedKind.Skills -> skillRepository.list().getOrNull()
                ?.filter { it.globalEnabled }
                ?.map { skill -> InheritedItem(skill.name, skill.name, skill.name !in policy.skills) }

            WorkspaceInheritedKind.Plugins -> pluginManager.state.value.plugins.map { plugin ->
                InheritedItem(plugin.id, plugin.name, plugin.id !in policy.plugins)
            }

            WorkspaceInheritedKind.Mcp -> runCatching { mcpConfigStore.load() }.getOrNull()
                ?.servers
                ?.keys
                ?.sorted()
                ?.map { name -> InheritedItem(name, name, name !in policy.mcpServers) }
        }
        if (loaded == null) {
            loadFailed = true
        } else {
            items = loaded
            latestExcluded = loaded.filterNot { it.enabled }.mapTo(mutableSetOf()) { it.id }
            savedExcluded = latestExcluded
        }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose { saveSignals.close() }
    }

    LaunchedEffect(workspaceCwd, kind) {
        for (signal in saveSignals) {
            if (!closeRequested) {
                delay(SAVE_DEBOUNCE_MILLIS)
                saveSignals.tryReceive()
            }
            if (latestExcluded == savedExcluded) {
                if (closeRequested) onDismiss()
                continue
            }
            val excluded = latestExcluded
            saveFailed = false
            val ok = runCatching {
                val policy = store.policyForCwd(workspaceCwd)
                val updated = when (kind) {
                    WorkspaceInheritedKind.Skills -> policy.copy(skills = excluded)
                    WorkspaceInheritedKind.Plugins -> policy.copy(plugins = excluded)
                    WorkspaceInheritedKind.Mcp -> policy.copy(mcpServers = excluded)
                }
                store.update(workspaceCwd, updated)
            }.isSuccess
            if (ok) {
                savedExcluded = excluded
                onSaved()
                if (closeRequested && latestExcluded == savedExcluded) onDismiss()
            } else {
                saveFailed = true
                closeRequested = false
            }
        }
    }

    val requestClose: () -> Unit = {
        if (!closeRequested) {
            closeRequested = true
            saveSignals.trySend(Unit)
        }
    }

    val toggle: (InheritedItem, Boolean) -> Unit = { item, checked ->
        val next = items.map { current ->
            if (current.id == item.id) current.copy(enabled = checked) else current
        }
        items = next
        latestExcluded = next.filterNot { it.enabled }.mapTo(mutableSetOf()) { it.id }
        saveFailed = false
        saveSignals.trySend(Unit)
    }

    DdSheet(onDismiss = requestClose) {
        Column(Modifier.fillMaxHeight().fillMaxWidth()) {
            DdSheetHeader(
                title = stringResource(kindTitle(kind)),
                onClose = requestClose,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(kindNote(kind)),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    loading -> Row(
                        Modifier.fillMaxWidth().height(120.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { DdSpinner() }

                    loadFailed -> Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.workspace_inherit_load_failed),
                            style = DdType.body,
                            color = colors.danger,
                            modifier = Modifier.weight(1f),
                        )
                        DdTextAction(
                            text = stringResource(R.string.workspace_inherit_retry),
                            onClick = { reloadKey += 1 },
                            modifier = Modifier.testTag("workspace_inherit_retry_load"),
                        )
                    }

                    items.isEmpty() -> Text(
                        text = stringResource(kindEmpty(kind)),
                        style = DdType.body,
                        color = colors.inkTertiary,
                    )

                    else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(items, key = { it.id }) { item ->
                            Row(
                                Modifier.fillMaxWidth().height(56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.label,
                                    style = DdType.body,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                DdSwitch(
                                    checked = item.enabled,
                                    onCheckedChange = { checked -> toggle(item, checked) },
                                    stateDescription = stringResource(
                                        if (item.enabled) {
                                            R.string.workspace_inherit_enabled
                                        } else {
                                            R.string.workspace_inherit_disabled
                                        },
                                    ),
                                    modifier = Modifier.testTag("workspace_inherit_${kind.name.lowercase()}_${item.id}"),
                                )
                            }
                            DdHairline()
                        }
                    }
                }
                if (saveFailed) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.workspace_inherit_save_failed),
                            style = DdType.bodySmall,
                            color = colors.danger,
                            modifier = Modifier.weight(1f),
                        )
                        DdTextAction(
                            text = stringResource(R.string.workspace_inherit_retry),
                            onClick = { saveSignals.trySend(Unit) },
                            modifier = Modifier.testTag("workspace_inherit_retry_save"),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun kindTitle(kind: WorkspaceInheritedKind): Int = when (kind) {
    WorkspaceInheritedKind.Skills -> R.string.workspace_inherit_skills_title
    WorkspaceInheritedKind.Plugins -> R.string.workspace_inherit_plugins_title
    WorkspaceInheritedKind.Mcp -> R.string.workspace_inherit_mcp_title
}

private fun kindNote(kind: WorkspaceInheritedKind): Int = when (kind) {
    WorkspaceInheritedKind.Skills,
    WorkspaceInheritedKind.Plugins -> R.string.workspace_inherit_note

    WorkspaceInheritedKind.Mcp -> R.string.workspace_inherit_mcp_note
}

private fun kindEmpty(kind: WorkspaceInheritedKind): Int = when (kind) {
    WorkspaceInheritedKind.Skills -> R.string.workspace_inherit_skills_empty
    WorkspaceInheritedKind.Plugins -> R.string.workspace_inherit_plugins_empty
    WorkspaceInheritedKind.Mcp -> R.string.workspace_inherit_mcp_empty
}
