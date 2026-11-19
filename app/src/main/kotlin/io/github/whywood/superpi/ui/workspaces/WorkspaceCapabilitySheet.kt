package io.github.whywood.superpi.ui.workspaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.superpi.R
import io.github.whywood.superpi.data.SkillRepository
import io.github.whywood.superpi.data.WorkspaceToolPolicy
import io.github.whywood.superpi.data.WorkspaceToolPolicyStore
import io.github.whywood.superpi.design.DdEmptyState
import io.github.whywood.superpi.design.DdHairline
import io.github.whywood.superpi.design.DdListRow
import io.github.whywood.superpi.design.DdSheet
import io.github.whywood.superpi.design.DdSheetHeader
import io.github.whywood.superpi.design.DdSpinner
import io.github.whywood.superpi.design.DdSwitch
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors
import io.github.whywood.superpi.plugin.manager.PluginManagerFacade
import io.github.whywood.superpi.runtime.McpConfigStore
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import kotlinx.coroutines.launch

internal enum class WorkspaceCapabilityKind { Skills, Plugins, Mcp }

private data class CapabilityItem(val id: String, val label: String)

@Composable
internal fun WorkspaceCapabilitySheet(
    workspace: RuntimeWorkspace,
    kind: WorkspaceCapabilityKind,
    store: WorkspaceToolPolicyStore,
    skillRepository: SkillRepository,
    pluginManager: PluginManagerFacade,
    mcpConfigStore: McpConfigStore,
    onDismiss: () -> Unit,
) {
    val colors = superPiColors()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<CapabilityItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var policy by remember { mutableStateOf(effectivePolicy(workspace, store)) }

    fun reload() {
        scope.launch {
            loading = true
            val loaded = when (kind) {
                WorkspaceCapabilityKind.Skills -> skillRepository.list().getOrNull()
                    ?.map { CapabilityItem(it.name, it.name) }
                    .orEmpty()

                WorkspaceCapabilityKind.Plugins -> pluginManager.state.value.plugins
                    .filter { it.enabled }
                    .map { CapabilityItem(it.id, it.name) }

                WorkspaceCapabilityKind.Mcp -> runCatching { mcpConfigStore.load().servers.keys }
                    .getOrDefault(emptySet())
                    .sorted()
                    .map { CapabilityItem(it, it) }
            }
            policy = effectivePolicy(workspace, store)
            items = loaded
            selected = when (kind) {
                WorkspaceCapabilityKind.Skills -> policy.enabledSkills(loaded.mapTo(mutableSetOf()) { it.id })
                WorkspaceCapabilityKind.Plugins -> policy.enabledPlugins(loaded.mapTo(mutableSetOf()) { it.id })
                WorkspaceCapabilityKind.Mcp -> policy.enabledMcpServers(loaded.mapTo(mutableSetOf()) { it.id })
            }
            loading = false
        }
    }

    LaunchedEffect(workspace.cwd, kind) { reload() }

    fun toggle(id: String, checked: Boolean) {
        val installed = items.mapTo(mutableSetOf()) { it.id }
        val next = if (checked) selected + id else selected - id
        val selection = if (policy.allCapabilities) installed - next else next
        val updated = when (kind) {
            WorkspaceCapabilityKind.Skills -> policy.copy(skills = selection)
            WorkspaceCapabilityKind.Plugins -> policy.copy(plugins = selection)
            WorkspaceCapabilityKind.Mcp -> policy.copy(mcpServers = selection)
        }
        policy = updated
        selected = next
        scope.launch { store.update(workspace.cwd, updated) }
    }

    DdSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .testTag("bot_capability_sheet"),
        ) {
            DdSheetHeader(
                title = stringResource(
                    when (kind) {
                        WorkspaceCapabilityKind.Skills -> R.string.bot_capabilities_skills
                        WorkspaceCapabilityKind.Plugins -> R.string.bot_capabilities_plugins
                        WorkspaceCapabilityKind.Mcp -> R.string.bot_capabilities_mcp
                    },
                ),
                onClose = onDismiss,
            )
            when {
                loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                }

                items.isEmpty() -> DdEmptyState(
                    text = stringResource(
                        when (kind) {
                            WorkspaceCapabilityKind.Skills -> R.string.bot_capabilities_empty_skills
                            WorkspaceCapabilityKind.Plugins -> R.string.bot_capabilities_empty_plugins
                            WorkspaceCapabilityKind.Mcp -> R.string.bot_capabilities_empty_mcp
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                else -> LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        DdListRow(
                            title = item.label,
                            trailing = {
                                DdSwitch(
                                    checked = item.id in selected,
                                    onCheckedChange = { checked -> toggle(item.id, checked) },
                                    modifier = Modifier.testTag("bot_capability_${item.id}"),
                                )
                            },
                        )
                        if (index != items.lastIndex) DdHairline()
                    }
                }
            }
        }
    }
}

private fun effectivePolicy(
    workspace: RuntimeWorkspace,
    store: WorkspaceToolPolicyStore,
): WorkspaceToolPolicy {
    val stored = store.policyForCwd(workspace.cwd)
    return if (workspace.isHome) stored.copy(allCapabilities = true) else stored
}
