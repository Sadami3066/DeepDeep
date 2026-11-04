package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun ScanResultCard(
    decision: ScanDecision,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val appInfo = remember(decision.primary) {
        (decision.primary as? ScanPrimary.AppScan)?.let { scanAppInfo(context, it.target) }
    }
    val title = when (val primary = decision.primary) {
        is ScanPrimary.AppScan -> stringResource(
            R.string.scan_action_app,
            appInfo?.label ?: primary.target.packageName,
        )

        is ScanPrimary.Browse -> stringResource(R.string.scan_action_browse)
        ScanPrimary.Copy -> stringResource(R.string.scan_action_copy)
    }
    DdPressable(
        onClick = onActivate,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        background = colors.raised,
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.wash),
                contentAlignment = Alignment.Center,
            ) {
                val icon = appInfo?.icon
                if (icon != null) {
                    val bitmap = remember(icon) { icon.toBitmap(96, 96) }
                    Image(
                        painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    DdIcon(icon = DdIcons.Globe, tint = colors.inkSecondary, size = 20.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = DdType.label,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = decision.raw,
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 18.dp)
        }
    }
}
