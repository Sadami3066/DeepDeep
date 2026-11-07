package io.github.whywood.deepdeep.ui.common.chat

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.whywood.deepdeep.R

@Composable
internal fun ActivityGifIcon(size: Dp = 15.dp) {
    AndroidView(
        factory = { context ->
            val animated = ImageDecoder.decodeDrawable(
                ImageDecoder.createSource(context.resources, R.drawable.process_activity),
            )
            (animated as? AnimatedImageDrawable)?.apply {
                repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                start()
            }
            ImageView(context).apply {
                setImageDrawable(animated)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        modifier = Modifier.size(size),
    )
}
