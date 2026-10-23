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
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.settings.SettingsGroup

@Composable
fun WorkspaceSettingsScreen(
    workspace: RuntimeWorkspace,
    skillRepository: SkillRepository,
    agentDocStore: AgentDocStore,
    onBack: () -> Unit,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var showProjectSkills by remember(workspace.id) { mutableStateOf(false) }
    var showAgentsDoc by remember(workspace.id) { mutableStateOf(false) }
    var reloadKey by remember(workspace.id) { mutableStateOf(0) }
    var projectSkillCount by remember(workspace.id) { mutableStateOf<Int?>(null) }
    var agentsDocSet by remember(workspace.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(workspace.id, reloadKey) {
        val skills = skillRepository.list()
        val enabledIds = skillRepository.projectEnabledIds(workspace.cwd)
        projectSkillCount = skills.count { it.id in enabledIds && !it.globalEnabled }
        agentsDocSet = agentDocStore.read(workspace.cwd) != null
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
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.workspace_settings_skills),
                    value = when (val count = projectSkillCount) {
                        null -> stringResource(R.string.workspace_settings_loading)
                        0 -> stringResource(R.string.workspace_settings_skills_none)
                        else -> stringResource(R.string.workspace_settings_skills_count, count)
                    },
                    onClick = { showProjectSkills = true },
                    showChevron = true,
                    modifier = Modifier.testTag("workspace_settings_skills"),
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

    if (showAgentsDoc) {
        AgentsDocSheet(
            workspace = workspace,
            agentDocStore = agentDocStore,
            onSaved = { reloadKey += 1 },
            onDismiss = { showAgentsDoc = false },
        )
    }
}
