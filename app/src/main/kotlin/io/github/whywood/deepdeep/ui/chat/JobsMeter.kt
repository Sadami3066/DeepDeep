package io.github.whywood.deepdeep.ui.chat

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
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun JobsMeter(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = 31.dp, minHeight = 31.dp).testTag("jobs_meter"),
        shape = RoundedCornerShape(8.dp),
        contentDescription = stringResource(R.string.jobs_meter_description, count),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Terminal, tint = colors.inkTertiary, size = 11.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = DdType.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 13.sp),
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}
