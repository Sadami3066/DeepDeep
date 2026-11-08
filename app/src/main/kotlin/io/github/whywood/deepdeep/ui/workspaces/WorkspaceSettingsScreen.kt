package io.github.whywood.deepdeep.ui.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.settings.SettingsGroup

@Composable
fun WorkspaceSettingsScreen(
    workspace: RuntimeWorkspace,
    skillRepository: SkillRepository,
    agentDocStore: AgentDocStore,
    workspaceToolPolicies: WorkspaceToolPolicyStore,
    pluginManager: PluginManagerFacade,
    onBack: () -> Unit,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var showProjectSkills by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var showAgentsDoc by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var reloadKey by remember(workspace.id) { mutableStateOf(0) }
    var projectSkillCount by remember(workspace.id) { mutableStateOf<Int?>(null) }
    var skillsLoadFailed by remember(workspace.id) { mutableStateOf(false) }
    var agentsDocSet by remember(workspace.id) { mutableStateOf<Boolean?>(null) }
    var showInheritedSkills by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var showInheritedPlugins by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var inheritedSkillsValue by remember(workspace.id) { mutableStateOf<String?>(null) }
    var inheritedPluginsValue by remember(workspace.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(workspace.id, reloadKey) {
        skillsLoadFailed = false
        val skills = skillRepository.list()
        val enabledIds = skillRepository.projectEnabledIds(workspace.cwd)
        skillsLoadFailed = skills.isFailure
        projectSkillCount = skills.getOrNull()?.count { it.globalEnabled || it.id in enabledIds }
        agentsDocSet = agentDocStore.read(workspace.cwd) != null
        val policy = workspaceToolPolicies.policyForCwd(workspace.cwd)
        val globalSkills = skills.getOrNull()?.count { it.globalEnabled } ?: 0
        inheritedSkillsValue = if (globalSkills == 0 || policy.skills.isEmpty()) null
            else "${policy.skills.size}"
        inheritedPluginsValue = if (policy.plugins.isEmpty()) null else "${policy.plugins.size}"
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("workspace_settings"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.workspace_settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            DdSectionLabel(stringResource(R.string.workspace_settings_label))
            Spacer(Modifier.height(8.dp))
            Text(
                text = workspace.name,
                style = DdType.pageTitle,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            val skillCount = projectSkillCount
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.workspace_settings_skills),
                    value = when {
                        skillsLoadFailed -> stringResource(R.string.workspace_settings_skills_failed)
                        skillCount == null -> stringResource(R.string.workspace_settings_loading)
                        skillCount == 0 -> stringResource(R.string.workspace_settings_skills_none)
                        else -> stringResource(R.string.workspace_settings_skills_count, skillCount)
                    },
                    onClick = { showProjectSkills = true },
                    showChevron = true,
                    modifier = Modifier.testTag("workspace_settings_skills"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.workspace_inherit_skills),
                    value = inheritedSkillsValue?.let { stringResource(R.string.workspace_inherit_disabled_count, it) }
                        ?: stringResource(R.string.workspace_inherit_all),
                    onClick = { showInheritedSkills = true },
                    showChevron = true,
                    modifier = Modifier.testTag("workspace_settings_inherit_skills"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Puzzle,
                    title = stringResource(R.string.workspace_inherit_plugins),
                    value = inheritedPluginsValue?.let { stringResource(R.string.workspace_inherit_disabled_count, it) }
                        ?: stringResource(R.string.workspace_inherit_all),
                    onClick = { showInheritedPlugins = true },
                    showChevron = true,
                    modifier = Modifier.testTag("workspace_settings_inherit_plugins"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.File,
                    title = stringResource(R.string.workspace_settings_agents_doc),
                    value = when (val set = agentsDocSet) {
                        null -> stringResource(R.string.workspace_settings_loading)
                        true -> stringResource(R.string.workspace_settings_agents_set)
                        false -> stringResource(R.string.workspace_settings_agents_unset)
                    },
                    onClick = { showAgentsDoc = true },
                    showChevron = true,
                    modifier = Modifier.testTag("workspace_settings_agents"),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showProjectSkills) {
        ProjectSkillsSheet(
            workspace = workspace,
            skillRepository = skillRepository,
            onOpenSkills = {
                showProjectSkills = false
                onOpenSkills()
            },
            onSaved = { reloadKey += 1 },
            onDismiss = { showProjectSkills = false },
        )
    }

    if (showInheritedSkills) {
        WorkspaceInheritedSheet(
            workspaceCwd = workspace.cwd,
            kind = WorkspaceInheritedKind.Skills,
            store = workspaceToolPolicies,
            skillRepository = skillRepository,
            pluginManager = pluginManager,
            onSaved = { reloadKey += 1 },
            onDismiss = { showInheritedSkills = false },
        )
    }

    if (showInheritedPlugins) {
        WorkspaceInheritedSheet(
            workspaceCwd = workspace.cwd,
            kind = WorkspaceInheritedKind.Plugins,
            store = workspaceToolPolicies,
            skillRepository = skillRepository,
            pluginManager = pluginManager,
            onSaved = { reloadKey += 1 },
            onDismiss = { showInheritedPlugins = false },
        )
    }

    if (showAgentsDoc) {
        AgentsDocSheet(
            workspace = workspace,
            agentDocStore = agentDocStore,
            onSaved = { reloadKey += 1 },
            onDismiss = { showAgentsDoc = false },
        )
    }
}
