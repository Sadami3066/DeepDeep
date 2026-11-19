package io.github.whywood.superpi.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors

@Composable
fun AvatarBadge(avatar: ImageBitmap?, name: String, size: Dp = 36.dp) {
    val colors = superPiColors()
    Box(
        Modifier.size(size).clip(CircleShape).background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "D" },
                style = DdType.label,
                color = colors.accent,
                maxLines = 1,
            )
        }
    }
}
