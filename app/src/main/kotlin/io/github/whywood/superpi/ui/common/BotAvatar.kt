package io.github.whywood.superpi.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BotAvatar(
    workspace: RuntimeWorkspace,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val context = LocalContext.current
    val path = workspace.avatar
    val avatar by produceState<ImageBitmap?>(initialValue = null, path) {
        value = loadAvatarFile(path)
    }
    val image = avatar ?: rememberAppIcon(context)
    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = workspace.name,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(size).clip(CircleShape).background(superPiColors().accentSoft), contentAlignment = Alignment.Center) {
                Text(
                    text = workspace.name.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "D" },
                    style = DdType.label,
                    color = superPiColors().accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun UriAvatarPreview(
    uri: Uri?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val context = LocalContext.current
    val avatar by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            uri?.let { value ->
                runCatching {
                    context.contentResolver.openInputStream(value)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    val image = avatar ?: rememberAppIcon(context)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

private var cachedAppIcon: ImageBitmap? = null

@Composable
internal fun rememberAppIcon(context: Context): ImageBitmap? {
    val icon by produceState(initialValue = cachedAppIcon, context) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
                }.getOrNull()
            }
            cachedAppIcon = loaded
            value = loaded
        }
    }
    return icon
}

private suspend fun loadAvatarFile(path: String?): ImageBitmap? {
    val file = path?.takeIf { it.isNotBlank() } ?: return null
    return withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(file)?.asImageBitmap() }.getOrNull()
    }
}
