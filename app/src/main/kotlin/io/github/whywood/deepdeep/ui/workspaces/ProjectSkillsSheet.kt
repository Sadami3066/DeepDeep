package io.github.whywood.deepdeep.ui.workspaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.InstalledSkill
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcons
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
import kotlinx.coroutines.launch

@Composable
fun ProjectSkillsSheet(
    workspace: RuntimeWorkspace,
    skillRepository: SkillRepository,
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

    LaunchedEffect(workspace.cwd, reloadKey) {
        loading = true
        loadFailed = false
        val enabled = skillRepository.projectEnabledIds(workspace.cwd)
        skillRepository.list().fold(
            onSuccess = { list ->
                val candidates = list.filterNot { it.globalEnabled }
                skills = candidates
                selected = enabled.intersect(candidates.mapTo(mutableSetOf()) { it.id })
            },
            onFailure = { loadFailed = true },
        )
        loading = false
    }

    val save: () -> Unit = {
        if (!saving) {
            saving = true
            failed = false
            scope.launch {
                val ok = skillRepository.applyProjectSkills(workspace.cwd, selected)
                saving = false
                if (ok) {
                    onSaved()
                    onDismiss()
                } else {
                    failed = true
                }
            }
        }
    }

    DdSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("project_skills_sheet"),
        ) {
            DdSheetHeader(
                title = stringResource(R.string.project_skills_title),
                onClose = onDismiss,
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
                    DdEmptyState(
                        text = stringResource(R.string.project_skills_empty_title),
                        icon = DdIcons.Sparkles,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                        action = {
                            DdPrimaryButton(
                                text = stringResource(R.string.project_skills_empty_action),
                                onClick = onOpenSkills,
                            )
                        },
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
                            DdListRow(
                                title = skill.name,
                                subtitle = skill.description,
                                subtitleMaxLines = 2,
                                trailing = {
                                    DdSwitch(
                                        checked = skill.id in selected,
                                        enabled = !loading && !loadFailed,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) {
                                                selected + skill.id
                                            } else {
                                                selected - skill.id
                                            }
                                            failed = false
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
