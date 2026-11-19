package io.github.whywood.superpi.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.superpi.R
import io.github.whywood.superpi.design.DdIcon
import io.github.whywood.superpi.design.DdIcons
import io.github.whywood.superpi.design.DdPressable
import io.github.whywood.superpi.design.DdShapes
import io.github.whywood.superpi.design.DdSpacing
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors

@Composable
fun JobsMeter(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val colors = superPiColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = 31.dp, minHeight = 31.dp).testTag("jobs_meter"),
        shape = DdShapes.Small,
        contentDescription = stringResource(R.string.jobs_meter_description, count),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = DdSpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Terminal, tint = colors.inkTertiary, size = 11.dp)
            Spacer(Modifier.width(DdSpacing.Xs))
            Text(
                text = count.toString(),
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}
