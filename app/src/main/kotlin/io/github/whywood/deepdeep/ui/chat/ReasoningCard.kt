package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantStep

@Composable
internal fun ReasoningCard(
    step: AssistantStep.Reasoning,
    active: Boolean,
    onToggle: () -> Unit,
) {
    val colors = deepDeepColors()
    val expanded = step.expanded
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "reasoning-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val preview = step.text.lineSequence().firstOrNull().orEmpty().trim()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { stateDescription = state }
                .clickable(role = Role.Button, onClick = onToggle)
                .testTag("reasoning_toggle_${step.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.assistant_reasoning),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = if (active) colors.inkSecondary else colors.inkTertiary,
            )
            if (!expanded && preview.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = preview,
                    modifier = Modifier.weight(1f),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.sp),
                    color = colors.inkTertiary.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.size(2.dp))
            DdIcon(
                icon = DdIcons.ChevronDown,
                modifier = Modifier.rotate(rotation),
                tint = colors.inkTertiary.copy(alpha = 0.7f),
                size = 12.dp,
            )
            Spacer(Modifier.width(2.dp))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(DdMotion.FAST)),
            exit = shrinkVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(DdMotion.FAST)),
        ) {
            Text(
                text = step.text.trim().ifEmpty { "…" },
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 6.dp),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 19.sp),
                color = colors.inkTertiary,
            )
        }
    }
}
