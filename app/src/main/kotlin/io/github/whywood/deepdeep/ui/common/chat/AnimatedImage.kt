package io.github.whywood.deepdeep.ui.common.chat

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val ANIMATED_IMAGE_EXTENSIONS = setOf("gif", "webp", "apng", "avif")

internal fun animatedImageFile(source: String): File? {
    val file = when {
        source.startsWith("file://") -> Uri.parse(source).path?.let(::File)
        source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true) -> null
        else -> File(source)
    } ?: return null
    if (!file.isFile) return null
    val extension = file.name.substringAfterLast('.', "").lowercase(Locale.US)
    return file.takeIf { extension in ANIMATED_IMAGE_EXTENSIONS }
}

internal fun imageBounds(file: File): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return options.outWidth to options.outHeight
}

@Composable
internal fun AnimatedImage(
    file: File,
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val drawable by produceState<Drawable?>(null, file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
            }.getOrNull()
        }
    }
    val current = drawable ?: return
    DisposableEffect(current) {
        val animation = current as? AnimatedImageDrawable
        animation?.start()
        onDispose { runCatching { animation?.stop() } }
    }
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                setImageDrawable(current)
                configure(this, scaleType, onClick, onLongPress)
            }
        },
        update = { view ->
            view.setImageDrawable(current)
            configure(view, scaleType, onClick, onLongPress)
        },
        modifier = modifier,
    )
}

private fun configure(
    view: ImageView,
    scaleType: ImageView.ScaleType,
    onClick: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
) {
    view.scaleType = scaleType
    view.setOnClickListener(onClick?.let { callback -> { callback() } })
    view.setOnLongClickListener(
        onLongPress?.let { callback ->
            {
                callback()
                true
            }
        },
    )
    (view.drawable as? AnimatedImageDrawable)?.start()
}
