package io.github.whywood.deepdeep.ui.workspaces

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.WorkspaceToolPolicy
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.common.BotAvatar
import io.github.whywood.deepdeep.ui.settings.SettingsGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WorkspaceSettingsScreen(
    workspace: RuntimeWorkspace,
    skillRepository: SkillRepository,
    agentDocStore: AgentDocStore,
    workspaceToolPolicies: WorkspaceToolPolicyStore,
    pluginManager: PluginManagerFacade,
    mcpConfigStore: McpConfigStore,
    existingNames: List<String>,
    screenToolsReady: Boolean,
    onRename: (String) -> Unit,
    onAvatarPicked: (Uri?) -> Unit,
    onIntroChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var reloadKey by remember(workspace.id) { mutableStateOf(0) }
    var showAgentsDoc by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var showAvatarSheet by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var showIntroDialog by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var capabilityKind by rememberSaveable(workspace.id) { mutableStateOf<WorkspaceCapabilityKind?>(null) }
    var agentsDocSet by remember(workspace.id) { mutableStateOf<Boolean?>(null) }
    var policy by remember(workspace.id) { mutableStateOf(effectivePolicy(workspace, workspaceToolPolicies)) }
    var skillCount by remember(workspace.id) { mutableStateOf<Int?>(null) }
    var pluginCount by remember(workspace.id) { mutableStateOf<Int?>(null) }
    var mcpCount by remember(workspace.id) { mutableStateOf<Int?>(null) }
    var skillsLoaded by remember(workspace.id) { mutableStateOf(false) }
    var pluginsLoaded by remember(workspace.id) { mutableStateOf(false) }
    var mcpLoaded by remember(workspace.id) { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAvatarPicked(uri)
    }

    LaunchedEffect(workspace.id, reloadKey) {
        val loaded = withContext(Dispatchers.IO) {
            val current = effectivePolicy(workspace, workspaceToolPolicies)
            val skills = skillRepository.list().getOrNull().orEmpty()
            val plugins = pluginManager.state.value.plugins.filter { it.enabled }.mapTo(mutableSetOf()) { it.id }
            val mcpServers = runCatching { mcpConfigStore.load().servers.keys }.getOrDefault(emptySet())
            SettingsLoad(
                agentsDocSet = agentDocStore.read(workspace.cwd) != null,
                policy = current,
                skillCount = current.enabledSkills(skills.mapTo(mutableSetOf()) { it.name }).size,
                pluginCount = current.enabledPlugins(plugins).size,
                mcpCount = current.enabledMcpServers(mcpServers).size,
            )
        }
        policy = loaded.policy
        agentsDocSet = loaded.agentsDocSet
        skillCount = loaded.skillCount
        pluginCount = loaded.pluginCount
        mcpCount = loaded.mcpCount
        skillsLoaded = true
        pluginsLoaded = true
        mcpLoaded = true
    }

    fun updatePolicy(block: (WorkspaceToolPolicy) -> WorkspaceToolPolicy) {
        val next = block(policy)
        policy = next
        scope.launch { workspaceToolPolicies.update(workspace.cwd, next) }
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
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotAvatar(workspace = workspace, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = workspace.name,
                        style = DdType.pageTitle,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val intro = workspace.intro?.trim().orEmpty()
                    if (intro.isNotEmpty()) {
                        Text(
                            text = intro,
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (workspace.isHome) {
                        Text(
                            text = stringResource(R.string.bot_default_badge),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (!workspace.isHome) {
                Spacer(Modifier.height(20.dp))
                DdSectionLabel(stringResource(R.string.bot_identity))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    DdListRow(
                        icon = DdIcons.Image,
                        title = stringResource(R.string.bot_avatar),
                        value = workspace.avatar
                            ?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.bot_avatar_change) },
                        onClick = { showAvatarSheet = true },
                        showChevron = true,
                        modifier = Modifier.testTag("bot_settings_avatar"),
                    )
                    DdHairline()
                    DdListRow(
                        icon = DdIcons.Pen,
                        title = stringResource(R.string.workspace_name_placeholder),
                        value = workspace.name,
                        onClick = { showRenameDialog = true },
                        showChevron = true,
                        modifier = Modifier.testTag("bot_settings_name"),
                    )
                    DdHairline()
                    DdListRow(
                        icon = DdIcons.Info,
                        title = stringResource(R.string.bot_intro),
                        value = workspace.intro?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.bot_intro_placeholder),
                        onClick = { showIntroDialog = true },
                        showChevron = true,
                        modifier = Modifier.testTag("bot_settings_intro"),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            DdSectionLabel(stringResource(R.string.bot_capabilities))
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.bot_capabilities_skills),
                    value = capabilityValue(skillCount, skillsLoaded),
                    onClick = { capabilityKind = WorkspaceCapabilityKind.Skills },
                    showChevron = true,
                    modifier = Modifier.testTag("bot_capabilities_skills"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Puzzle,
                    title = stringResource(R.string.bot_capabilities_plugins),
                    value = capabilityValue(pluginCount, pluginsLoaded),
                    onClick = { capabilityKind = WorkspaceCapabilityKind.Plugins },
                    showChevron = true,
                    modifier = Modifier.testTag("bot_capabilities_plugins"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Plug,
                    title = stringResource(R.string.bot_capabilities_mcp),
                    value = capabilityValue(mcpCount, mcpLoaded),
                    onClick = { capabilityKind = WorkspaceCapabilityKind.Mcp },
                    showChevron = true,
                    modifier = Modifier.testTag("bot_capabilities_mcp"),
                )
            }
            Spacer(Modifier.height(20.dp))
            DdSectionLabel(stringResource(R.string.bot_tools))
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Desktop,
                    title = stringResource(R.string.bot_tools_screen),
                    onClick = { updatePolicy { it.copy(screenTools = !it.screenTools) } },
                    modifier = Modifier.testTag("bot_tools_screen"),
                    trailing = {
                        DdSwitch(
                            checked = policy.screenTools,
                            onCheckedChange = { checked -> updatePolicy { it.copy(screenTools = checked) } },
                        )
                    },
                )
                if (policy.screenTools && !screenToolsReady) {
                    Text(
                        text = stringResource(R.string.bot_tools_screen_permission),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                DdHairline()
                DdListRow(
                    icon = DdIcons.Apps,
                    title = stringResource(R.string.bot_tools_miniapp),
                    onClick = { updatePolicy { it.copy(miniappTools = !it.miniappTools) } },
                    modifier = Modifier.testTag("bot_tools_miniapp"),
                    trailing = {
                        DdSwitch(
                            checked = policy.miniappTools,
                            onCheckedChange = { checked -> updatePolicy { it.copy(miniappTools = checked) } },
                        )
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.File,
                    title = stringResource(R.string.bot_instructions),
                    value = when (val set = agentsDocSet) {
                        null -> stringResource(R.string.workspace_settings_loading)
                        true -> stringResource(R.string.workspace_settings_agents_set)
                        else -> stringResource(R.string.workspace_settings_agents_unset)
                    },
                    onClick = { showAgentsDoc = true },
                    showChevron = true,
                    modifier = Modifier.testTag("bot_settings_agents"),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showAgentsDoc) {
        AgentsDocSheet(
            workspace = workspace,
            agentDocStore = agentDocStore,
            onSaved = { reloadKey += 1 },
            onDismiss = { showAgentsDoc = false },
        )
    }

    capabilityKind?.let { kind ->
        WorkspaceCapabilitySheet(
            workspace = workspace,
            kind = kind,
            store = workspaceToolPolicies,
            skillRepository = skillRepository,
            pluginManager = pluginManager,
            mcpConfigStore = mcpConfigStore,
            onDismiss = {
                capabilityKind = null
                reloadKey += 1
            },
        )
    }

    if (showAvatarSheet) {
        BotAvatarSheet(
            hasCustomAvatar = !workspace.avatar.isNullOrBlank(),
            onChooseImage = {
                showAvatarSheet = false
                pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onUseDefault = {
                showAvatarSheet = false
                onAvatarPicked(null)
            },
            onDismiss = { showAvatarSheet = false },
        )
    }

    if (showIntroDialog) {
        BotIntroDialog(
            initial = workspace.intro.orEmpty(),
            onConfirm = { text ->
                showIntroDialog = false
                onIntroChange(text)
            },
            onDismiss = { showIntroDialog = false },
        )
    }

    if (showRenameDialog) {
        WorkspaceNameDialog(
            initial = workspace.name,
            existingNames = existingNames,
            onConfirm = { name ->
                showRenameDialog = false
                onRename(name)
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}

private data class SettingsLoad(
    val agentsDocSet: Boolean,
    val policy: WorkspaceToolPolicy,
    val skillCount: Int,
    val pluginCount: Int,
    val mcpCount: Int,
)

private fun effectivePolicy(workspace: RuntimeWorkspace, store: WorkspaceToolPolicyStore): WorkspaceToolPolicy {
    val stored = store.policyForCwd(workspace.cwd)
    return if (workspace.isHome) stored.copy(allCapabilities = true) else stored
}

@Composable
private fun capabilityValue(count: Int?, loaded: Boolean): String {
    if (!loaded || count == null) return stringResource(R.string.workspace_settings_loading)
    return if (count == 0) {
        stringResource(R.string.bot_capabilities_none)
    } else {
        stringResource(R.string.bot_capabilities_count, count)
    }
}
