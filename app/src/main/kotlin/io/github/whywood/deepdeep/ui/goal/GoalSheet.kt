package io.github.whywood.deepdeep.ui.goal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.GoalPhase
import io.github.whywood.deepdeep.runtime.GoalView

@Composable
fun GoalSheet(
    view: GoalView?,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = deepDeepColors()
    val goal = view?.goal
    val phase = when {
        goal == null -> null
        goal.phase == GoalPhase.ACTIVE && view.armed.not() -> GoalPhase.PAUSED
        else -> goal.phase
    }
    var draft by remember(goal?.id, goal?.revision) { mutableStateOf(goal?.objective.orEmpty()) }
    var confirmingClear by remember(goal?.id) { mutableStateOf(false) }
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(
            title = stringResource(if (goal == null) R.string.goal_create_title else R.string.goal_title),
            onClose = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (goal == null) {
                Text(
                    text = stringResource(R.string.goal_objective_label),
                    style = DdType.label,
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(10.dp))
                DdTextArea(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = stringResource(R.string.goal_objective_hint),
                    testTag = "goal_objective",
                )
                Spacer(Modifier.height(16.dp))
                DdPrimaryButton(
                    text = stringResource(R.string.goal_create),
                    onClick = { onCreate(draft.trim()) },
                    modifier = Modifier.fillMaxWidth().testTag("goal_create_action"),
                    enabled = draft.isNotBlank(),
                )
            } else {
                GoalMetaRow(
                    label = stringResource(R.string.goal_phase_label),
                    value = goalPhaseLabel(phase ?: GoalPhase.ACTIVE),
                    valueColor = goalPhaseColor(phase ?: GoalPhase.ACTIVE),
                )
                DdHairline()
                GoalMetaRow(
                    label = stringResource(R.string.goal_rounds_label),
                    value = if (goal.maxRounds > 0) {
                        stringResource(R.string.goal_rounds_value, goal.roundsStarted, goal.maxRounds)
                    } else {
                        stringResource(R.string.goal_rounds_unbounded, goal.roundsStarted)
                    },
                )
                DdHairline()
                val blockedReason = goal.blockedReason?.takeIf { it.isNotBlank() }
                if (blockedReason != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.goal_blocked_reason),
                        style = DdType.micro,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = blockedReason,
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.goal_objective_label),
                    style = DdType.label,
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(10.dp))
                DdTextArea(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = stringResource(R.string.goal_objective_hint),
                    testTag = "goal_objective",
                )
                Spacer(Modifier.height(12.dp))
                DdPrimaryButton(
                    text = stringResource(R.string.goal_save),
                    onClick = { onEdit(draft.trim()) },
                    modifier = Modifier.fillMaxWidth().testTag("goal_save"),
                    enabled = draft.isNotBlank() && draft.trim() != goal.objective,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (phase ?: GoalPhase.ACTIVE) {
                        GoalPhase.ACTIVE -> DdTextAction(
                            text = stringResource(R.string.goal_pause),
                            onClick = onPause,
                            modifier = Modifier.testTag("goal_pause"),
                            color = colors.inkSecondary,
                        )

                        GoalPhase.PAUSED, GoalPhase.BLOCKED -> DdTextAction(
                            text = stringResource(R.string.goal_resume),
                            onClick = onResume,
                            modifier = Modifier.testTag("goal_resume"),
                        )

                        GoalPhase.COMPLETE -> Unit
                    }
                    if (phase != GoalPhase.COMPLETE) {
                        Spacer(Modifier.width(12.dp))
                        DdTextAction(
                            text = stringResource(R.string.goal_complete),
                            onClick = onComplete,
                            modifier = Modifier.testTag("goal_complete"),
                            color = colors.inkSecondary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    DdTextAction(
                        text = stringResource(
                            if (confirmingClear) {
                                R.string.goal_clear_confirm
                            } else {
                                R.string.goal_clear
                            },
                        ),
                        onClick = {
                            if (confirmingClear) {
                                confirmingClear = false
                                onClear()
                            } else {
                                confirmingClear = true
                            }
                        },
                        modifier = Modifier.testTag("goal_clear"),
                        color = colors.danger,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun GoalMetaRow(label: String, value: String, valueColor: Color? = null) {
    val colors = deepDeepColors()
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DdType.bodySmall,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = DdType.bodySmall,
            color = valueColor ?: colors.ink,
            maxLines = 1,
        )
    }
}
