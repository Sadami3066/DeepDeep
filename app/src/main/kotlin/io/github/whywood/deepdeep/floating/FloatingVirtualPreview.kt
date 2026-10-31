package io.github.whywood.deepdeep.floating

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.vscreen.VirtualScreenState

@Composable
internal fun FloatingVirtualPreview(
    state: VirtualScreenState?,
    frame: ImageBitmap?,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(FloatingVirtualPreviewCorner)
    Box(
        Modifier
            .fillMaxSize()
            .padding(FloatingVirtualPreviewPadding)
            .testTag("floating_virtual_preview"),
    ) {
        if (state is VirtualScreenState.Online && frame != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .floatingLift(shape, colors)
                    .clip(shape)
                    .background(Color.Black)
                    .border(1.dp, colors.ink.copy(alpha = 0.16f), shape),
            ) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .floatingLift(shape, colors)
                    .clip(shape)
                    .background(FloatingVirtualPreviewChipBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = previewChipText(state),
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = FloatingVirtualPreviewChipText,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun previewChipText(state: VirtualScreenState?): String = when (state) {
    null, VirtualScreenState.Offline -> stringResource(R.string.floating_preview_offline)
    VirtualScreenState.Starting -> stringResource(R.string.floating_preview_starting)
    is VirtualScreenState.Failed -> stringResource(R.string.floating_preview_failed)
    is VirtualScreenState.Online -> stringResource(R.string.floating_preview_waiting)
}
