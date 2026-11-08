package io.github.whywood.deepdeep.ui.common.chat

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantStep

@Composable
internal fun ReasoningCard(
    step: AssistantStep.Reasoning,
    active: Boolean,
    onToggle: () -> Unit,
    terminatedStatus: ProcessStatus? = null,
) {
    val colors = deepDeepColors()
    val expanded = step.expanded
    val rotation = animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "reasoning-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val shimmer = rememberShimmerBrush(
        active = active,
        base = colors.inkTertiary,
        highlight = colors.inkSecondary,
    )
    val titleStyle = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, brush = shimmer)
    val terminatedLabel = when (terminatedStatus) {
        ProcessStatus.Stopped -> stringResource(R.string.assistant_stopped)
        ProcessStatus.Failed -> stringResource(R.string.assistant_ux_process_failed)
        else -> null
    }
    val terminatedColor = when (terminatedStatus) {
        ProcessStatus.Failed -> colors.danger
        else -> colors.inkTertiary
    }
    val preview = reasoningPreview(step.text)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DdSpacing.Xs)
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { stateDescription = state }
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = DdSpacing.Sm)
                .testTag("reasoning_toggle_${step.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.assistant_reasoning),
                style = titleStyle,
                color = if (shimmer != null) {
                    Color.Unspecified
                } else if (active) {
                    colors.inkSecondary
                } else {
                    colors.inkTertiary
                },
            )
            if (terminatedLabel != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = terminatedLabel,
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.sp),
                    color = terminatedColor,
                )
            }
            if (!expanded && preview.isNotEmpty()) {
                Spacer(Modifier.width(DdSpacing.Sm))
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
            Spacer(Modifier.size(DdSpacing.Xxs))
            DdIcon(
                icon = DdIcons.ChevronRight,
                modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
                tint = colors.inkTertiary.copy(alpha = DdAlpha.Secondary),
                size = 12.dp,
            )
            Spacer(Modifier.width(DdSpacing.Xxs))
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
                modifier = Modifier.fillMaxWidth().padding(start = DdSpacing.Md, top = DdSpacing.Xxs, bottom = 6.dp),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 19.sp),
                color = colors.inkTertiary,
            )
        }
    }
}
