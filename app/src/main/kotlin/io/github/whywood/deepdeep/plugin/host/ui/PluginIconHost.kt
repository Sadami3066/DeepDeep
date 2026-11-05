package io.github.whywood.deepdeep.plugin.host.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.PluginValidation
import java.io.File

@Composable
internal fun PluginIconHost(
    icon: IconRef?,
    assetsDir: File,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    contentDescription: String? = null,
) {
    when (icon) {
        null -> Unit

        is IconRef.Resource -> DdIcon(
            icon = DdIcon(icon.id),
            tint = tint,
            modifier = modifier,
            size = size,
            contentDescription = contentDescription,
        )

        is IconRef.Asset -> {
            val path = icon.path
            val bitmap = remember(assetsDir, path) {
                if (!PluginValidation.isSafeRelativePath(path)) {
                    null
                } else {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(File(assetsDir, path).absolutePath)
                    }.getOrNull()
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = modifier.size(size),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
