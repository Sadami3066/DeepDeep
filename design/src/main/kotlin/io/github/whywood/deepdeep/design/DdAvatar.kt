package io.github.whywood.deepdeep.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DdAvatar(
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    initials: String? = null,
    size: Dp = 40.dp,
    placeholderIcon: DdIcon = DdIcons.Sparkles,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val colors = deepDeepColors()
    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.wash),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = contentDescription,
                    modifier = Modifier.matchParentSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else if (!initials.isNullOrBlank()) {
                DdText(text = initials, style = DdType.label, color = colors.inkSecondary)
            } else {
                DdIcon(
                    icon = placeholderIcon,
                    tint = colors.inkTertiary,
                    size = size * 0.45f,
                    contentDescription = contentDescription,
                )
            }
        }
    }
    if (onClick != null) {
        DdPressable(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            contentDescription = contentDescription,
        ) {
            content()
        }
    } else {
        Box(modifier) { content() }
    }
}
