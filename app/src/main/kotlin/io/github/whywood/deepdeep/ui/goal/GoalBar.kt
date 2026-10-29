package io.github.whywood.deepdeep.ui.goal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.GoalPhase
import io.github.whywood.deepdeep.runtime.GoalView

@Composable
fun GoalBar(
    view: GoalView,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val goal = view.goal
    val phase = if (goal.phase == GoalPhase.ACTIVE && !view.armed) GoalPhase.PAUSED else goal.phase
    Column(modifier.fillMaxWidth().testTag("goal_bar")) {
        DdHairline()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = DdIcons.Target,
                tint = colors.inkTertiary,
                size = 16.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = goal.objective.ifBlank { stringResource(R.string.goal_untitled) },
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = goalPhaseLabel(phase),
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = goalPhaseColor(phase),
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (goal.maxRounds > 0) {
                    stringResource(R.string.goal_round, goal.roundsStarted, goal.maxRounds)
                } else {
                    stringResource(R.string.goal_round_unbounded, goal.roundsStarted)
                },
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.inkTertiary,
                maxLines = 1,
            )
            when (phase) {
                GoalPhase.ACTIVE -> {
                    Spacer(Modifier.width(6.dp))
                    DdTextAction(
                        text = stringResource(R.string.goal_pause),
                        onClick = onPause,
                        modifier = Modifier.testTag("goal_action"),
                        color = colors.inkSecondary,
                    )
                }

                GoalPhase.PAUSED, GoalPhase.BLOCKED -> {
                    Spacer(Modifier.width(6.dp))
                    DdTextAction(
                        text = stringResource(R.string.goal_resume),
                        onClick = onResume,
                        modifier = Modifier.testTag("goal_action"),
                        color = colors.accent,
                    )
                }

                GoalPhase.COMPLETE -> Unit
            }
        }
    }
}

@Composable
internal fun goalPhaseLabel(phase: GoalPhase): String = stringResource(
    when (phase) {
        GoalPhase.ACTIVE -> R.string.goal_phase_active
        GoalPhase.PAUSED -> R.string.goal_phase_paused
        GoalPhase.BLOCKED -> R.string.goal_phase_blocked
        GoalPhase.COMPLETE -> R.string.goal_phase_complete
    },
)

@Composable
internal fun goalPhaseColor(phase: GoalPhase): Color {
    val colors = deepDeepColors()
    return when (phase) {
        GoalPhase.ACTIVE -> colors.accent
        GoalPhase.PAUSED -> colors.inkSecondary
        GoalPhase.BLOCKED -> colors.danger
        GoalPhase.COMPLETE -> colors.inkTertiary
    }
}
