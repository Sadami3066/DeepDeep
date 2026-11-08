package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategy
import io.github.whywood.deepdeep.ui.common.scan.scanAppInfo



@Composable
internal fun ScanAppPickerOverlay(
    strategies: List<ScanStrategy>,
    onSelect: (ScanStrategy) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.94f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DdSpacing.Xl, end = DdSpacing.Sm, top = DdSpacing.Sm, bottom = DdSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.scan_picker_title),
                style = DdType.label,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            DdIconButton(
                icon = DdIcons.Close,
                onClick = onDismiss,
                contentDescription = stringResource(R.string.camera_close),
                tint = Color.White,
            )
        }
        if (strategies.isEmpty()) {
            Text(
                text = stringResource(R.string.scan_picker_empty),
                style = DdType.bodySmall,
                color = Color.White.copy(alpha = DdAlpha.Border),
                modifier = Modifier.padding(horizontal = DdSpacing.Xl, vertical = DdSpacing.Xxl),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DdSpacing.Md, vertical = DdSpacing.Xs),
            ) {
                strategies.forEach { strategy ->
                    StrategyRow(strategy = strategy, onClick = { onSelect(strategy) })
                }
            }
        }
    }
}

@Composable
private fun StrategyRow(strategy: ScanStrategy, onClick: () -> Unit) {
    val context = LocalContext.current
    val info = remember(strategy.packageName) { scanAppInfo(context, strategy.packageName) }
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DdShapes.Large,
        contentDescription = info.label,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DdSpacing.Md, vertical = DdSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = info.icon
            if (icon != null) {
                val bitmap = remember(icon) { icon.toBitmap(96, 96) }
                Image(
                    painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(DdSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = info.label,
                    style = DdType.label,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = strategy.packageName,
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = Color.White.copy(alpha = DdAlpha.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(DdSpacing.Sm))
            DdIcon(icon = DdIcons.ChevronRight, tint = Color.White.copy(alpha = 0.5f), size = 16.dp)
        }
    }
}
