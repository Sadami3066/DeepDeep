package io.github.whywood.deepdeep.ui.common.chat

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantStep

@Composable
internal fun ReasoningCard(
    step: AssistantStep.Reasoning,
    active: Boolean,
    compact: Boolean = false,
    terminatedStatus: ProcessStatus? = null,
) {
    val colors = deepDeepColors()
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
    val iconTint = when {
        terminatedStatus == ProcessStatus.Failed -> colors.danger
        active -> colors.inkSecondary
        else -> colors.inkTertiary.copy(alpha = DdAlpha.Muted)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DdSpacing.Xs)
            .heightIn(min = 32.dp)
            .padding(horizontal = DdSpacing.Sm)
            .testTag("reasoning_${step.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(
            icon = DdIcons.RadioOff,
            tint = iconTint,
            size = 13.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(
                if (compact) R.string.assistant_reasoning_compact else R.string.assistant_reasoning,
            ),
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
        Spacer(Modifier.weight(1f))
    }
}
