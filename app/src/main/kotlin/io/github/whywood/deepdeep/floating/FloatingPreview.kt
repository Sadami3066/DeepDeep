package io.github.whywood.deepdeep.floating

import android.graphics.Bitmap
import androidx.core.graphics.scale
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import kotlin.math.roundToInt

const val FLOATING_PREVIEW_FRAME_INTERVAL_MS = 200L

fun floatingPreviewAccepted(
    lastAcceptedAt: Long,
    now: Long,
    intervalMs: Long = FLOATING_PREVIEW_FRAME_INTERVAL_MS,
): Boolean = lastAcceptedAt <= 0L || now - lastAcceptedAt >= intervalMs

fun floatingStableVirtualScreenState(state: VirtualScreenState): VirtualScreenState =
    if (state is VirtualScreenState.Online && state.frame != null) state.copy(frame = null) else state

internal fun floatingPreviewBitmap(frame: Bitmap?, targetWidth: Int): Bitmap? {
    if (frame == null || targetWidth <= 0 || frame.isRecycled) return null
    if (frame.width <= 0 || frame.height <= 0) return null
    return runCatching {
        if (frame.width <= targetWidth) {
            frame.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            val height = (frame.height.toFloat() * targetWidth / frame.width)
                .roundToInt()
                .coerceAtLeast(1)
            frame.scale(targetWidth, height, true)
        }
    }.getOrNull()?.takeUnless { it.isRecycled }
}

const val FLOATING_PREVIEW_DEFAULT_RATIO = 9f / 16f

fun floatingPreviewRatio(state: VirtualScreenState): Float {
    val online = state as? VirtualScreenState.Online ?: return FLOATING_PREVIEW_DEFAULT_RATIO
    if (online.width <= 0 || online.height <= 0) return FLOATING_PREVIEW_DEFAULT_RATIO
    return online.width.toFloat() / online.height.toFloat()
}

fun floatingPreviewEnabled(
    shown: Boolean,
    previewOpen: Boolean,
): Boolean = shown && previewOpen
