package io.github.whywood.deepdeep.ui.workspaces

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.WorkspaceToolPolicy
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import kotlinx.coroutines.launch

enum class WorkspaceInheritedKind { Skills, Plugins }

private data class InheritedItem(val id: String, val label: String, val enabled: Boolean)

@Composable
fun WorkspaceInheritedSheet(
    workspaceCwd: String,
    kind: WorkspaceInheritedKind,
    store: WorkspaceToolPolicyStore,
    skillRepository: SkillRepository,
    pluginManager: PluginManagerFacade,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<InheritedItem>>(emptyList()) }

    LaunchedEffect(workspaceCwd, kind) {
        loading = true
        loadFailed = false
        val policy = store.policyForCwd(workspaceCwd)
        val loaded: List<InheritedItem>? = when (kind) {
            WorkspaceInheritedKind.Skills -> {
                val skills = skillRepository.list()
                if (skills.isFailure) {
                    null
                } else {
                    skills.getOrNull()!!.filter { it.globalEnabled }.map { skill ->
                        InheritedItem(skill.name, skill.name, skill.name !in policy.skills)
                    }
                }
            }
            WorkspaceInheritedKind.Plugins -> pluginManager.state.value.plugins.map { plugin ->
                InheritedItem(plugin.id, plugin.name, plugin.id !in policy.plugins)
            }
        }
        if (loaded == null) {
            loadFailed = true
        } else {
            items = loaded
        }
        loading = false
    }

    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight().fillMaxWidth()) {
            DdSheetHeader(
                title = stringResource(
                    if (kind == WorkspaceInheritedKind.Skills) R.string.workspace_inherit_skills_title
                    else R.string.workspace_inherit_plugins_title,
                ),
                onClose = onDismiss,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.workspace_inherit_note),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    loading -> Row(
                        Modifier.fillMaxWidth().height(120.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { DdSpinner() }
                    loadFailed -> Text(
                        text = stringResource(R.string.workspace_inherit_load_failed),
                        style = DdType.body,
                        color = colors.danger,
                    )
                    items.isEmpty() -> Text(
                        text = stringResource(
                            if (kind == WorkspaceInheritedKind.Skills) R.string.workspace_inherit_skills_empty
                            else R.string.workspace_inherit_plugins_empty,
                        ),
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
                                DdSwitch(checked = item.enabled, onCheckedChange = { checked ->
                                    items = items.map { current ->
                                        if (current.id == item.id) current.copy(enabled = checked) else current
                                    }
                                })
                            }
                            DdHairline()
                        }
                    }
                }
                if (saveFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.workspace_inherit_save_failed),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(12.dp))
                DdPrimaryButton(
                    text = stringResource(R.string.workspace_inherit_save),
                    onClick = {
                        if (saving) return@DdPrimaryButton
                        saving = true
                        saveFailed = false
                        scope.launch {
                            val disabled = items.filterNot { it.enabled }.map { it.id }.toSet()
                            val policy = store.policyForCwd(workspaceCwd)
                            val updated = when (kind) {
                                WorkspaceInheritedKind.Skills -> WorkspaceToolPolicy(skills = disabled, plugins = policy.plugins)
                                WorkspaceInheritedKind.Plugins -> WorkspaceToolPolicy(skills = policy.skills, plugins = disabled)
                            }
                            val ok = runCatching { store.update(workspaceCwd, updated) }.isSuccess
                            saving = false
                            if (ok) {
                                onSaved()
                                onDismiss()
                            } else {
                                saveFailed = true
                            }
                        }
                    },
                    enabled = !loading && !saving,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
