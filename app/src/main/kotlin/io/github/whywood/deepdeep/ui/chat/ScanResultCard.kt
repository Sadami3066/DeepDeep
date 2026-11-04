package io.github.whywood.deepdeep.ui.chat

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.scan.ScanDecision
import io.github.whywood.deepdeep.ui.common.scan.ScanPrimary
import io.github.whywood.deepdeep.ui.common.scan.scanAppInfo

@Composable
internal fun ScanResultCard(
    decisions: List<ScanDecision>,
    showAppPicker: Boolean,
    onActivate: (ScanDecision) -> Unit,
    onOpenAppPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.raised),
    ) {
        decisions.take(MAX_ROWS).forEachIndexed { index, decision ->
            if (index > 0) DdHairline()
            ScanDecisionRow(decision = decision, onClick = { onActivate(decision) })
        }
        if (showAppPicker) {
            if (decisions.isNotEmpty()) DdHairline()
            ScanRowContent(
                title = stringResource(R.string.scan_action_other_app),
                subtitle = null,
                appIcon = null,
                ddIcon = DdIcons.Scan,
                chevron = true,
                onClick = onOpenAppPicker,
            )
        }
    }
}

@Composable
private fun ScanDecisionRow(decision: ScanDecision, onClick: () -> Unit) {
    val context = LocalContext.current
    val appInfo = remember(decision.primary) {
        (decision.primary as? ScanPrimary.AppScan)?.let { scanAppInfo(context, it.strategy.packageName) }
    }
    val title = when (val primary = decision.primary) {
        is ScanPrimary.AppScan -> stringResource(
            R.string.scan_action_app,
            appInfo?.label ?: primary.strategy.packageName,
        )

        is ScanPrimary.Browse -> stringResource(R.string.scan_action_browse)
        ScanPrimary.Copy -> stringResource(R.string.scan_action_copy)
    }
    ScanRowContent(
        title = title,
        subtitle = decision.raw,
        appIcon = appInfo?.icon,
        ddIcon = when (decision.primary) {
            is ScanPrimary.Browse -> DdIcons.Globe
            ScanPrimary.Copy -> DdIcons.Copy
            is ScanPrimary.AppScan -> null
        },
        chevron = decision.primary !is ScanPrimary.Copy,
        onClick = onClick,
    )
}

@Composable
private fun ScanRowContent(
    title: String,
    subtitle: String?,
    appIcon: Drawable?,
    ddIcon: DdIcon?,
    chevron: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (appIcon != null) {
                val bitmap = remember(appIcon) { appIcon.toBitmap(96, 96) }
                Image(
                    painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            } else if (ddIcon != null) {
                DdIcon(icon = ddIcon, tint = colors.inkSecondary, size = 20.dp)
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
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 16.dp)
            }
        }
    }
}

private const val MAX_ROWS = 4
