package io.github.whywood.deepdeep.ui.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.InstalledSkill
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val GLOBAL_SAVE_DEBOUNCE_MILLIS = 300L

@Composable
fun ProjectSkillsSheet(
    workspace: RuntimeWorkspace,
    skillRepository: SkillRepository,
    workspaceToolPolicies: WorkspaceToolPolicyStore,
    onOpenSkills: () -> Unit,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var skills by remember { mutableStateOf<List<InstalledSkill>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var globalExcluded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var globalSaved by remember { mutableStateOf<Set<String>>(emptySet()) }
    var globalFailed by remember { mutableStateOf(false) }
    var closeRequested by remember { mutableStateOf(false) }
    val globalSignals = remember { Channel<Unit>(Channel.CONFLATED) }
    val persistMutex = remember { Mutex() }

    LaunchedEffect(workspace.cwd, reloadKey) {
        loading = true
        loadFailed = false
        globalFailed = false
        val installed = skillRepository.list()
        val enabled = skillRepository.projectEnabledIds(workspace.cwd)
        val policy = workspaceToolPolicies.policyForCwd(workspace.cwd)
        globalExcluded = policy.skills
        globalSaved = policy.skills
        installed.fold(
            onSuccess = { list ->
                skills = list
                selected = enabled intersect list.map { it.id }.toSet()
            },
            onFailure = { loadFailed = true },
        )
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose { globalSignals.close() }
    }

    val persistGlobal: suspend () -> Boolean = {
        persistMutex.withLock {
            val excluded = globalExcluded
            if (excluded == globalSaved) {
                true
            } else {
                runCatching {
                    val policy = workspaceToolPolicies.policyForCwd(workspace.cwd)
                    workspaceToolPolicies.update(workspace.cwd, policy.copy(skills = excluded))
                }.isSuccess.also { ok -> if (ok) globalSaved = excluded }
            }
        }
    }

    LaunchedEffect(workspace.cwd) {
        for (signal in globalSignals) {
            if (!closeRequested) {
                delay(GLOBAL_SAVE_DEBOUNCE_MILLIS)
                globalSignals.tryReceive()
            }
            if (globalExcluded == globalSaved) {
                if (closeRequested) onDismiss()
                continue
            }
            val ok = persistGlobal()
            if (ok) {
                onSaved()
                if (closeRequested && globalExcluded == globalSaved) onDismiss()
            } else {
                globalFailed = true
                closeRequested = false
            }
        }
    }

    val requestClose: () -> Unit = {
        if (!closeRequested) {
            closeRequested = true
            globalSignals.trySend(Unit)
        }
    }

    val globallyEnabled = skills.filter { it.globalEnabled }.mapTo(mutableSetOf()) { it.id }
    val save: () -> Unit = {
        if (!saving) {
            saving = true
            failed = false
            globalFailed = false
            scope.launch {
                val globalOk = persistGlobal()
                val projectOk = globalOk && skillRepository.applyProjectSkills(
                    workspace.cwd,
                    selected.filterNot { it in globallyEnabled }.toSet(),
                )
                saving = false
                if (projectOk) {
                    onSaved()
                    onDismiss()
                } else if (globalOk) {
                    failed = true
                } else {
                    globalFailed = true
                }
            }
        }
    }

    DdSheet(onDismiss = requestClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("project_skills_sheet"),
        ) {
            DdSheetHeader(
                title = stringResource(R.string.project_skills_title),
                onClose = requestClose,
            )
            when {
                loading -> {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                    }
                }

                loadFailed -> {
                    Box(
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.project_skills_load_failed),
                                style = DdType.bodySmall,
                                color = colors.danger,
                            )
                            Spacer(Modifier.height(8.dp))
                            DdTextAction(
                                text = stringResource(R.string.skills_retry),
                                onClick = { reloadKey += 1 },
                                modifier = Modifier.testTag("project_skills_retry"),
                            )
                        }
                    }
                }

                skills.isEmpty() -> {
                    ProjectSkillsEmpty(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onOpenSkills = onOpenSkills,
                    )
                }

                else -> {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp),
                    ) {
                        itemsIndexed(skills, key = { _, skill -> skill.id }) { index, skill ->
                            val global = skill.globalEnabled
                            DdListRow(
                                title = skill.name,
                                subtitle = if (global) {
                                    stringResource(R.string.project_skills_global_hint, skill.description)
                                } else {
                                    skill.description
                                },
                                subtitleMaxLines = 2,
                                trailing = {
                                    DdSwitch(
                                        checked = if (global) {
                                            skill.name !in globalExcluded
                                        } else {
                                            skill.id in selected
                                        },
                                        enabled = !loading && !loadFailed,
                                        onCheckedChange = { checked ->
                                            if (global) {
                                                globalExcluded = if (checked) {
                                                    globalExcluded - skill.name
                                                } else {
                                                    globalExcluded + skill.name
                                                }
                                                globalFailed = false
                                                globalSignals.trySend(Unit)
                                            } else {
                                                selected = if (checked) {
                                                    selected + skill.id
                                                } else {
                                                    selected - skill.id
                                                }
                                                failed = false
                                            }
                                        },
                                        modifier = Modifier.testTag("project_skill_${skill.id}"),
                                    )
                                },
                            )
                            if (index != skills.lastIndex) DdHairline()
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                if (globalFailed) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.project_skills_save_failed),
                            style = DdType.bodySmall,
                            color = colors.danger,
                            modifier = Modifier.weight(1f),
                        )
                        DdTextAction(
                            text = stringResource(R.string.workspace_inherit_retry),
                            onClick = {
                                globalFailed = false
                                globalSignals.trySend(Unit)
                            },
                            modifier = Modifier.testTag("project_skills_global_retry"),
                        )
                    }
                }
                if (failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.project_skills_save_failed),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.project_skills_note),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(12.dp))
                DdPrimaryButton(
                    text = stringResource(R.string.project_skills_save),
                    onClick = save,
                    modifier = Modifier.fillMaxWidth().testTag("project_skills_save"),
                    enabled = !loading && !saving && !loadFailed,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProjectSkillsEmpty(
    modifier: Modifier,
    onOpenSkills: () -> Unit,
) {
    val colors = deepDeepColors()
    Column(
        modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.project_skills_empty_title),
            style = DdType.body,
            color = colors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.project_skills_empty_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(20.dp))
        DdPrimaryButton(
            text = stringResource(R.string.project_skills_empty_action),
            onClick = onOpenSkills,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
